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

import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.testng.annotations.Test;

public class ModificationGeneratingCallbackTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testX() {
        EntitySpec<TestEntity> spec = EntitySpec.create(TestEntity.class)
                .configure(TestEntity.CONF_NAME, "Bob");
        TestEntity entity = app.createAndManageChild(spec);
        spec.configure(TestEntity.CONF_NAME, "Pop");
        ModificationGeneratingCallback callback = new ModificationGeneratingCallback();
        callback.onMatch(entity, spec);
        List<Modification> mods = callback.getModifications();
        assertEquals(mods.size(), 1, "expected single element in " + mods);
        Modification mod = mods.iterator().next();
        assertEquals(mod.getClass(), Modifications.SetConfig.class);
    }

}
