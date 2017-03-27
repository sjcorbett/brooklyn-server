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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.objs.Configurable;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.upgrade.EntityAndSpecMatcher;
import org.apache.brooklyn.core.upgrade.Modification;
import org.apache.brooklyn.core.upgrade.Modifications;
import org.apache.brooklyn.core.upgrade.UpgradePlanMatcherCallback;
import org.apache.brooklyn.util.collections.MutableSet;
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

    @Test
    public void testConfigKeyUpdate() throws Exception {

        final String DOWNLOAD_URL = "download.url";
        final String MYSERVER_PORT = "myserver.port";
        final String MYSERVER_REALM = "myserver.realm";

        final String testUrlFromYamlFile = "http://myserver.example.com/downloads/something.zip";

        loadIntoCatalogue("classpath://upgrades/config-key-v1.yaml");
        Entity testApp = loadAndStart("classpath://upgrades/config-key-app.yaml");
        final Entity largo = Iterables.getOnlyElement(testApp.getChildren());

        ConfigKey<String> downloadUrlAsString = ConfigKeys.newStringConfigKey(DOWNLOAD_URL);
        ConfigKey<Integer> myServerPortAsInt = ConfigKeys.newIntegerConfigKey(MYSERVER_PORT);
        ConfigKey<String> myServerRealm = ConfigKeys.newStringConfigKey(MYSERVER_REALM);
        ConfigKey<java.net.URI> downloadUrlAsUri = ConfigKeys.newConfigKey(java.net.URI.class, DOWNLOAD_URL);
        ConfigKey<PortRange> myServerPortAsPortRange = ConfigKeys.newConfigKey(PortRange.class, MYSERVER_PORT);

        assertEquals(largo.config().get(downloadUrlAsString), testUrlFromYamlFile, "URL did not match expected value");

        final Set<ConfigKey<?>> asString = withNameAndType(largo.config(), downloadUrlAsString);
        assertEquals(asString.size(), 1, "download.uri should have type string");
        assertEquals(largo.config().get(myServerPortAsInt), Integer.valueOf(3456), "port did not match expected value");
        assertEquals(largo.config().get(myServerRealm), "web", "realm did not match expected value");

        loadIntoCatalogue("classpath://upgrades/config-key-v2.yaml");
        EntitySpec<?> spec = loadSpec("classpath://upgrades/config-key-app-v2.yaml");
        final EntitySpec<?> childSpec = Iterables.getOnlyElement(spec.getChildren());

        UpgradePlanMatcherCallback callback = new UpgradePlanMatcherCallback(true);
        EntityAndSpecMatcher matcher = new EntityAndSpecMatcher(callback);

        matcher.match(largo, childSpec);
        final Iterable<Modification> modifications = callback.getPlan().getModifications();
        assertFalse(Iterables.isEmpty(modifications), "no modifications created");

        final Modification reset = Iterables.find(modifications, contains("Reset configuration"));
        Modifications.ResetConfig resetConfig = ((Modifications.ResetConfig) reset);

        // check download.uri key is a URI and not a string
        final Set<ConfigKey<?>> resetDownloadString = withNameAndType(resetConfig.getConfig(), downloadUrlAsString);
        assertEquals(resetDownloadString.size(), 0);
        final Set<ConfigKey<?>> resetDownloadUri = withNameAndType(resetConfig.getConfig(), downloadUrlAsUri);
        assertEquals(resetDownloadUri.size(), 1);

        // check port key is a PortRange not an int
        final Set<ConfigKey<?>> resetPortRange = withNameAndType(resetConfig.getConfig(), myServerPortAsPortRange);
        assertEquals(resetPortRange.size(), 1);
        final String actualPort = resetConfig.getConfig().get(myServerPortAsPortRange).toString();
        assertEquals(actualPort, "3456", "myserver.port value unexpected");

        // TODO once we have implemented applying the modifications to the entity,
        // add the same checks in here that the config keys on the entity have the right value and type.
    }


    // workarounds for BasicConfigKey.equals matching just on name; we want to check the type as well.
    private Set<ConfigKey<?>> withNameAndType(Map<ConfigKey<?>, Object> config, final ConfigKey<?> expected) {
        final MutableSet<ConfigKey<?>> result = MutableSet.of();
        for (ConfigKey<?> key: config.keySet()) {
            if (key.getName().equals(expected.getName()) && key.getTypeName().equals(expected.getTypeName())) {
                result.add(key);
            }
        }
        return result;
    }

    private <T> Set<ConfigKey<?>> withNameAndType(Configurable.ConfigurationSupport config, final ConfigKey<T> expected) {
        final Set<ConfigKey<?>> keysPresent = config.findKeysPresent(new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return input.getName().equals(expected.getName())
                    && input.getTypeName().equals(expected.getTypeName());
            }
        });
        return keysPresent;
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
