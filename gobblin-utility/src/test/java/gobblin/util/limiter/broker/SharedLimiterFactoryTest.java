/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gobblin.util.limiter.broker;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import gobblin.broker.BrokerConstants;
import gobblin.broker.SharedResourcesBrokerFactory;
import gobblin.broker.SharedResourcesBrokerImpl;
import gobblin.broker.SimpleScope;
import gobblin.broker.SimpleScopeType;
import gobblin.broker.iface.NotConfiguredException;
import gobblin.broker.iface.SharedResourcesBroker;
import gobblin.util.limiter.CountBasedLimiter;
import gobblin.util.limiter.Limiter;
import gobblin.util.limiter.MultiLimiter;
import gobblin.util.limiter.NoopLimiter;


public class SharedLimiterFactoryTest {

  public static final Joiner JOINER = Joiner.on(".");

  @Test
  public void testEmptyConfig() throws Exception {

    SharedResourcesBrokerImpl<SimpleScopeType> broker =
        getBrokerForConfigMap(ImmutableMap.<String, String>of());

    SharedLimiterFactory<SimpleScopeType> factory = new SharedLimiterFactory<>();

    Assert.assertEquals(
        factory.getAutoScope(broker, broker.getConfigView(SimpleScopeType.LOCAL, new SharedLimiterKey("resource"), factory.getName())),
        SimpleScopeType.GLOBAL);

    Limiter limiter = factory.createResource(broker,
        broker.getConfigView(SimpleScopeType.GLOBAL, new SharedLimiterKey("resource"), factory.getName()));
    Assert.assertTrue(limiter instanceof NoopLimiter);
  }

  @Test
  public void testCountLimiter() throws Exception {

    SharedResourcesBrokerImpl<SimpleScopeType> broker =
        getBrokerForConfigMap(ImmutableMap.of(
            JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, SharedLimiterFactory.NAME, SharedLimiterFactory.LIMITER_CLASS_KEY), "CountBasedLimiter",
            JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, SharedLimiterFactory.NAME, CountBasedLimiter.Factory.COUNT_KEY), "10"
        ));

    SharedLimiterFactory<SimpleScopeType> factory = new SharedLimiterFactory<>();

    Assert.assertEquals(
        factory.getAutoScope(broker, broker.getConfigView(SimpleScopeType.LOCAL, new SharedLimiterKey("resource"), factory.getName())),
        SimpleScopeType.GLOBAL);

    Limiter limiter = factory.createResource(broker,
          broker.getConfigView(SimpleScopeType.GLOBAL, new SharedLimiterKey("resource"), factory.getName()));

    Assert.assertTrue(limiter instanceof CountBasedLimiter);
    Assert.assertEquals(((CountBasedLimiter) limiter).getCountLimit(), 10);

  }

  @Test
  public void testMultiLevelLimiter() throws Exception {

    SharedResourcesBrokerImpl<SimpleScopeType> broker =
        getBrokerForConfigMap(ImmutableMap.of(
            JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, SharedLimiterFactory.NAME, SimpleScopeType.GLOBAL, SharedLimiterFactory.LIMITER_CLASS_KEY), "CountBasedLimiter",
            JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, SharedLimiterFactory.NAME, SimpleScopeType.GLOBAL, CountBasedLimiter.Factory.COUNT_KEY), "10",
            JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, SharedLimiterFactory.NAME, SimpleScopeType.LOCAL, SharedLimiterFactory.LIMITER_CLASS_KEY), "CountBasedLimiter",
            JOINER.join(BrokerConstants.GOBBLIN_BROKER_CONFIG_PREFIX, SharedLimiterFactory.NAME, SimpleScopeType.LOCAL, CountBasedLimiter.Factory.COUNT_KEY), "5"
        ));
    SharedResourcesBroker<SimpleScopeType> localBroker1 =
        broker.newSubscopedBuilder(new SimpleScope<>(SimpleScopeType.LOCAL, "local1")).build();
    SharedResourcesBroker<SimpleScopeType> localBroker2 =
        broker.newSubscopedBuilder(new SimpleScope<>(SimpleScopeType.LOCAL, "local2")).build();

    SharedLimiterFactory<SimpleScopeType> factory = new SharedLimiterFactory<>();

    Limiter limiter1 = factory.createResource(localBroker1,
        broker.getConfigView(SimpleScopeType.LOCAL, new SharedLimiterKey("resource"), factory.getName()));
    Limiter limiter2 = factory.createResource(localBroker2,
        broker.getConfigView(SimpleScopeType.LOCAL, new SharedLimiterKey("resource"), factory.getName()));

    Assert.assertTrue(limiter1 instanceof MultiLimiter);
    Assert.assertTrue(limiter2 instanceof MultiLimiter);

    Assert.assertEquals(((CountBasedLimiter)((MultiLimiter) limiter1).getUnderlyingLimiters().get(0)).getCountLimit(), 5);
    Assert.assertEquals(((CountBasedLimiter)((MultiLimiter) limiter1).getUnderlyingLimiters().get(1)).getCountLimit(), 10);
    Assert.assertEquals(((CountBasedLimiter)((MultiLimiter) limiter2).getUnderlyingLimiters().get(0)).getCountLimit(), 5);
    Assert.assertEquals(((CountBasedLimiter)((MultiLimiter) limiter2).getUnderlyingLimiters().get(1)).getCountLimit(), 10);

    Assert.assertNotEquals(((MultiLimiter) limiter1).getUnderlyingLimiters().get(0), ((MultiLimiter) limiter2).getUnderlyingLimiters().get(0));
    Assert.assertEquals(((MultiLimiter) limiter1).getUnderlyingLimiters().get(1), ((MultiLimiter) limiter2).getUnderlyingLimiters().get(1));
  }

  private SharedResourcesBrokerImpl<SimpleScopeType> getBrokerForConfigMap(Map<String, String> configMap) {
    Config config = ConfigFactory.parseMap(configMap);

    return
        SharedResourcesBrokerFactory.<SimpleScopeType>createDefaultTopLevelBroker(config, SimpleScopeType.GLOBAL.defaultScopeInstance());
  }

}
