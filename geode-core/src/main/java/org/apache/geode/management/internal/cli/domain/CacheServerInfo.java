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
package org.apache.geode.management.internal.cli.domain;

import java.io.Serializable;

import org.apache.geode.cache.server.CacheServer;

public class CacheServerInfo implements Serializable {

  private static final long serialVersionUID = 1L;

  private String bindAddress;
  private int port;
  private boolean isRunning;
  private int maxConnections;
  private int maxThreads;

  public CacheServerInfo(CacheServer cacheServer) {
    bindAddress = cacheServer.getBindAddress();
    port = cacheServer.getPort();
    isRunning = cacheServer.isRunning();
    maxConnections = cacheServer.getMaxConnections();
    maxThreads = cacheServer.getMaxThreads();
  }

  public String getBindAddress() {
    return bindAddress;
  }

  public int getPort() {
    return port;
  }

  public int getMaxThreads() {
    return maxThreads;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public boolean isRunning() {
    return isRunning;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Bind Address :");
    sb.append(this.bindAddress);
    sb.append('\n');
    sb.append("Port :");
    sb.append(this.port);
    sb.append('\n');
    sb.append("Running :");
    sb.append(this.isRunning);
    return sb.toString();
  }

}
