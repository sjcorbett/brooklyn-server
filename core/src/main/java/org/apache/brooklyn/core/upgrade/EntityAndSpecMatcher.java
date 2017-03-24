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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;

public class EntityAndSpecMatcher {

    private final EntityAndSpecMatcherCallback callback;

    public EntityAndSpecMatcher(EntityAndSpecMatcherCallback callback) {
        this.callback = callback;
    }

    public void match(Entity entity, EntitySpec<?> spec) {
        Deque<Entity> outerE = new ArrayDeque<>();
        outerE.add(entity);

        Set<DecoratedSpec> innerES = new HashSet<>();
        Deque<Set<DecoratedSpec>> outerES = new ArrayDeque<>();
        innerES.add(new DecoratedSpec(spec, DecoratedSpec.DecoratedSpecKind.ROOT));
        outerES.add(innerES);

        match(outerE, outerES, callback);
    }

    private void match(Deque<Entity> ancestry, Deque<Set<DecoratedSpec>> specs, EntityAndSpecMatcherCallback callback) {
        // invariants: ancestry and specs are never empty.
        Entity entity = ancestry.peek();
        DecoratedSpec specMatch = match(entity, specs);

        // Specs that were plausibly the basis for entity's children.
        Set<DecoratedSpec> subSpecs = new HashSet<>();

        if (specMatch != null) {
            callback.onMatch(entity, specMatch.spec());
            specMatch.setMatched();
            subSpecs.addAll(getSubSpecs(specMatch.spec()));
        } else {
            callback.unmatched(entity);
        }

        for (Entity descendant : entity.getChildren()) {
            ancestry.push(descendant);
            // repeatedly popped by recursive call.
            specs.push(subSpecs);
            match(ancestry, specs, callback);
        }

        for (DecoratedSpec subSpec : subSpecs) {
            // Don't care about specs that came from parameters or flags that weren't matched.
            if (!subSpec.isMatched() && !subSpec.kind().isInherited()) {
                callback.unmatched(subSpec.spec(), entity);
            }
        }

        ancestry.pop();
        specs.pop();
    }

    /**
     * Match a single entity with an entry in specs
     */
    private DecoratedSpec match(Entity entity, Deque<Set<DecoratedSpec>> specs) {
        // Checks for an exact PLAN_ID match. Alternatively could score everything in specs and
        // return the highest scoring spec, returning null if a minimum threshold is not met.
        String planId = entity.config().get(BrooklynConfigKeys.PLAN_ID);
        // Only care about child specs when checking the head of specs. It's impossible that entity
        // was created with a child spec on any ancestor other than its parent.
        boolean matchChildSpecs = true;
        if (planId != null) {
            for (Set<DecoratedSpec> spec : specs) {
                for (DecoratedSpec ds : spec) {
                    if (matchChildSpecs || ds.kind().isInherited()) {
                        Object specPlanId = ds.spec().getConfig().get(BrooklynConfigKeys.PLAN_ID);
                        if (specPlanId != null && planId.equals(specPlanId)) {
                            return ds;
                        }
                    }
                }
                matchChildSpecs = false;
            }
        }
        return null;
    }

    /**
     * @return A set containing the children of spec and all EntitySpec parameters and flags.
     */
    private Set<DecoratedSpec> getSubSpecs(EntitySpec<?> spec) {
        Set<DecoratedSpec> specs = new LinkedHashSet<>();

        // Find all the config that's a spec!
        for (SpecParameter<?> parameter : spec.getParameters()) {
            if (parameter.getConfigKey().getTypeToken().isAssignableFrom(EntitySpec.class)) {
                EntitySpec<?> configSpec = null;
                Object defaultValue = parameter.getConfigKey().getDefaultValue();
                if (defaultValue instanceof EntitySpec) {
                    configSpec = (EntitySpec) defaultValue;
                }
                Object configValue = spec.getConfig().get(parameter.getConfigKey());
                if (configValue instanceof EntitySpec) {
                    configSpec = (EntitySpec) configValue;
                }
                if (configSpec != null) {
                    specs.add(new DecoratedSpec(configSpec, DecoratedSpec.DecoratedSpecKind.PARAMETER));
                }
            }
        }

        // Must we support flags?
        for (Object value : spec.getFlags().values()) {
            if (value instanceof EntitySpec) {
                specs.add(new DecoratedSpec((EntitySpec) value, DecoratedSpec.DecoratedSpecKind.FLAG));
            }
        }

        for (EntitySpec<?> childSpec : spec.getChildren()) {
            specs.add(new DecoratedSpec(childSpec, DecoratedSpec.DecoratedSpecKind.CHILD));
        }

        return specs;
    }
}
