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
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class UpgradePlanMatcherCallback implements EntityAndSpecMatcherCallback {

    private static final Logger LOG = LoggerFactory.getLogger(UpgradePlanMatcherCallback.class);

    private final UpgradePlan plan = new UpgradePlan();
    private final boolean resetConfig;

    public UpgradePlanMatcherCallback(boolean resetConfig) {
        this.resetConfig = resetConfig;
    }

    public UpgradePlan getPlan() {
        return plan;
    }

    @Override
    public void onMatch(Entity entity, EntitySpec<?> spec) {
        checkCatalogId(entity, spec);
        if (resetConfig) {
            resetConfig(entity, spec);
        } else {
            plan.addNoop("Ignoring configuration changes on " + entity + ": resetConfig is false");
        }
    }

    @Override
    public void unmatched(Entity entity) {
        plan.addNoop(entity + " could not be matched with a spec");
    }

    @Override
    public void unmatched(EntitySpec<?> spec, Entity parent) {
        LOG.debug("Unmatched spec under {}: {}", parent, spec);
        plan.addError(new IllegalArgumentException("Unmatched spec under " + parent + ": " + spec));
        //modifications.add(Modifications.addChild(parent, spec));
    }

    @VisibleForTesting
    void checkCatalogId(Entity entity, EntitySpec<?> spec) {
        String entityCatalogId = entity.getCatalogItemId();
        String specCatalogId = spec.getCatalogItemId();
        if (entityCatalogId != null && specCatalogId != null && !entityCatalogId.equals(specCatalogId)) {
            // Format should be name:number.
            String[] entityIds = entityCatalogId.split(":");
            String[] specIds = specCatalogId.split(":");
            // Throw? Warn? What other context can be given in each case?
            if (entityIds.length != 2) {
                plan.addError(new IllegalArgumentException("Unexpected entity catalog id format: " + entityCatalogId));
            } else if (specIds.length != 2) {
                plan.addError(new IllegalArgumentException("Unexpected spec catalog id format: " + specCatalogId));
            } else {
                plan.addModification(Modifications.changeCatalogId(entity, entityIds[0], entityIds[1], specIds[0], specIds[1]));
            }
        } else if (entityCatalogId == null && specCatalogId != null){
            plan.addNoop("Cannot change catalog item id of " + entity + " to " + specCatalogId + ": its current catalog id is null");
        } else if (entityCatalogId != null && specCatalogId == null) {
            plan.addNoop("Cannot change catalog item id of " + entity + " from " + entityCatalogId + " to null");
        }
    }

    @VisibleForTesting
    void resetConfig(Entity entity, EntitySpec<?> spec) {
        // TODO: Make sure this is consistent with whatever happens when configuration is applied when apps are created.
        Map<String, ?> specFlags = spec.getFlags();
        // getAnnotatedConfigKeys includes flags defined in entity's superclasses and interfaces.
        Map<ConfigKey<?>, SetFromFlag> entityFlags = FlagUtils.getAnnotatedConfigKeys(entity.getClass());
        Map<ConfigKey<?>, Object> specConfig = new HashMap<>(spec.getConfig());
        // Merge spec flags with config and treat together.
        for (Map.Entry<ConfigKey<?>, SetFromFlag> entry : entityFlags.entrySet()) {
            if (specFlags.containsKey(entry.getValue().value())) {
                specConfig.put(entry.getKey(), specFlags.get(entry.getValue().value()));
            }
        }
        plan.addModification(Modifications.resetConfig(entity, specConfig));
    }

}
