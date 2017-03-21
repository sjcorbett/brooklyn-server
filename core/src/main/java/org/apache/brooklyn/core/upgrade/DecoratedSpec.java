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

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.brooklyn.api.entity.EntitySpec;

// TODO: Probably doesn't need to be public?
public class DecoratedSpec {

    public enum DecoratedSpecKind {
        ROOT, CHILD, FLAG, PARAMETER;
    }

    private final EntitySpec<?> spec;
    private final DecoratedSpecKind kind;
    private boolean matched;

    public DecoratedSpec(EntitySpec<?> spec, DecoratedSpecKind kind) {
        this.spec = checkNotNull(spec, "spec");
        this.kind = checkNotNull(kind, "kind");
    }

    public EntitySpec<?> spec() {
        return spec;
    }

    public DecoratedSpecKind kind() {
        return kind;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched() {
        this.matched = true;
    }

    @Override
    public String toString() {
        return "DecoratedSpec{spec=" + spec + ", matched=" + matched + ", kind=" + kind + "}";
    }
}
