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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ModificationGeneratingCallback implements EntityAndSpecMatcherCallback {

    private static final Logger LOG = LoggerFactory.getLogger(ModificationGeneratingCallback.class);

    private final List<Modification> modifications = Lists.newLinkedList();

    /** @return An immutable copy of modifications */
    public List<Modification> getModifications() {
        return ImmutableList.copyOf(modifications);
    }

    @Override
    public void onMatch(Entity entity, EntitySpec<?> spec) {
        checkCatalogId(entity, spec);
        compareConfig(entity, spec);
    }

    @Override
    public void unmatched(Entity entity) {
        LOG.warn("Unmatched entity: " + entity);
    }

    @Override
    public void unmatched(EntitySpec<?> spec, Entity parent) {
        modifications.add(Modifications.addChild(parent, spec));
    }

    @VisibleForTesting
    void checkCatalogId(Entity entity, EntitySpec<?> spec) {
        String entityCatalogId = entity.getCatalogItemId();
        String specCatalogId = spec.getCatalogItemId();
        if (entityCatalogId != null && !entityCatalogId.equals(specCatalogId)) {
            // Format should be name:number.
            String[] entityIds = entityCatalogId.split(":");
            String[] specIds = specCatalogId.split(":");
            if (entityIds.length != 2) {
                // Throw? Warn?
                throw new IllegalArgumentException("Unexpected entity catalog id format: " + entityCatalogId);
            } else if (specIds.length != 2) {
                throw new IllegalArgumentException("Unexpected spec catalog id format: " + specCatalogId);
            } else if (!entityIds[0].equals(specIds[0])) {
                throw new IllegalArgumentException("Catalog ids do not match: " + entityCatalogId + " and " + specCatalogId);
            } else {
                modifications.add(Modifications.changeCatalogId(entity, entityIds[0], entityIds[1], specIds[1]));
            }
        }
    }

    @VisibleForTesting
    void compareConfig(Entity entity, EntitySpec<?> spec) {
        AbstractEntity.BasicConfigurationSupport entityConfig = (AbstractEntity.BasicConfigurationSupport) entity.config();
        Map<ConfigKey<?>, Object> localRaw = entityConfig.getAllLocalRaw();

        // Merge spec flags with config and treat together.
        // TODO: Make sure this is consistent with whatever happens when configuration is applied when apps are created.
        Map<String, ?> specFlags = spec.getFlags();
        Map<ConfigKey<?>, SetFromFlag> entityFlags = FlagUtils.getAnnotatedConfigKeys(entity.getClass());
        Map<ConfigKey<?>, Object> specConfig = new HashMap<>(spec.getConfig());

        for (Map.Entry<ConfigKey<?>, SetFromFlag> entry : entityFlags.entrySet()) {
            if (specFlags.containsKey(entry.getValue().value())) {
                specConfig.put(entry.getKey(), specFlags.get(entry.getValue().value()));
            }
        }

        // Config on spec not on entity
        Sets.SetView<ConfigKey<?>> specOnly = Sets.difference(specConfig.keySet(), localRaw.keySet());
        for (ConfigKey<?> key : specOnly) {
            modifications.add(Modifications.setConfig(entity, key, specConfig.get(key)));
        }

        // Config on entity not on spec
        //Sets.SetView<ConfigKey<?>> entityOnly = Sets.difference(localRaw.keySet(), specConfig.keySet());
        //for (ConfigKey<?> key : entityOnly) {
        //    //LOG.info(" --> only on entity: key=" + key.getName() + ", value=" + specConfig.get(key));
        //}

        // Config on both that's changed.
        Sets.SetView<ConfigKey<?>> onBoth = Sets.intersection(specConfig.keySet(), localRaw.keySet());
        for (ConfigKey<?> key : onBoth) {
            Object specValue = specConfig.get(key);
            Object entityValue = localRaw.get(key);
            if (specValue != null) {
                if (!specValue.equals(entityValue)) {
                    modifications.add(Modifications.setConfig(entity, key, specConfig.get(key)));
                }
            } else if (entityValue != null) {
                //LOG.info(" --> entity has value for " + key.getName() + " that's null on the new spec");
            }
        }
    }

}
