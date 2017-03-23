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
import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.testng.annotations.Test;

public class ModificationGeneratingCallbackTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testCheckCatalogId() {
        app.setCatalogItemId("catalog:0.1");
        EntitySpec<?> spec = EntitySpec.create(BasicApplication.class)
                .catalogItemId("catalog:0.2");
        ModificationGeneratingCallback callback = new ModificationGeneratingCallback(true);
        Modification mod = callback.checkCatalogId(app, spec);
        assertNotNull(mod);
        assertEquals(mod.getClass(), Modifications.ChangeCatalogItemId.class);
    }

    @Test
    public void testErrorWhenCatalogItemMismatch() {
        app.setCatalogItemId("catalog:0.1");
        EntitySpec<?> spec = EntitySpec.create(BasicApplication.class)
                .catalogItemId("adifferentcatalog:0.2");
        ModificationGeneratingCallback callback = new ModificationGeneratingCallback(true);
        Modification mod = callback.checkCatalogId(app, spec);
        assertNotNull(mod);
        assertEquals(mod.getClass(), Modifications.ChangeCatalogItemId.class);    }

    @Test
    public void testCompareConfig() {
        app.config().set(TestEntity.CONF_NAME, "original");
        EntitySpec<?> spec = EntitySpec.create(BasicApplication.class)
                .configure(TestEntity.CONF_NAME, "update");
        ModificationGeneratingCallback callback = new ModificationGeneratingCallback(true);
        Modification mod = callback.resetConfig(app, spec);
        assertNotNull(mod);
        assertEquals(mod.getClass(), Modifications.ResetConfig.class);
    }

    @Test
    public void testCompareConfigWithFlag() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "original"));
        EntitySpec<TestEntity> upgradeSpec = EntitySpec.create(TestEntity.class)
                .configure("confName", "updated");
        ModificationGeneratingCallback callback = new ModificationGeneratingCallback(true);
        Modification mod = callback.resetConfig(entity, upgradeSpec);
        assertNotNull(mod);
        assertEquals(mod.getClass(), Modifications.ResetConfig.class);
    }

}