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

import java.util.Iterator;
import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindManager;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.mgmt.rebind.RebindManagerImpl;
import org.apache.brooklyn.core.mgmt.rebind.transformer.CompoundTransformer;
import org.apache.brooklyn.util.collections.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Modifications {
    
    private static final Logger LOG = LoggerFactory.getLogger(Modifications.class);

    static class GroupingModification extends AbstractModification {
        private final Iterable<Modification> modifications;

        GroupingModification(Iterable<Modification> modifications) {
            this.modifications = modifications;
        }

        @Override
        public String description() {
            StringBuilder sb = new StringBuilder("[");
            Iterator<Modification> it = modifications.iterator();
            while (it.hasNext()) {
                Modification mod = it.next();
                sb.append(mod.description());
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }

        @Override
        void doApply() {
            for (Modification modification : modifications) {
                modification.apply();
            }
        }
    }

    public static Modification grouping(Iterable<Modification> modifications) {
        return new GroupingModification(modifications);
    }

    static class SetConfig extends AbstractModification {
        private final Entity target;
        private final ConfigKey key;
        private final Object value;

        SetConfig(Entity target, ConfigKey key, Object value) {
            this.target = target;
            this.key = key;
            this.value = value;
        }

        @Override
        public String description() {
            return "Set " + key.getName() + " to " + value + " on " + target;
        }

        @Override
        void doApply() {
            target.config().set(key, value);
        }
    }

    public static Modification setConfig(Entity entity, ConfigKey key, Object value) {
        return new SetConfig(entity, key, value);
    }

    static class ResetConfig extends AbstractModification {
        private final Entity target;
        private final Map<ConfigKey<?>, Object> config;

        ResetConfig(Entity target, Map<ConfigKey<?>, Object> config) {
            this.target = target;
            // Mutable in case config contains nulls
            this.config = MutableMap.copyOf(config);
        }

        @Override
        public String description() {
            StringBuilder sb = new StringBuilder("{");
            Iterator<ConfigKey<?>> it = config.keySet().iterator();
            while (it.hasNext()) {
                ConfigKey key = it.next();
                sb.append(key.getName())
                        .append("=")
                        .append(config.get(key));
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("}");
            return "Reset configuration on " + target + " to " + sb;
        }

        @Override
        void doApply() {
            AbstractEntity.BasicConfigurationSupport eC = (AbstractEntity.BasicConfigurationSupport) target.config();
            eC.removeAllLocalConfig();
            for (Map.Entry<ConfigKey<?>, Object> entry : config.entrySet()) {
                target.config().set((ConfigKey) entry.getKey(), entry.getValue());
            }
        }
    }

    public static Modification resetConfig(Entity entity, Map<ConfigKey<?>, Object> newConfig) {
        return new ResetConfig(entity, newConfig);
    }

    static class AddChild extends AbstractModification {
        private final Entity parent;
        private final EntitySpec<?> spec;

        public AddChild(Entity parent, EntitySpec<?> spec) {
            this.parent = parent;
            this.spec = spec;
        }

        @Override
        void doApply() {
            this.parent.addChild(spec);
        }

        @Override
        public String description() {
            return "Add new child to " + parent + " using " + spec;
        }
    }

    public static Modification addChild(Entity parent, EntitySpec<?> spec) {
        return new AddChild(parent, spec);
    }

    static class ChangeCatalogItemId extends AbstractModification {
        final Entity entity;
        final String oldCatalogItem;
        final String oldVersion;
        final String newCatalogItem;
        final String newVersion;

        public ChangeCatalogItemId(Entity entity, String oldCatalogItem, String oldVersion, String newCatalogItem, String newVersion) {
            this.entity = entity;
            this.oldCatalogItem = oldCatalogItem;
            this.oldVersion = oldVersion;
            this.newCatalogItem = newCatalogItem;
            this.newVersion = newVersion;
        }

        @Override
        public String description() {
            String oldV = oldCatalogItem + ":" + oldVersion;
            String newV = newCatalogItem + ":" + newVersion;
            return "Change catalog item id of " + entity + " from " + oldV + " to " + newV;
        }

        @Override
        void doApply() {
            ManagementContext mgmt = entity.getApplication().getManagementContext();
            RebindManager rebindManager = mgmt.getRebindManager();
            if (rebindManager instanceof RebindManagerImpl) {
                if (!oldCatalogItem.equals(newCatalogItem)) {
                    LOG.debug("Changing catalog type of {} from {} to {}", new Object[]{entity, oldCatalogItem, newCatalogItem});
                }
                RebindManagerImpl impl = (RebindManagerImpl) rebindManager;
                CompoundTransformer transformation = CompoundTransformer.builder().changeCatalogItemId(
                        oldCatalogItem, oldVersion, newCatalogItem, newVersion).build();
                impl.rebindPartialActive(transformation, entity.getId());
            } else {
                throw new IllegalStateException("Expected instance of RebindManagerImpl: " + rebindManager);
            }
        }
    }

    public static Modification changeCatalogId(Entity entity, String oldCatalogItem, String oldVersion, String newCatalogItem, String newVersion) {
        return new ChangeCatalogItemId(entity, oldCatalogItem, oldVersion, newCatalogItem, newVersion);
    }

}
