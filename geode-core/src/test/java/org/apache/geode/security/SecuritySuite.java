/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
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
package org.apache.geode.security;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import org.apache.geode.cache.util.PasswordUtilJUnitTest;
import org.apache.geode.distributed.internal.membership.gms.GMSMemberJUnitTest;
import org.apache.geode.distributed.internal.membership.gms.auth.GMSAuthenticatorWithAuthenticatorTest;
import org.apache.geode.distributed.internal.membership.gms.auth.GMSAuthenticatorWithSecurityManagerTest;
import org.apache.geode.internal.SSLConfigJUnitTest;
import org.apache.geode.internal.security.CallbackInstantiatorTest;
import org.apache.geode.internal.security.SecurityServiceFactoryShiroIntegrationTest;
import org.apache.geode.internal.security.SecurityServiceFactoryTest;
import org.apache.geode.internal.security.SecurityServiceTest;
import org.apache.geode.management.RegionCreateDestroyDUnitTest;
import org.apache.geode.management.internal.configuration.ClusterConfigWithSecurityDUnitTest;
import org.apache.geode.management.internal.security.AccessControlMBeanJUnitTest;
import org.apache.geode.management.internal.security.CacheServerMBeanAuthorizationJUnitTest;
import org.apache.geode.management.internal.security.CacheServerMBeanWithShiroIniIntegrationTest;
import org.apache.geode.management.internal.security.CliCommandsSecurityTest;
import org.apache.geode.management.internal.security.DataCommandsSecurityTest;
import org.apache.geode.management.internal.security.DeployCommandsSecurityTest;
import org.apache.geode.management.internal.security.DiskStoreMXBeanSecurityJUnitTest;
import org.apache.geode.management.internal.security.GatewayReceiverMBeanSecurityTest;
import org.apache.geode.management.internal.security.GatewaySenderMBeanSecurityTest;
import org.apache.geode.management.internal.security.GfshCommandsPostProcessorTest;
import org.apache.geode.management.internal.security.GfshCommandsSecurityTest;
import org.apache.geode.management.internal.security.LockServiceMBeanAuthorizationJUnitTest;
import org.apache.geode.management.internal.security.MBeanSecurityJUnitTest;
import org.apache.geode.management.internal.security.ManagerMBeanAuthorizationJUnitTest;
import org.apache.geode.management.internal.security.MemberMBeanSecurityJUnitTest;
import org.apache.geode.management.internal.security.MultiUserDUnitTest;
import org.apache.geode.management.internal.security.ResourcePermissionTest;
import org.apache.geode.management.internal.security.SecurityServiceWithCustomRealmIntegrationTest;
import org.apache.geode.management.internal.security.SecurityServiceWithShiroIniIntegrationTest;
import org.apache.geode.security.templates.PKCSPrincipalTest;
import org.apache.geode.security.templates.UsernamePrincipalTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({AccessControlMBeanJUnitTest.class, CacheFactoryWithSecurityObjectTest.class,
    CacheServerMBeanAuthorizationJUnitTest.class, CacheServerMBeanWithShiroIniIntegrationTest.class,
    CallbackInstantiatorTest.class, CliCommandsSecurityTest.class, ClientAuthDUnitTest.class,
    ClientAuthenticationDUnitTest.class, ClientAuthenticationPart2DUnitTest.class,
    ClientAuthorizationDUnitTest.class, ClientContainsKeyAuthDUnitTest.class,
    ClientDestroyInvalidateAuthDUnitTest.class, ClientDestroyRegionAuthDUnitTest.class,
    ClientExecuteFunctionAuthDUnitTest.class, ClientExecuteRegionFunctionAuthDUnitTest.class,
    ClientGetAllAuthDUnitTest.class, ClientGetEntryAuthDUnitTest.class,
    ClientGetPutAuthDUnitTest.class, ClientMultiUserAuthzDUnitTest.class,
    ClientRegionClearAuthDUnitTest.class, ClientRegisterInterestAuthDUnitTest.class,
    ClientRemoveAllAuthDUnitTest.class, ClientUnregisterInterestAuthDUnitTest.class,
    ClusterConfigWithEmbededLocatorDUnitTest.class, ClusterConfigWithoutSecurityDUnitTest.class,
    ClusterConfigWithSecurityDUnitTest.class, DataCommandsSecurityTest.class,
    DeltaClientAuthorizationDUnitTest.class, DeltaClientPostAuthorizationDUnitTest.class,
    DeployCommandsSecurityTest.class, DiskStoreMXBeanSecurityJUnitTest.class,
    ExampleSecurityManagerTest.class, GatewayReceiverMBeanSecurityTest.class,
    GatewaySenderMBeanSecurityTest.class, GemFireSecurityExceptionTest.class,
    GfshCommandsPostProcessorTest.class, GfshCommandsSecurityTest.class,
    GMSAuthenticatorWithAuthenticatorTest.class, GMSAuthenticatorWithSecurityManagerTest.class,
    GMSMemberJUnitTest.class, IntegratedSecurityPeerAuthDistributedTest.class,
    LockServiceMBeanAuthorizationJUnitTest.class, ManagerMBeanAuthorizationJUnitTest.class,
    MBeanSecurityJUnitTest.class, MemberMBeanSecurityJUnitTest.class, MultiUserDUnitTest.class,
    NoShowValue1PostProcessorDUnitTest.class, NotAuthorizedExceptionTest.class,
    P2PAuthenticationDUnitTest.class, PasswordUtilJUnitTest.class,
    PDXGfshPostProcessorOnRemoteServerTest.class, PDXPostProcessorDUnitTest.class,
    PeerAuthenticatorDUnitTest.class, PeerAuthenticatorWithCachelessLocatorDUnitTest.class,
    PeerSecurityWithEmbeddedLocatorDUnitTest.class, PKCSPrincipalTest.class,
    PostProcessorDUnitTest.class, RegionCreateDestroyDUnitTest.class, ResourcePermissionTest.class,
    SecurityClusterConfigDUnitTest.class, SecurityManagerLifecycleDistributedTest.class,
    SecurityManagerLifecycleIntegrationTest.class, SecurityServiceFactoryShiroIntegrationTest.class,
    SecurityServiceFactoryTest.class, SecurityServiceTest.class,
    SecurityServiceWithCustomRealmIntegrationTest.class,
    SecurityServiceWithShiroIniIntegrationTest.class, SecurityWithoutClusterConfigDUnitTest.class,
    SimpleSecurityManagerTest.class, SSLConfigJUnitTest.class, StartServerAuthorizationTest.class,
    UsernamePrincipalTest.class,})
public class SecuritySuite {
}

// ClientAuthorizationTwoDUnitTest.class,
// ClientAuthzObjectModDUnitTest.class,
// ClientCQPostAuthorizationDUnitTest.class,
// ClientPostAuthorizationDUnitTest.class,
// ClientQueryAuthDUnitTest.class,
// CommandOverHttpDUnitTest.class,
// ConnectCommandWithHttpAndSSLDUnitTest.class,
// CQClientAuthDUnitTest.class,
// CQPDXPostProcessorDUnitTest.class,
// CQPostProcessorDunitTest.class,
// GfshCommandsOverHttpSecurityTest.class,
// LuceneClientSecurityDUnitTest.class,
// LuceneCommandsSecurityDUnitTest.class,
// MultiUserAPIDUnitTest.class,
// MultiUserDurableCQAuthzDUnitTest.class,
// RestSecurityIntegrationTest.class,
// RestSecurityPostProcessorTest.class,
// RestSecurityWithSSLTest.class,
