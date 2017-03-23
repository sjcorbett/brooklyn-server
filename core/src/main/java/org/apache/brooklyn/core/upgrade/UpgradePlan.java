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

import java.util.List;

import org.apache.brooklyn.util.exceptions.CompoundRuntimeException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class UpgradePlan {

    private final List<Modification> modifications = Lists.newLinkedList();
    private final List<Exception> errors = Lists.newLinkedList();
    // Intend for this to be contextual data about why certain things _weren't_ acted on.
    private final List<String> noOps = Lists.newLinkedList();

    public void run() {
        if (!errors.isEmpty()) {
            throw new CompoundRuntimeException("Cannot apply modifications", errors);
        } else {
            Modifications.grouping(modifications).apply();
        }
    }

    UpgradePlan addModification(Modification modification) {
        modifications.add(modification);
        return this;
    }

    UpgradePlan addError(Exception exception) {
        errors.add(exception);
        return this;
    }

    UpgradePlan addNoop(String context) {
        noOps.add(context);
        return this;
    }

    /**
     * @return An immutable copy of modifications
     */
    public Iterable<Modification> getModifications() {
        return ImmutableList.copyOf(modifications);
    }

    public Iterable<String> getNoOps() {
        return ImmutableList.copyOf(noOps);
    }

    public Iterable<? extends Exception> getErrors() {
        return ImmutableList.copyOf(errors);
    }

}
