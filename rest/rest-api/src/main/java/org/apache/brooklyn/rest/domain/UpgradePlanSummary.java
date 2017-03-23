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

package org.apache.brooklyn.rest.domain;

import java.util.ArrayList;

import org.apache.brooklyn.core.upgrade.Modification;
import org.apache.brooklyn.core.upgrade.UpgradePlan;

public class UpgradePlanSummary {

    private final Iterable<ApiError> errors;
    private final Iterable<ModificationSummary> modifications;
    private final Iterable<String> noOps;

    public UpgradePlanSummary(UpgradePlan upgradePlan) {
        ArrayList<ApiError> errors = new ArrayList<>();
        for (Exception e : upgradePlan.getErrors()) {
            errors.add(new ApiError(e.getMessage()));
        }
        this.errors = errors;
        ArrayList<ModificationSummary> modifications = new ArrayList<>();
        for (Modification mod : upgradePlan.getModifications()) {
            modifications.add(new ModificationSummary(mod));
        }
        this.modifications = modifications;
        noOps = upgradePlan.getNoOps();
    }

    public Iterable<ApiError> getErrors() {
        return errors;
    }

    public Iterable<ModificationSummary> getModifications() {
        return modifications;
    }

    public Iterable<String> getNoOps() {
        return noOps;
    }
}
