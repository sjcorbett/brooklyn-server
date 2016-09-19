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
package org.apache.brooklyn.config;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.collections.CollectionMerger;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;

@SuppressWarnings("serial")
public interface ConfigInheritance extends Serializable {
    
    /** marker interface for inheritance contexts, for keys which can define one or more inheritance patterns */
    public interface ConfigInheritanceContext {}

    /** @deprecated since 0.10.0 see implementations of this interface */ @Deprecated
    @Beta
    public enum InheritanceMode {
        NONE,
        IF_NO_EXPLICIT_VALUE,
        DEEP_MERGE
    }
    
    /** @deprecated since 0.10.0 see implementations of this interface */ @Deprecated
    public static final ConfigInheritance NONE = new Legacy.None();
    /** @deprecated since 0.10.0 see implementations of this interface */ @Deprecated
    public static final ConfigInheritance ALWAYS = new Legacy.Always();
    /** @deprecated since 0.10.0 see implementations of this interface */ @Deprecated
    public static final ConfigInheritance DEEP_MERGE = new Legacy.Merged();
    
    @Deprecated
    InheritanceMode isInherited(ConfigKey<?> key, Object from, Object to);

    interface ContainerAndValue<T> {
        Object getContainer();
        T getValue();
        /** if false, the contents of {@link #getValue()} will have come from the default */
        boolean isValueSet();
    }
    
    interface ContainerAndKeyValue<T> extends ContainerAndValue<T> {
        ConfigKey<T> getKey();
        T getDefaultValue();
    }
    
    /** 
     * given a key and local value, together with an optional record of ancestor containers (eg an entity) and associated data,
     * this finds the value for a config key <b>applying the appropriate inheritance strategies</b>.
     * for instance this may merge a map throughout a container hierarchy, 
     * or this may traverse up until a non-reinheritable key definition is found and in the absence of values lower
     * in the hierarchy this will return the default value of the key 
     * <p>
     * this uses an interface on the input so that:
     * - the caller can supply the hierarchy
     * - hierarchy is only traversed as far as needed
     * - caller can do full resolution as required for local values
     * <p>
     * this returns in interface so that caller can get the value, 
     * and if needed also find the container where the key is defined.
     * that can be useful for when the value needs to be further resolved,
     * e.g. a DSL function or a URL. the returned value may be evaluated lazily,
     * i.e. the actual traversal and evaluation may be deferred until a method on
     * the returned object is invoked.
     * <p>
     * this object is taken as the default inheritance and used if no inheritance is
     * defined on the key.
     * <p>
     * so that the caller can determine if a key/value is to be exported to children from a container,
     * this method should accept an iterable whose first entry has a null container
     * and whose second entry gives the container, key, and potential value to be exported.
     * if null is returned the caller knows nothing is to be exported to children.
     */
    <T> ContainerAndValue<T> resolveInheriting(
        ConfigKey<T> key,
        @Nullable Maybe<T> localValue,
        @Nullable Object container,
        Iterator<? extends ContainerAndKeyValue<T>> ancestorContainerKeyValues,
        ConfigInheritanceContext context);
    
    /** @deprecated since 0.10.0 see implementations of this interface */ @Deprecated
    public static class Legacy {
        public static ConfigInheritance fromString(String val) {
            if (Strings.isBlank(val)) return null;
            switch (val.toLowerCase().trim()) {
            case "none":
                return NONE;
            case "always": 
                return ALWAYS;
            case "deepmerge" :
            case "deep_merge" :
                return DEEP_MERGE;
            default:
                throw new IllegalArgumentException("Invalid config-inheritance '"+val+"' (legal values are none, always or merge)");
            }
        }
        private abstract static class LegacyAbstractConversion implements ConfigInheritance {
            private static class Result<T> implements ContainerAndValue<T> {
                Object container = null;
                T value = null;
                boolean isValueSet = false;
                @Override public Object getContainer() { return container; }
                @Override public T getValue() { return value; }
                @Override public boolean isValueSet() { return isValueSet; }
            }

            // close copy of method in BasicConfigInheritance for this legacy compatibility evaluation
            @Override
            public <T> ContainerAndValue<T> resolveInheriting(
                    @Nullable ConfigKey<T> key, Maybe<T> localValue, Object container,
                    Iterator<? extends ContainerAndKeyValue<T>> ancestorContainerKeyValues, ConfigInheritanceContext context) {
                ConfigInheritance inh = key==null ? null : key.getInheritanceByContext(context);
                if (inh==null) inh = this;
                if (inh!=this) return inh.resolveInheriting(key, localValue, container, ancestorContainerKeyValues, context);
                
                ContainerAndValue<T> v2 = null;
                if (getMode()==InheritanceMode.IF_NO_EXPLICIT_VALUE && localValue.isPresent()) {
                    // don't inherit
                } else if (ancestorContainerKeyValues==null || !ancestorContainerKeyValues.hasNext()) {
                    // nothing to inherit
                } else {
                    // check whether parent allows us to get inherited value
                    ContainerAndKeyValue<T> c = ancestorContainerKeyValues.next();
                    ConfigInheritance inh2 = c.getKey()==null ? null : c.getKey().getInheritanceByContext(context);
                    if (inh2==null) inh2 = this;
                    if (getMode()==InheritanceMode.NONE) {
                        // can't inherit
                    } else {
                        // get inherited value
                        v2 = inh2.resolveInheriting(c.getKey(), 
                            c.isValueSet() ? Maybe.of(c.getValue()) : Maybe.<T>absent(), c.getContainer(), 
                                ancestorContainerKeyValues, context);
                    }
                }

                if (v2!=null && v2.isValueSet() && !localValue.isPresent()) return v2;
                Result<T> v = new Result<T>();
                v.container = container;
                if (v2==null || !v2.isValueSet()) {
                    v.isValueSet = localValue.isPresent();
                    v.value = v.isValueSet() ? localValue.get() : key.getDefaultValue(); 
                } else {
                    v.value = resolveConflict(key, localValue, Maybe.ofAllowingNull(v2.getValue()));
                    v.isValueSet = true;
                }
                return v;
            }
            /** only invoked if there is an ancestor value; custom strategies can overwrite */
            protected <T> T resolveConflict(ConfigKey<T> key, Maybe<T> localValue, Maybe<T> ancestorValue) {
                if (getMode()==InheritanceMode.IF_NO_EXPLICIT_VALUE) {
                    if (localValue.isPresent()) return localValue.get();
                    return ancestorValue.orNull();
                }
                if (getMode()==InheritanceMode.DEEP_MERGE) {
                    return deepMerge(localValue, ancestorValue).orNull();
                }
                throw new IllegalStateException("Unknown config conflict resolution strategy '"+getMode()+"' evaluating "+key);
            }
            private static <T> Maybe<? extends T> deepMerge(Maybe<? extends T> val1, Maybe<? extends T> val2) {
                if (val2.isAbsent() || val2.isNull()) {
                    return val1;
                } else if (val1.isAbsent()) {
                    return val2;
                } else if (val1.isNull()) {
                    return val1; // an explicit null means an override; don't merge
                } else if (val1.get() instanceof Map && val2.get() instanceof Map) {
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    Maybe<T> result = (Maybe)Maybe.of(CollectionMerger.builder().build().merge((Map<?,?>)val1.get(), (Map<?,?>)val2.get()));
                    return result;
                } else {
                    // cannot merge; just return val1
                    return val1;
                }
            }
//            @Override
//            public <T> ContainerAndValue<T> resolveInheriting(ConfigKey<T> key, Maybe<T> localValue, Object container,
//                    Iterator<ContainerAndKeyValue<T>> ancestorContainerKeyValues, ConfigInheritanceContext context) {
//                if (ancestorContainerKeyValues.hasNext()) {
//                    
//                    ContainerAndKeyValue<T> c = ancestorContainerKeyValues.next();
//                    if (c==null) return resolveInheriting(ancestorContainerKeyValues, context);
//                    
//                    ConfigKey<T> key = c.getKey();
//                    ConfigInheritance ci = null;
//                    if (key!=null) ci = key.getInheritanceByContext(context);
//                    if (ci==null) ci = this;
//                    
//                    if (getMode()==InheritanceMode.NONE) {
//                        // don't inherit, fall through to below
//                    } else if (!c.isValueSet()) {
//                        // no value here, try to inherit
//                        ContainerAndValue<T> ri = ci.resolveInheriting(ancestorContainerKeyValues, context);
//                        if (ri.isValueSet()) {
//                            // value found, return it
//                            return ri;
//                        }
//                        // else no inherited, fall through to below
//                    } else {
//                        if (getMode()==InheritanceMode.IF_NO_EXPLICIT_VALUE) {
//                            // don't inherit, fall through to below
//                        } else {
//                            // merging
//                            Maybe<?> mr = deepMerge(asMaybe(c), 
//                                asMaybe(ci.resolveInheriting(ancestorContainerKeyValues, context)));
//                            if (mr.isPresent()) {
//                                Result<T> r = new Result<T>();
//                                r.container = c.getContainer();
//                                r.isValueSet = true;
//                                @SuppressWarnings("unchecked")
//                                T vt = (T) mr.get();
//                                r.value = vt;
//                                return r;
//                            }
//                        }
//                    }
//                    Result<T> r = new Result<T>();
//                    r.container = c.getContainer();
//                    r.isValueSet = c.isValueSet();
//                    r.value = r.isValueSet ? c.getValue() : c.getDefaultValue();
//                    return r;
//                }
//                return new Result<T>();
//            }
            @Override
            public InheritanceMode isInherited(ConfigKey<?> key, Object from, Object to) {
                return getMode();
            }
            protected abstract InheritanceMode getMode();
            private static <T> Maybe<T> asMaybe(ContainerAndValue<T> cv) {
                if (cv.isValueSet()) return Maybe.of(cv.getValue());
                return Maybe.absent();
            }
//            private static <T> Maybe<?> deepMerge(Maybe<? extends T> val1, Maybe<? extends T> val2) {
//                if (val2.isAbsent() || val2.isNull()) {
//                    return val1;
//                } else if (val1.isAbsent()) {
//                    return val2;
//                } else if (val1.isNull()) {
//                    return val1; // an explicit null means an override; don't merge
//                } else if (val1.get() instanceof Map && val2.get() instanceof Map) {
//                    return Maybe.of(CollectionMerger.builder().build().merge((Map<?,?>)val1.get(), (Map<?,?>)val2.get()));
//                } else {
//                    // cannot merge; just return val1
//                    return val1;
//                }
//            }
        }
        private static class Always extends LegacyAbstractConversion {
            @Override
            public InheritanceMode getMode() {
                return InheritanceMode.IF_NO_EXPLICIT_VALUE;
            }
        }
        private static class None extends LegacyAbstractConversion {
            @Override
            public InheritanceMode getMode() {
                return InheritanceMode.NONE;
            }
        }
        private static class Merged extends LegacyAbstractConversion {
            @Override
            public InheritanceMode getMode() {
                return InheritanceMode.DEEP_MERGE;
            }
        }
    }
    
}
