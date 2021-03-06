/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.entity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.mgmt.ExecutionManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigMap;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.ConfigPredicates;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey.IntegerAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.task.BasicTask;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;

import groovy.lang.Closure;

public class EntityConfigTest extends BrooklynAppUnitTestSupport {

    private static final int TIMEOUT_MS = 10*1000;

    private ExecutorService executor;
    private ExecutionManager executionManager;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        executionManager = mgmt.getExecutionManager();
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        super.tearDown();
    }

    @Test
    public void testConfigBagContainsMatchesForConfigKeyName() throws Exception {
        EntityInternal entity = mgmt.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("myentity.myconfig", "myval1")
                .configure("myentity.myconfigwithflagname", "myval2"));
        
        assertEquals(entity.getAllConfig(), ImmutableMap.of(MyEntity.MY_CONFIG, "myval1", MyEntity.MY_CONFIG_WITH_FLAGNAME, "myval2"));
        assertEquals(entity.getAllConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfig", "myval1", "myentity.myconfigwithflagname", "myval2"));
        assertEquals(entity.getLocalConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfig", "myval1", "myentity.myconfigwithflagname", "myval2"));
    }

    @Test
    public void testConfigBagContainsMatchesForFlagName() throws Exception {
        // Prefers flag-name, over config-key's name
        EntityInternal entity = mgmt.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("myconfigflagname", "myval"));
        
        assertEquals(entity.getAllConfig(), ImmutableMap.of(MyEntity.MY_CONFIG_WITH_FLAGNAME, "myval"));
        assertEquals(entity.getAllConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfigwithflagname", "myval"));
        assertEquals(entity.getLocalConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfigwithflagname", "myval"));
    }

    // TODO Which way round should it be?!
    @Test(enabled=false)
    public void testPrefersFlagNameOverConfigKeyName() throws Exception {
        EntityInternal entity = mgmt.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("myconfigflagname", "myval")
                .configure("myentity.myconfigwithflagname", "shouldIgnoreAndPreferFlagName"));
        
        assertEquals(entity.getAllConfig(), ImmutableMap.of(MyEntity.MY_CONFIG_WITH_FLAGNAME, "myval"));
    }

    @Test
    public void testConfigBagContainsUnmatched() throws Exception {
        EntityInternal entity = mgmt.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("notThere", "notThereVal"));
        
        assertEquals(entity.getAllConfig(), ImmutableMap.of(ConfigKeys.newConfigKey(Object.class, "notThere"), "notThereVal"));
        assertEquals(entity.getAllConfigBag().getAllConfig(), ImmutableMap.of("notThere", "notThereVal"));
        assertEquals(entity.getLocalConfigBag().getAllConfig(), ImmutableMap.of("notThere", "notThereVal"));
    }
    
    @Test
    public void testChildConfigBagInheritsUnmatchedAtParent() throws Exception {
        EntityInternal entity = mgmt.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("mychildentity.myconfig", "myval1")
                .configure("mychildconfigflagname", "myval2")
                .configure("notThere", "notThereVal"));

        EntityInternal child = mgmt.getEntityManager().createEntity(EntitySpec.create(MyChildEntity.class)
                .parent(entity));

        assertEquals(child.getAllConfig(), ImmutableMap.of(MyChildEntity.MY_CHILD_CONFIG, "myval1", 
            ConfigKeys.newConfigKey(Object.class, "mychildconfigflagname"), "myval2",
            ConfigKeys.newConfigKey(Object.class, "notThere"), "notThereVal"));
        assertEquals(child.getAllConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfig", "myval1", "mychildconfigflagname", "myval2", "notThere", "notThereVal"));
        assertEquals(child.getLocalConfigBag().getAllConfig(), ImmutableMap.of());
    }
    
    @Test
    public void testChildInheritsFromParent() throws Exception {
        EntityInternal entity = mgmt.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("myentity.myconfig", "myval1"));

        EntityInternal child = mgmt.getEntityManager().createEntity(EntitySpec.create(MyChildEntity.class)
                .parent(entity));

        assertEquals(child.getAllConfig(), ImmutableMap.of(MyEntity.MY_CONFIG, "myval1"));
        assertEquals(child.getAllConfigBag().getAllConfig(), ImmutableMap.of("myentity.myconfig", "myval1"));
        assertEquals(child.getLocalConfigBag().getAllConfig(), ImmutableMap.of());
    }
    
    @Test
    public void testChildCanOverrideConfigUsingKeyName() throws Exception {
        EntityInternal entity = mgmt.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure("mychildentity.myconfigwithflagname", "myval")
                .configure("notThere", "notThereVal"));

        EntityInternal child = mgmt.getEntityManager().createEntity(EntitySpec.create(MyChildEntity.class)
                .parent(entity)
                .configure("mychildentity.myconfigwithflagname", "overrideMyval")
                .configure("notThere", "overrideNotThereVal"));

        assertEquals(child.getAllConfig(), ImmutableMap.of(MyChildEntity.MY_CHILD_CONFIG_WITH_FLAGNAME, "overrideMyval",
            ConfigKeys.newConfigKey(Object.class, "notThere"), "overrideNotThereVal"));
        assertEquals(child.getAllConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfigwithflagname", "overrideMyval", "notThere", "overrideNotThereVal"));
        assertEquals(child.getLocalConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfigwithflagname", "overrideMyval", "notThere", "overrideNotThereVal"));
    }
    
    @Test
    public void testChildCanOverrideConfigUsingFlagName() throws Exception {
        EntityInternal entity = mgmt.getEntityManager().createEntity(EntitySpec.create(MyEntity.class)
                .configure(MyChildEntity.MY_CHILD_CONFIG_WITH_FLAGNAME, "myval"));
        assertEquals(entity.getAllConfig(), ImmutableMap.of(MyChildEntity.MY_CHILD_CONFIG_WITH_FLAGNAME, "myval"));

        EntityInternal child = mgmt.getEntityManager().createEntity(EntitySpec.create(MyChildEntity.class)
                .parent(entity)
                .configure("mychildconfigflagname", "overrideMyval"));

        assertEquals(child.getAllConfig(), ImmutableMap.of(MyChildEntity.MY_CHILD_CONFIG_WITH_FLAGNAME, "overrideMyval"));
        assertEquals(child.getAllConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfigwithflagname", "overrideMyval"));
        assertEquals(child.getLocalConfigBag().getAllConfig(), ImmutableMap.of("mychildentity.myconfigwithflagname", "overrideMyval"));
    }

    @Test
    public void testGetConfigMapWithSubKeys() throws Exception {
        TestEntity entity = mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_MAP_THING.subKey("mysub"), "myval"));
        
        assertEquals(entity.config().get(TestEntity.CONF_MAP_THING), ImmutableMap.of("mysub", "myval"));
        assertEquals(entity.config().getNonBlocking(TestEntity.CONF_MAP_THING).get(), ImmutableMap.of("mysub", "myval"));
        
        assertEquals(entity.config().get(TestEntity.CONF_MAP_THING.subKey("mysub")), "myval");
        assertEquals(entity.config().getNonBlocking(TestEntity.CONF_MAP_THING.subKey("mysub")).get(), "myval");
    }
    
    @Test
    public void testGetConfigMapWithExplicitMap() throws Exception {
        TestEntity entity = mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_MAP_THING, ImmutableMap.of("mysub", "myval")));
        
        assertEquals(entity.config().get(TestEntity.CONF_MAP_THING), ImmutableMap.of("mysub", "myval"));
        assertEquals(entity.config().getNonBlocking(TestEntity.CONF_MAP_THING).get(), ImmutableMap.of("mysub", "myval"));
        
        assertEquals(entity.config().get(TestEntity.CONF_MAP_THING.subKey("mysub")), "myval");
        assertEquals(entity.config().getNonBlocking(TestEntity.CONF_MAP_THING.subKey("mysub")).get(), "myval");
    }
    
    // TODO This now fails because the task has been cancelled, in entity.config().get().
    // But it used to pass (e.g. with commit 56fcc1632ea4f5ac7f4136a7e04fabf501337540).
    // It failed after the rename of CONF_MAP_THING_OBJ to CONF_MAP_OBJ_THING, which 
    // suggests there was an underlying problem that was masked by the unfortunate naming
    // of the previous "test.confMapThing.obj".
    //
    // Presumably an earlier call to task.get() timed out, causing it to cancel the task?
    // I (Aled) question whether we want to support passing a task (rather than a 
    // DeferredSupplier or TaskFactory, for example). Our EntitySpec.configure is overloaded
    // to take a Task, but that feels wrong!?
    @Test(groups="Broken")
    public void testGetTaskNonBlocking() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Task<String> task = Tasks.<String>builder().body(
                new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        latch.await();
                        return "myval";
                    }})
                .build();
        runGetConfigNonBlocking(latch, task, "myval");
    }
    
    @Test
    public void testGetDeferredSupplierNonBlocking() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        DeferredSupplier<String> task = new DeferredSupplier<String>() {
            @Override public String get() {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
                return "myval";
            }
        };
        runGetConfigNonBlocking(latch, task, "myval");
    }
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void runGetConfigNonBlocking(CountDownLatch latch, Object blockingVal, String expectedVal) throws Exception {
        TestEntity entity = (TestEntity) mgmt.getEntityManager().createEntity(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_MAP_OBJ_THING, ImmutableMap.<String, Object>of("mysub", blockingVal))
                .configure((ConfigKey)TestEntity.CONF_NAME, blockingVal));
        
        // Will initially return absent, because task is not done
        assertTrue(entity.config().getNonBlocking(TestEntity.CONF_MAP_OBJ_THING).isAbsent());
        assertTrue(entity.config().getNonBlocking(TestEntity.CONF_MAP_OBJ_THING.subKey("mysub")).isAbsent());
        
        latch.countDown();
        
        // Can now finish task, so will return expectedVal
        assertEquals(entity.config().get(TestEntity.CONF_MAP_OBJ_THING), ImmutableMap.of("mysub", expectedVal));
        assertEquals(entity.config().get(TestEntity.CONF_MAP_OBJ_THING.subKey("mysub")), expectedVal);
        
        assertEquals(entity.config().getNonBlocking(TestEntity.CONF_MAP_OBJ_THING).get(), ImmutableMap.of("mysub", expectedVal));
        assertEquals(entity.config().getNonBlocking(TestEntity.CONF_MAP_OBJ_THING.subKey("mysub")).get(), expectedVal);
    }
    
    @Test
    public void testGetConfigKeysReturnsFromSuperAndInterfacesAndSubClass() throws Exception {
        MySubEntity entity = app.addChild(EntitySpec.create(MySubEntity.class));
        assertEquals(entity.getEntityType().getConfigKeys(), ImmutableSet.of(
                MySubEntity.SUPER_KEY_1, MySubEntity.SUPER_KEY_2, MySubEntity.SUB_KEY_2, MySubEntity.INTERFACE_KEY_1, AbstractEntity.DEFAULT_DISPLAY_NAME));
    }

    @Test
    public void testConfigKeyDefaultUsesValueInSubClass() throws Exception {
        MySubEntity entity = app.addChild(EntitySpec.create(MySubEntity.class));
        assertEquals(entity.getConfig(MyBaseEntity.SUPER_KEY_1), "overridden superKey1 default");
    }

    @Test
    public void testConfigureFromKey() throws Exception {
        MySubEntity entity = app.addChild(EntitySpec.create(MySubEntity.class)
                .configure(MySubEntity.SUPER_KEY_1, "changed"));
        assertEquals(entity.getConfig(MySubEntity.SUPER_KEY_1), "changed");
    }

    @Test
    public void testConfigureFromSuperKey() throws Exception {
        MySubEntity entity = app.addChild(EntitySpec.create(MySubEntity.class)
                .configure(MyBaseEntity.SUPER_KEY_1, "changed"));
        assertEquals(entity.getConfig(MySubEntity.SUPER_KEY_1), "changed");
    }

    @Test
    public void testConfigSubMap() throws Exception {
        MySubEntity entity = app.addChild(EntitySpec.create(MySubEntity.class));
        entity.config().set(MyBaseEntity.SUPER_KEY_1, "s1");
        entity.config().set(MySubEntity.SUB_KEY_2, "s2");
        ConfigMap sub = entity.getConfigMap().submap(ConfigPredicates.nameMatchesGlob("sup*"));
        Assert.assertEquals(sub.getConfigRaw(MyBaseEntity.SUPER_KEY_1, true).get(), "s1");
        Assert.assertFalse(sub.getConfigRaw(MySubEntity.SUB_KEY_2, true).isPresent());
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testFailFastOnInvalidConfigKeyCoercion() throws Exception {
        MyOtherEntity entity = app.addChild(EntitySpec.create(MyOtherEntity.class));
        ConfigKey<Integer> key = MyOtherEntity.INT_KEY;
        entity.config().set((ConfigKey)key, "thisisnotanint");
    }

    @Test
    public void testGetConfigWithDeferredSupplierReturnsSupplied() throws Exception {
        DeferredSupplier<Integer> supplier = new DeferredSupplier<Integer>() {
            volatile int next = 0;
            public Integer get() {
                return next++;
            }
        };

        MyOtherEntity entity = app.addChild(EntitySpec.create(MyOtherEntity.class));
        entity.config().set((ConfigKey)MyOtherEntity.INT_KEY, supplier);

        assertEquals(entity.getConfig(MyOtherEntity.INT_KEY), Integer.valueOf(0));
        assertEquals(entity.getConfig(MyOtherEntity.INT_KEY), Integer.valueOf(1));
    }

    @Test
    public void testGetConfigWithFutureWaitsForResult() throws Exception {
        LatchingCallable<String> work = new LatchingCallable<String>("abc");
        Future<String> future = executor.submit(work);

        final MyOtherEntity entity = app.addChild(EntitySpec.create(MyOtherEntity.class));
        entity.config().set((ConfigKey)MyOtherEntity.STRING_KEY, future);

        Future<String> getConfigFuture = executor.submit(new Callable<String>() {
            public String call() {
                return entity.getConfig(MyOtherEntity.STRING_KEY);
            }});

        assertTrue(work.latchCalled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertFalse(getConfigFuture.isDone());

        work.latchContinued.countDown();
        assertEquals(getConfigFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), "abc");
    }

    @Test
    public void testGetConfigWithExecutedTaskWaitsForResult() throws Exception {
        LatchingCallable<String> work = new LatchingCallable<String>("abc");
        Task<String> task = executionManager.submit(work);

        final MyOtherEntity entity = app.addChild(EntitySpec.create(MyOtherEntity.class)
                .configure(MyOtherEntity.STRING_KEY, task));

        Future<String> getConfigFuture = executor.submit(new Callable<String>() {
            public String call() {
                return entity.getConfig(MyOtherEntity.STRING_KEY);
            }});

        assertTrue(work.latchCalled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertFalse(getConfigFuture.isDone());

        work.latchContinued.countDown();
        assertEquals(getConfigFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), "abc");
        assertEquals(work.callCount.get(), 1);
    }

    @Test
    public void testGetConfigWithUnexecutedTaskIsExecutedAndWaitsForResult() throws Exception {
        LatchingCallable<String> work = new LatchingCallable<String>("abc");
        Task<String> task = new BasicTask<String>(work);

        final MyOtherEntity entity = app.addChild(EntitySpec.create(MyOtherEntity.class)
                .configure(MyOtherEntity.STRING_KEY, task));

        Future<String> getConfigFuture = executor.submit(new Callable<String>() {
            public String call() {
                return entity.getConfig(MyOtherEntity.STRING_KEY);
            }});

        assertTrue(work.latchCalled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertFalse(getConfigFuture.isDone());

        work.latchContinued.countDown();
        assertEquals(getConfigFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), "abc");
        assertEquals(work.callCount.get(), 1);
    }

    @ImplementedBy(MyBaseEntityImpl.class)
    public interface MyBaseEntity extends EntityInternal {
        public static final ConfigKey<String> SUPER_KEY_1 = ConfigKeys.newStringConfigKey("superKey1", "superKey1 key", "superKey1 default");
        public static final ConfigKey<String> SUPER_KEY_2 = ConfigKeys.newStringConfigKey("superKey2", "superKey2 key", "superKey2 default");
    }
    
    public static class MyBaseEntityImpl extends AbstractEntity implements MyBaseEntity {
    }

    @ImplementedBy(MySubEntityImpl.class)
    public interface MySubEntity extends MyBaseEntity, MyInterface {
        public static final ConfigKey<String> SUPER_KEY_1 = ConfigKeys.newConfigKeyWithDefault(MyBaseEntity.SUPER_KEY_1, "overridden superKey1 default");
        public static final ConfigKey<String> SUB_KEY_2 = ConfigKeys.newStringConfigKey("subKey2", "subKey2 key", "subKey2 default");
    }
    
    public static class MySubEntityImpl extends MyBaseEntityImpl implements MySubEntity {
    }

    public interface MyInterface {
        public static final ConfigKey<String> INTERFACE_KEY_1 = ConfigKeys.newStringConfigKey("interfaceKey1", "interface key 1", "interfaceKey1 default");
    }

    @ImplementedBy(MyOtherEntityImpl.class)
    public interface MyOtherEntity extends Entity {
        public static final ConfigKey<Integer> INT_KEY = ConfigKeys.newIntegerConfigKey("intKey", "int key", 1);
        public static final ConfigKey<String> STRING_KEY = ConfigKeys.newStringConfigKey("stringKey", "string key", null);
        public static final ConfigKey<Object> OBJECT_KEY = ConfigKeys.newConfigKey(Object.class, "objectKey", "object key", null);
        public static final ConfigKey<Closure> CLOSURE_KEY = ConfigKeys.newConfigKey(Closure.class, "closureKey", "closure key", null);
        public static final ConfigKey<Future> FUTURE_KEY = ConfigKeys.newConfigKey(Future.class, "futureKey", "future key", null);
        public static final ConfigKey<Task> TASK_KEY = ConfigKeys.newConfigKey(Task.class, "taskKey", "task key", null);
        public static final ConfigKey<Predicate> PREDICATE_KEY = ConfigKeys.newConfigKey(Predicate.class, "predicateKey", "predicate key", null);
        public static final IntegerAttributeSensorAndConfigKey SENSOR_AND_CONFIG_KEY = new IntegerAttributeSensorAndConfigKey("sensorConfigKey", "sensor+config key", 1);
    }
    
    public static class MyOtherEntityImpl extends AbstractEntity implements MyOtherEntity {
    }

    static class LatchingCallable<T> implements Callable<T> {
        final CountDownLatch latchCalled = new CountDownLatch(1);
        final CountDownLatch latchContinued = new CountDownLatch(1);
        final AtomicInteger callCount = new AtomicInteger(0);
        final T result;
        
        public LatchingCallable(T result) {
            this.result = result;
        }
        
        @Override
        public T call() throws Exception {
            callCount.incrementAndGet();
            latchCalled.countDown();
            latchContinued.await();
            return result;
        }
    }

    public static class MyEntity extends AbstractEntity {
        public static final ConfigKey<String> MY_CONFIG = ConfigKeys.newStringConfigKey("myentity.myconfig");

        @SetFromFlag("myconfigflagname")
        public static final ConfigKey<String> MY_CONFIG_WITH_FLAGNAME = ConfigKeys.newStringConfigKey("myentity.myconfigwithflagname");
        
        @Override
        public void init() {
            super.init();
            
            // Just calling this to prove we can! When config() was changed to return BasicConfigurationSupport,
            // it broke because BasicConfigurationSupport was private.
            config().getLocalBag();
        }
    }
    
    public static class MyChildEntity extends AbstractEntity {
        public static final ConfigKey<String> MY_CHILD_CONFIG = ConfigKeys.newStringConfigKey("mychildentity.myconfig");

        @SetFromFlag("mychildconfigflagname")
        public static final ConfigKey<String> MY_CHILD_CONFIG_WITH_FLAGNAME = ConfigKeys.newStringConfigKey("mychildentity.myconfigwithflagname");
    }
}
