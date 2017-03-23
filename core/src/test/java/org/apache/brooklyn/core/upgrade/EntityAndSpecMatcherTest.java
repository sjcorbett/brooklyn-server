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

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class EntityAndSpecMatcherTest extends BrooklynAppUnitTestSupport {

    /*

    test matching app with id
    test matching child of app
    test matching entity from specs on dynamic cluster

    Use mockito for callback?

    later:
    test matching app without id
    what about matches where the ID is right but the java type is wrong? Do we care?

     */
    private static final String APP_PLAN_ID = "app-plan-id";

    EntityAndSpecMatcherCallback callback;

    @BeforeMethod
    @Override
    public void setUp() throws Exception {
        super.setUp();
        callback = Mockito.mock(EntityAndSpecMatcherCallback.class);
        app.config().set(BrooklynConfigKeys.PLAN_ID, APP_PLAN_ID);
    }

    @Test
    public void testBasicMatch() {
        EntitySpec<BasicApplication> spec = EntitySpec.create(BasicApplication.class)
                .configure(BrooklynConfigKeys.PLAN_ID, APP_PLAN_ID);
        EntityAndSpecMatcher matcher = new EntityAndSpecMatcher(callback);
        matcher.match(app, spec);
        Mockito.verify(callback).onMatch(app, spec);
        Mockito.verifyNoMoreInteractions(callback);
    }

}