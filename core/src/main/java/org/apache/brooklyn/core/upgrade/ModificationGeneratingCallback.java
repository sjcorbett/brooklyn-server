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
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class ModificationGeneratingCallback implements EntityAndSpecMatcherCallback {

    private static final Logger LOG = LoggerFactory.getLogger(ModificationGeneratingCallback.class);

    private final List<Modification> modifications = Lists.newLinkedList();

    private final boolean resetConfig;

    public ModificationGeneratingCallback(boolean resetConfig) {
        this.resetConfig = resetConfig;
    }

    /** @return An immutable copy of modifications */
    public List<Modification> getModifications() {
        return ImmutableList.copyOf(modifications);
    }

    @Override
    public void onMatch(Entity entity, EntitySpec<?> spec) {
        Modification catalogReset = checkCatalogId(entity, spec);
        if (catalogReset != null) {
            modifications.add(catalogReset);
        }
        if (resetConfig) {
            modifications.add(resetConfig(entity, spec));
        }
    }

    @Override
    public void unmatched(Entity entity) {
        LOG.debug("Unmatched child: " + entity);
    }

    @Override
    public void unmatched(EntitySpec<?> spec, Entity parent) {
        LOG.debug("Unmatched spec under {}: {}", parent, spec);
        throw new IllegalArgumentException("Unmatched spec under " + parent + ": " + spec);
        //modifications.add(Modifications.addChild(parent, spec));
    }

    @VisibleForTesting
    Modification checkCatalogId(Entity entity, EntitySpec<?> spec) {
        String entityCatalogId = entity.getCatalogItemId();
        String specCatalogId = spec.getCatalogItemId();
        if (entityCatalogId != null && specCatalogId != null && !entityCatalogId.equals(specCatalogId)) {
            // Format should be name:number.
            String[] entityIds = entityCatalogId.split(":");
            String[] specIds = specCatalogId.split(":");
            if (entityIds.length != 2) {
                // Throw? Warn?
                throw new IllegalArgumentException("Unexpected entity catalog id format: " + entityCatalogId);
            } else if (specIds.length != 2) {
                throw new IllegalArgumentException("Unexpected spec catalog id format: " + specCatalogId);
            } else if (!entityIds[0].equals(specIds[0])) {
                LOG.debug("Changing catalog type of {} from {} to {}", new Object[]{entity, entityIds[0], specIds[0]});
            }
            return Modifications.changeCatalogId(entity, entityIds[0], entityIds[1], specIds[0], specIds[1]);
        } else {
            // todo could return a no-op modification and filter them out from getModifications
            return null;
        }
    }

    @VisibleForTesting
    Modification resetConfig(Entity entity, EntitySpec<?> spec) {
        // Merge spec flags with config and treat together.
        // TODO: Make sure this is consistent with whatever happens when configuration is applied when apps are created.
        Map<String, ?> specFlags = spec.getFlags();
        // getAnnotatedConfigKeys includes flags defined in entity's superclasses and interfaces.
        Map<ConfigKey<?>, SetFromFlag> entityFlags = FlagUtils.getAnnotatedConfigKeys(entity.getClass());
        Map<ConfigKey<?>, Object> specConfig = new HashMap<>(spec.getConfig());

        for (Map.Entry<ConfigKey<?>, SetFromFlag> entry : entityFlags.entrySet()) {
            if (specFlags.containsKey(entry.getValue().value())) {
                specConfig.put(entry.getKey(), specFlags.get(entry.getValue().value()));
            }
        }

        return Modifications.resetConfig(entity, specConfig);
    }

}
