/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sshd.common.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class GenericUtils {

    public static final String trimToEmpty(String s) {
        if (s == null) {
            return "";
        } else {
            return s.trim();
        }
    }

    public static final int length(CharSequence cs) {
        if (cs == null) {
            return 0;
        } else {
            return cs.length();
        }
    }

    public static final boolean isEmpty(CharSequence cs) {
        if (length(cs) <= 0) {
            return true;
        } else {
            return false;
        }
    }
    
    public static final int size(Collection<?> c) {
        if (c == null) {
            return 0;
        } else {
            return c.size();
        }
    }

    public static final boolean isEmpty(Collection<?> c) {
        if (size(c) <= 0) {
            return true;
        } else {
            return false;
        }
    }

    public static final int size(Map<?,?> m) {
        if (m == null) {
            return 0;
        } else {
            return m.size();
        }
    }

    public static final boolean isEmpty(Map<?,?> m) {
        if (size(m) <= 0) {
            return true;
        } else {
            return false;
        }
    }

    @SafeVarargs
    public static final <T> int length(T ... a) {
        if (a == null) {
            return 0;
        } else {
            return a.length;
        }
    }

    @SafeVarargs
    public static final <T> boolean isEmpty(T ... a) {
        if (length(a) <= 0) {
            return true;
        } else {
            return false;
        }
    }

    @SafeVarargs    // there is no EnumSet.of(...) so we have to provide our own
    public static final <E extends Enum<E>> Set<E> of(E ... values) {
        return of(isEmpty(values) ? Collections.<E>emptySet() : Arrays.asList(values));
    }

    public static final <E extends Enum<E>> Set<E> of(Collection<? extends E> values) {
        if (isEmpty(values)) {
            return Collections.emptySet();
        }

        Set<E>  result=null;
        for (E v : values) {
            /*
             * A trick to compensate for the fact that we do not have
             * the enum Class to invoke EnumSet.noneOf
             */
            if (result == null) {
                result = EnumSet.of(v);
            } else {
                result.add(v);
            }
        }

        return result;
    }
}
