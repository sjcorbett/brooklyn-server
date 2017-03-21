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

package org.apache.brooklyn.camp.brooklyn.upgrade;

import static org.testng.Assert.assertFalse;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.upgrade.Modification;
import org.apache.brooklyn.core.upgrade.ModificationGeneratingCallback;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

public class ConfigComparisonTest extends AbstractYamlTest {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigComparisonTest.class);

    @Test
    public void testSimpleConfigChange() throws Exception {
        String originalBlueprint = new ResourceUtils(this).getResourceAsString("classpath://upgrades/simple-config-change.v1.yaml");
        String upgradeBlueprint = new ResourceUtils(this).getResourceAsString("classpath://upgrades/simple-config-change.v2.yaml");

        Entity app = createAndStartApplication(originalBlueprint);
        EntitySpec<?> spec = createAppEntitySpec(upgradeBlueprint);

        ModificationGeneratingCallback callback = new ModificationGeneratingCallback();
        callback.onMatch(app, spec);

        assertFalse(callback.getModifications().isEmpty());

        for (Modification mod : callback.getModifications()) {
            LOG.info(mod.toString());
        }
    }
}
