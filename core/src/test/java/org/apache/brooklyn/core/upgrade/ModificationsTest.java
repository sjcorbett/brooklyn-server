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

package org.apache.brooklyn.core.upgrade;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class ModificationsTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testAddChild() {
        assertEquals(app.getChildren().size(), 0);
        Modifications.addChild(app, EntitySpec.create(TestEntity.class))
                .apply();
        assertEquals(app.getChildren().size(), 1);
    }

    @Test
    public void testSetConfig() {
        assertNull(app.config().get(TestEntity.CONF_OBJECT));
        Modifications.setConfig(app, TestEntity.CONF_OBJECT, "Jimbo")
                .apply();
        assertEquals(app.config().get(TestEntity.CONF_OBJECT), "Jimbo");
    }

    @Test
    public void testResetConfig() {
        final Object appConfigVal = new Object();
        app.config().set(TestEntity.CONF_OBJECT, appConfigVal);
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "confName"));
        assertEquals(entity.config().get(TestEntity.CONF_OBJECT), appConfigVal);
        assertEquals(entity.config().get(TestEntity.CONF_NAME), "confName");
        assertEquals(entity.config().get(TestEntity.CONF_STRING), TestEntity.CONF_STRING.getDefaultValue());
        Modifications.resetConfig(entity, ImmutableMap.<ConfigKey<?>, Object>of(TestEntity.CONF_STRING, "confString"))
                .apply();
        assertEquals(entity.config().get(TestEntity.CONF_OBJECT), appConfigVal);
        assertEquals(entity.config().get(TestEntity.CONF_NAME), TestEntity.CONF_NAME.getDefaultValue());
        assertEquals(entity.config().get(TestEntity.CONF_STRING), "confString");
    }

}
