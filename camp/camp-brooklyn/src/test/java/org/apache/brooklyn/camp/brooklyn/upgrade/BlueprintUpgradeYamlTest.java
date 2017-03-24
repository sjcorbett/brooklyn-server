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
import static org.testng.Assert.assertTrue;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.upgrade.EntityAndSpecMatcher;
import org.apache.brooklyn.core.upgrade.Modification;
import org.apache.brooklyn.core.upgrade.UpgradePlanMatcherCallback;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class BlueprintUpgradeYamlTest extends AbstractYamlTest {

    @Test
    public void testSimpleConfigChange() throws Exception {
        Entity app = loadAndStart("classpath://upgrades/simple-config-change.v1.yaml");
        EntitySpec<?> spec = loadSpec("classpath://upgrades/simple-config-change.v2.yaml");

        UpgradePlanMatcherCallback callback = new UpgradePlanMatcherCallback(true);
        EntityAndSpecMatcher matcher = new EntityAndSpecMatcher(callback);

        matcher.match(app, spec);
        assertFalse(Iterables.isEmpty(callback.getPlan().getModifications()), "no modifications created");

        for (Modification mod : callback.getPlan().getModifications()) {
            System.out.println(mod);
        }
    }

    @Test
    public void testVersionChange() throws Exception {
        loadIntoCatalogue("classpath://upgrades/version-change.catalog.v1.yaml");
        Entity app = loadAndStart("classpath://upgrades/version-change.v1.yaml");
        loadIntoCatalogue("classpath://upgrades/version-change.catalog.v2.yaml");
        EntitySpec<?> spec = loadSpec("classpath://upgrades/version-change.v2.yaml");

        UpgradePlanMatcherCallback callback = new UpgradePlanMatcherCallback(true);
        EntityAndSpecMatcher matcher = new EntityAndSpecMatcher(callback);
        matcher.match(app, spec);
        assertFalse(Iterables.isEmpty(callback.getPlan().getModifications()), "no modifications created");
        for (Modification mod : callback.getPlan().getModifications()) {
            System.out.println(mod);
        }
    }

    @Test
    public void testComplexChange() throws Exception {
        Entity app = loadAndStart("classpath://upgrades/complex-upgrade.v1.yaml");
        EntitySpec<?> spec = loadSpec("classpath://upgrades/complex-upgrade.v2.yaml");

        UpgradePlanMatcherCallback callback = new UpgradePlanMatcherCallback(true);
        EntityAndSpecMatcher matcher = new EntityAndSpecMatcher(callback);

        matcher.match(app, spec);
        assertFalse(Iterables.isEmpty(callback.getPlan().getModifications()), "no modifications created");

        for (Modification mod : callback.getPlan().getModifications()) {
            System.out.println(mod);
        }
    }


    @Test
    public void testExampleFromDiscussionDoc() throws Exception {
        loadIntoCatalogue("classpath://upgrades/example-from-discussion-doc.v1.yaml");
        Entity app = loadAndStart("classpath://upgrades/example-from-discussion-doc-app.v1.yaml");
        final Entity testEntity = Iterables.getOnlyElement(app.getChildren());
        testEntity.config().set(ConfigKeys.newIntegerConfigKey("keyC"), 9);

        loadIntoCatalogue("classpath://upgrades/example-from-discussion-doc.v2.yaml");

        EntitySpec<?> spec = loadSpec("classpath://upgrades/example-from-discussion-doc-app.v2.yaml");
        final EntitySpec<?> childSpec = Iterables.getOnlyElement(spec.getChildren());

        UpgradePlanMatcherCallback callback = new UpgradePlanMatcherCallback(true);
        EntityAndSpecMatcher matcher = new EntityAndSpecMatcher(callback);

        matcher.match(testEntity, childSpec);
        final Iterable<Modification> modifications = callback.getPlan().getModifications();
        assertFalse(Iterables.isEmpty(modifications), "no modifications created");

        for (Modification mod : modifications) {
            System.out.println(mod);
        }
        final Modification cat = Iterables.find(modifications, contains("Change catalog item id"));
        assertTrue(cat.toString().contains(childSpec.getCatalogItemId()));

        final Modification reset = Iterables.find(modifications, contains("Reset configuration"));

        assertTrue(reset.toString().contains("keyA=2"), "Unexpected upgraded config value");

        // maybe TODO? keyB=9 (spec overrides are kept)
        assertTrue(reset.toString().contains("keyB=2"), "Unexpected upgraded config value");

        // maybe TODO? keyC=9 (out-of-band change is kept)
        assertTrue(reset.toString().contains("keyC=2"), "Unexpected upgraded config value");

        assertTrue(reset.toString().contains("keyD=2"), "Unexpected upgraded config value");
    }

    private Predicate<Modification> contains(final String text) {
        return new Predicate<Modification>() {
            @Override
            public boolean apply(@Nullable Modification input) {
                return input.toString().contains(text);
            }
        };
    }

    private Entity loadAndStart(String resource) throws Exception {
        String originalBlueprint = load(resource);
        return createAndStartApplication(originalBlueprint);
    }

    private EntitySpec<?> loadSpec(String resource) {
        String upgradeBlueprint = load(resource);
        return createAppEntitySpec(upgradeBlueprint);
    }

    private void loadIntoCatalogue(String resource) {
        String yaml = load(resource);
        addCatalogItems(yaml);
    }

    private String load(String resource) {
        return new ResourceUtils(this).getResourceAsString(resource);
    }

}
