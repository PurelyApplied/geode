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
package org.apache.geode.cache30;

import static org.apache.geode.distributed.ConfigurationProperties.OFF_HEAP_MEMORY_SIZE;

import java.util.Properties;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.Scope;
import org.apache.geode.internal.cache.OffHeapTestUtil;
import org.apache.geode.test.dunit.Invoke;
import org.apache.geode.test.dunit.SerializableRunnable;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.apache.geode.test.junit.categories.FlakyTest;
import org.apache.geode.test.junit.categories.OffHeapTest;


/**
 * Tests Distributed Ack Persistent Region with ConcurrencyChecksEnabled and OffHeap memory.
 *
 * @since Geode 1.0
 */
@SuppressWarnings({"deprecation", "serial"})
@Category({DistributedTest.class, OffHeapTest.class})
public class DistributedAckPersistentRegionCCEOffHeapDUnitTest
    extends DistributedAckPersistentRegionCCEDUnitTest {

  public DistributedAckPersistentRegionCCEOffHeapDUnitTest() {
    super();
  }

  @Override
  public final void preTearDownAssertions() throws Exception {
    SerializableRunnable checkOrphans = new SerializableRunnable() {

      @Override
      public void run() {
        if (hasCache()) {
          OffHeapTestUtil.checkOrphans(getCache());
        }
      }
    };
    Invoke.invokeInEveryVM(checkOrphans);
    checkOrphans.run();
  }

  @Override
  public Properties getDistributedSystemProperties() {
    Properties props = super.getDistributedSystemProperties();
    props.setProperty(OFF_HEAP_MEMORY_SIZE, "10m");
    return props;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  protected RegionAttributes getRegionAttributes() {
    AttributesFactory factory1 = new AttributesFactory();
    factory1.setScope(Scope.DISTRIBUTED_ACK);
    factory1.setDataPolicy(DataPolicy.PERSISTENT_REPLICATE);
    factory1.setConcurrencyChecksEnabled(true);
    RegionAttributes attrs = factory1.create();
    AttributesFactory factory = new AttributesFactory(attrs);
    factory.setOffHeap(true);
    return factory.create();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  protected RegionAttributes getRegionAttributes(String type) {
    RegionAttributes ra1 = getCache().getRegionAttributes(type);
    if (ra1 == null) {
      throw new IllegalStateException("The region shortcut " + type + " has been removed.");
    }
    AttributesFactory factory1 = new AttributesFactory(ra1);
    factory1.setConcurrencyChecksEnabled(true);
    RegionAttributes ra = factory1.create();
    AttributesFactory factory = new AttributesFactory(ra);
    if (!ra.getDataPolicy().isEmpty()) {
      factory.setOffHeap(true);
    }
    return factory.create();
  }
}
