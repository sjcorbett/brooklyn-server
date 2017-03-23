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

// TODO: Name!
/*
 * What kind of configuration might a callback use?
 * - whether to reset config on the thing in question
 * - whether to act on a particular entity (both specific and filtering)
 * - possibly what to do on failure? though that's more down to the thing applying modifications.
 */
public interface EntityAndSpecMatcherCallback {

    void onMatch(Entity entity, EntitySpec<?> spec);
    void unmatched(Entity entity);
    void unmatched(EntitySpec<?> spec, Entity parent);

}
