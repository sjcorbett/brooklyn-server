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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindManager;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.mgmt.rebind.RebindManagerImpl;
import org.apache.brooklyn.core.mgmt.rebind.transformer.CompoundTransformer;

import com.google.common.base.MoreObjects;

class Modifications {

    static abstract class AbstractModification implements Modification {
        private boolean hasFired;
        abstract void doApply();

        @Override
        public boolean isApplied() {
            return hasFired;
        }

        public void apply() {
            if (hasFired) {
                throw new IllegalStateException("Already applied " + this);
            } else {
                hasFired = true;
                doApply();
            }
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("description", description())
                    .add("hasFired", hasFired)
                    .toString();
        }
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
        final String catalogItem;
        final String oldVersion;
        final String newVersion;

        public ChangeCatalogItemId(Entity entity, String catalogItem, String oldVersion, String newVersion) {
            this.entity = entity;
            this.catalogItem = catalogItem;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        @Override
        public String description() {
            String oldV = catalogItem + ":" + oldVersion;
            String newV = catalogItem + ":" + newVersion;
            return "Change catalog item id of " + entity + " from " + oldV + " to " + newV;
        }

        @Override
        void doApply() {
            ManagementContext mgmt = entity.getApplication().getManagementContext();
            RebindManager rebindManager = mgmt.getRebindManager();
            if (rebindManager instanceof RebindManagerImpl) {
                RebindManagerImpl impl = (RebindManagerImpl) rebindManager;
                CompoundTransformer transformation = CompoundTransformer.builder().changeCatalogItemId(
                        catalogItem, oldVersion, catalogItem, newVersion).build();
                impl.rebindPartialActive(transformation, entity.getId());
            } else {
                throw new IllegalStateException("Expected instance of RebindManagerImpl: " + rebindManager);
            }
        }
    }

    public static Modification changeCatalogId(Entity entity, String catalogItem, String oldVersion, String newVersion) {
        return new ChangeCatalogItemId(entity, catalogItem, oldVersion, newVersion);
    }

}
