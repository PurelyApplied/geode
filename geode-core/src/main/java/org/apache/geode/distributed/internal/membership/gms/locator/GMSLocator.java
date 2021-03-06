/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.distributed.internal.membership.gms.locator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;

import org.apache.geode.DataSerializer;
import org.apache.geode.InternalGemFireException;
import org.apache.geode.annotations.VisibleForTesting;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.distributed.DistributedSystem;
import org.apache.geode.distributed.internal.ClusterDistributionManager;
import org.apache.geode.distributed.internal.InternalConfigurationPersistenceService;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.distributed.internal.LocatorStats;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember;
import org.apache.geode.distributed.internal.membership.InternalDistributedMember.InternalDistributedMemberWrapper;
import org.apache.geode.distributed.internal.membership.MembershipManager;
import org.apache.geode.distributed.internal.membership.NetView;
import org.apache.geode.distributed.internal.membership.gms.GMSUtil;
import org.apache.geode.distributed.internal.membership.gms.NetLocator;
import org.apache.geode.distributed.internal.membership.gms.Services;
import org.apache.geode.distributed.internal.membership.gms.interfaces.Locator;
import org.apache.geode.distributed.internal.membership.gms.membership.HostAddress;
import org.apache.geode.distributed.internal.membership.gms.mgr.GMSMembershipManager;
import org.apache.geode.distributed.internal.tcpserver.TcpClient;
import org.apache.geode.distributed.internal.tcpserver.TcpServer;
import org.apache.geode.internal.Version;
import org.apache.geode.internal.VersionedObjectInput;
import org.apache.geode.internal.logging.LogService;

public class GMSLocator implements Locator, NetLocator {

  static final int LOCATOR_FILE_STAMP = 0x7b8cf741;

  private static final Logger logger = LogService.getLogger();

  private final boolean usePreferredCoordinators;
  private final boolean networkPartitionDetectionEnabled;
  private final String securityUDPDHAlgo;
  private final String locatorString;
  private final List<HostAddress> locators;
  private final LocatorStats locatorStats;
  private final Set<InternalDistributedMember> registrants = new HashSet<>();
  private final Map<InternalDistributedMemberWrapper, byte[]> publicKeys =
      new ConcurrentHashMap<>();
  private final File workingDirectory;

  private volatile boolean isCoordinator;

  private Services services;
  private InternalDistributedMember localAddress;

  /**
   * The current membership view, or one recovered from disk. This is a copy-on-write variable.
   */
  private NetView view;

  private NetView recoveredView;

  private File viewFile;

  /**
   * @param bindAddress network address that TcpServer will bind to
   * @param locatorString location of other locators (bootstrapping, failover)
   * @param usePreferredCoordinators true if the membership coordinator should be a Locator
   * @param networkPartitionDetectionEnabled true if network partition detection is enabled
   * @param locatorStats the locator statistics object
   * @param securityUDPDHAlgo DF algorithm
   * @param workingDirectory directory to use for view file (defaults to "user.dir")
   */
  public GMSLocator(InetAddress bindAddress, String locatorString, boolean usePreferredCoordinators,
      boolean networkPartitionDetectionEnabled, LocatorStats locatorStats,
      String securityUDPDHAlgo, File workingDirectory) {
    this.usePreferredCoordinators = usePreferredCoordinators;
    this.networkPartitionDetectionEnabled = networkPartitionDetectionEnabled;
    this.securityUDPDHAlgo = securityUDPDHAlgo;
    this.locatorString = locatorString;
    if (this.locatorString == null || this.locatorString.isEmpty()) {
      locators = new ArrayList<>(0);
    } else {
      locators = GMSUtil.parseLocators(locatorString, bindAddress);
    }
    this.locatorStats = locatorStats;
    this.workingDirectory = workingDirectory;
  }

  @Override
  public synchronized boolean setMembershipManager(MembershipManager mgr) {
    if (services == null || services.isStopped()) {
      services = ((GMSMembershipManager) mgr).getServices();
      localAddress = services.getMessenger().getMemberID();
      Objects.requireNonNull(localAddress, "member address should have been established");
      logger.info("Peer locator is connecting to local membership services with ID {}",
          localAddress);
      services.setLocator(this);
      NetView newView = services.getJoinLeave().getView();
      if (newView != null) {
        view = newView;
        recoveredView = null;
      } else {
        // if we are auto-reconnecting we may already have a membership view
        // and should use it as a "recovered view" which only hints at who is
        // the current membership coordinator
        if (view != null) {
          view.setViewId(-100); // no longer a valid view
          recoveredView = view; // auto-reconnect will be based on the recovered view
          view = null;
        }
        if (localAddress != null) {
          if (recoveredView != null) {
            recoveredView.remove(localAddress);
          }
          synchronized (registrants) {
            registrants.add(localAddress);
          }
        }
      }
      notifyAll();
      return true;
    }
    return false;
  }

  @VisibleForTesting
  File setViewFile(File file) {
    viewFile = file.getAbsoluteFile();
    return viewFile;
  }

  @VisibleForTesting
  File getViewFile() {
    return viewFile;
  }

  @Override
  public void init(TcpServer server) throws InternalGemFireException {
    if (viewFile == null) {
      viewFile =
          new File(workingDirectory, "locator" + server.getPort() + "view.dat").getAbsoluteFile();
    }
    logger.info(
        "GemFire peer location service starting.  Other locators: {}  Locators preferred as coordinators: {}  Network partition detection enabled: {}  View persistence file: {}",
        locatorString, usePreferredCoordinators, networkPartitionDetectionEnabled, viewFile);
    recover();
  }

  @Override
  public void installView(NetView view) {
    synchronized (registrants) {
      registrants.clear();
    }
    logger.info("Peer locator received new membership view: {}", view);
    this.view = view;
    recoveredView = null;
    saveView(view);
  }

  @Override
  public void setIsCoordinator(boolean isCoordinator) {
    if (isCoordinator) {
      logger.info(
          "Location services has received notification that this node is becoming membership coordinator");
    }
    this.isCoordinator = isCoordinator;
  }

  @Override
  public Object processRequest(Object request) {
    if (logger.isDebugEnabled()) {
      logger.debug("Peer locator processing {}", request);
    }

    if (localAddress == null && services != null) {
      localAddress = services.getMessenger().getMemberID();
    }

    Object response = null;
    if (request instanceof GetViewRequest) {
      if (view != null) {
        response = new GetViewResponse(view);
      }
    } else if (request instanceof FindCoordinatorRequest) {
      response = processFindCoordinatorRequest((FindCoordinatorRequest) request);
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Peer locator returning {}", response);
    }
    return response;
  }

  private FindCoordinatorResponse processFindCoordinatorRequest(
      FindCoordinatorRequest findRequest) {
    if (!findRequest.getDHAlgo().equals(securityUDPDHAlgo)) {
      return new FindCoordinatorResponse(
          "Rejecting findCoordinatorRequest, as member not configured same udp security("
              + findRequest.getDHAlgo() + " )as locator (" + securityUDPDHAlgo + ")");
    }

    if (services == null) {
      if (findRequest.getMyPublicKey() != null) {
        publicKeys.put(new InternalDistributedMemberWrapper(findRequest.getMemberID()),
            findRequest.getMyPublicKey());
      }
      logger.debug(
          "Rejecting a request to find the coordinator - membership services are still initializing");
      return null;
    }

    if (findRequest.getMemberID() == null) {
      return null;
    }

    services.getMessenger().setPublicKey(findRequest.getMyPublicKey(),
        findRequest.getMemberID());

    // at this level we want to return the coordinator known to membership services,
    // which may be more up-to-date than the one known by the membership manager
    if (view == null) {
      if (services == null) {
        // we must know this process's identity in order to respond
        return null;
      }
    }

    NetView responseView = view;
    if (responseView == null) {
      responseView = recoveredView;
    }

    synchronized (registrants) {
      registrants.add(findRequest.getMemberID());
      if (recoveredView != null) {
        recoveredView.remove(findRequest.getMemberID());
      }
    }

    InternalDistributedMember coordinator = null;
    boolean fromView = false;
    if (responseView != null) {
      // if the ID of the requester matches an entry in the membership view then remove
      // that entry - it's obviously an old member since the ID has been reused
      InternalDistributedMember requestingMemberID = findRequest.getMemberID();
      for (InternalDistributedMember id : responseView.getMembers()) {
        if (requestingMemberID.compareTo(id, false) == 0) {
          NetView newView = new NetView(responseView, responseView.getViewId());
          newView.remove(id);
          responseView = newView;
          break;
        }
      }

      if (responseView.getViewId() > findRequest.getLastViewId()) {
        // ignore the requests rejectedCoordinators if the view has changed
        coordinator = responseView.getCoordinator(Collections.emptyList());
      } else {
        coordinator = responseView.getCoordinator(findRequest.getRejectedCoordinators());
      }
      logger.info("Peer locator: coordinator from view is {}", coordinator);
      fromView = true;
    }

    if (coordinator == null) {
      // find the "oldest" registrant
      Collection<InternalDistributedMember> rejections = findRequest.getRejectedCoordinators();
      if (rejections == null) {
        rejections = Collections.emptyList();
      }

      synchronized (registrants) {
        coordinator = services.getJoinLeave().getMemberID();
        for (InternalDistributedMember mbr : registrants) {
          if (mbr != coordinator && (coordinator == null || mbr.compareTo(coordinator) < 0)) {
            if (!rejections.contains(mbr) && (mbr.getNetMember().preferredForCoordinator()
                || !mbr.getNetMember().isNetworkPartitionDetectionEnabled())) {
              coordinator = mbr;
            }
          }
        }
        logger.info("Peer locator: coordinator from registrations is {}", coordinator);
      }
    }

    synchronized (registrants) {
      if (isCoordinator) {
        coordinator = localAddress;
        if (responseView != null && localAddress != null
            && !localAddress.equals(responseView.getCoordinator())) {
          responseView = null;
          fromView = false;
        }
      }

      byte[] coordinatorPublicKey = null;
      if (responseView != null) {
        coordinatorPublicKey = (byte[]) responseView.getPublicKey(coordinator);
      }
      if (coordinatorPublicKey == null) {
        coordinatorPublicKey = services.getMessenger().getPublicKey(coordinator);
      }

      return new FindCoordinatorResponse(coordinator, localAddress, fromView, responseView,
          new HashSet<>(registrants), networkPartitionDetectionEnabled, usePreferredCoordinators,
          coordinatorPublicKey);
    }
  }

  private void saveView(NetView view) {
    if (viewFile == null) {
      return;
    }
    if (!viewFile.delete() && viewFile.exists()) {
      logger.warn("Peer locator is unable to delete persistent membership information in {}",
          viewFile.getAbsolutePath());
    }
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(viewFile))) {
      oos.writeInt(LOCATOR_FILE_STAMP);
      oos.writeInt(Version.CURRENT_ORDINAL);
      DataSerializer.writeObject(view, oos);
    } catch (Exception e) {
      logger.warn(
          "Peer locator encountered an error writing current membership to disk.  Disabling persistence.  Care should be taken when bouncing this locator as it will not be able to recover knowledge of the running distributed system",
          e);
      viewFile = null;
    }
  }

  @Override
  public void endRequest(Object request, long startTime) {
    locatorStats.endLocatorRequest(startTime);
  }

  @Override
  public void endResponse(Object request, long startTime) {
    locatorStats.endLocatorResponse(startTime);
  }

  public byte[] getPublicKey(InternalDistributedMember member) {
    return publicKeys.get(new InternalDistributedMemberWrapper(member));
  }

  @Override
  public void shutDown() {
    // nothing to do for GMSLocator
    publicKeys.clear();
  }

  @VisibleForTesting
  public List<InternalDistributedMember> getMembers() {
    if (view != null) {
      return new ArrayList<>(view.getMembers());
    }
    synchronized (registrants) {
      return new ArrayList<>(registrants);
    }
  }

  @Override
  public void restarting(DistributedSystem ds, GemFireCache cache,
      InternalConfigurationPersistenceService sharedConfig) {
    setMembershipManager(((InternalDistributedSystem) ds).getDM().getMembershipManager());
  }

  private void recover() throws InternalGemFireException {
    if (!recoverFromOtherLocators()) {
      recoverFromFile(viewFile);
    }
  }

  private boolean recoverFromOtherLocators() {
    for (HostAddress other : locators) {
      if (recover(other.getSocketInetAddress())) {
        logger.info("Peer locator recovered state from {}", other);
        return true;
      }
    }
    return false;
  }

  private boolean recover(InetSocketAddress other) {
    try {
      logger.info("Peer locator attempting to recover from {}", other);
      TcpClient client = new TcpClient();
      Object response = client.requestToServer(other.getAddress(), other.getPort(),
          new GetViewRequest(), 20000, true);
      if (response instanceof GetViewResponse) {
        view = ((GetViewResponse) response).getView();
        logger.info("Peer locator recovered initial membership of {}", view);
        return true;
      }
    } catch (IOException | ClassNotFoundException e) {
      logger.debug("Peer locator could not recover membership view from {}: {}", other,
          e.getMessage());
    }
    logger.info("Peer locator was unable to recover state from this locator");
    return false;
  }

  boolean recoverFromFile(File file) throws InternalGemFireException {
    if (!file.exists()) {
      logger.info("recovery file not found: {}", file.getAbsolutePath());
      return false;
    }

    logger.info("Peer locator recovering from {}", file.getAbsolutePath());
    try (ObjectInput ois = new ObjectInputStream(new FileInputStream(file))) {
      if (ois.readInt() != LOCATOR_FILE_STAMP) {
        return false;
      }

      ObjectInput input = ois;
      int version = input.readInt();
      if (version != Version.CURRENT_ORDINAL) {
        Version geodeVersion = Version.fromOrdinalNoThrow((short) version, false);
        logger.info("Peer locator found that persistent view was written with {}", geodeVersion);
        input = new VersionedObjectInput(input, geodeVersion);
      }

      recoveredView = DataSerializer.readObject(input);
      // this is not a valid view so it shouldn't have a usable Id
      recoveredView.setViewId(-1);
      List<InternalDistributedMember> members = new ArrayList<>(recoveredView.getMembers());
      // Remove locators from the view. Since we couldn't recover from an existing
      // locator we know that all of the locators in the view are defunct
      for (InternalDistributedMember member : members) {
        if (member.getVmKind() == ClusterDistributionManager.LOCATOR_DM_TYPE) {
          recoveredView.remove(member);
        }
      }

      logger.info("Peer locator recovered membership is {}", recoveredView);
      return true;

    } catch (Exception e) {
      String message =
          String.format("Unable to recover previous membership view from %s", file.toString());
      logger.warn(message, e);
      if (!file.delete() && file.exists()) {
        logger.warn("Peer locator was unable to recover from or delete {}", file);
        viewFile = null;
      }
      throw new InternalGemFireException(message, e);
    }
  }
}
