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

package org.apache.sshd.common.signature;

import java.security.spec.ECParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import org.apache.sshd.common.Digest;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.Signature;
import org.apache.sshd.common.cipher.ECCurves;
import org.apache.sshd.common.config.NamedFactoriesListParseResult;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.common.util.ValidateUtils;

/**
 * Provides easy access to the currently implemented signatures
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public enum BuiltinSignatures implements SignatureFactory {
    dsa(KeyPairProvider.SSH_DSS) {
        @Override
        public Signature create() {
            return new SignatureDSA("SHA1withDSA");
        }
    },
    rsa(KeyPairProvider.SSH_RSA) {
        @Override
        public Signature create() {
            return new SignatureRSA();
        }
    },
    nistp256(KeyPairProvider.ECDSA_SHA2_NISTP256) {
        @Override
        public Signature create() {
            return new SignatureECDSA("SHA256withECDSA");
        }
        
        @Override
        public boolean isSupported() {
            return SecurityUtils.isBouncyCastleRegistered() || SecurityUtils.hasEcc();
        }
    },
    nistp384(KeyPairProvider.ECDSA_SHA2_NISTP384) {
        @Override
        public Signature create() {
            return new SignatureECDSA("SHA384withECDSA");
        }
        
        @Override
        public boolean isSupported() {
            return SecurityUtils.isBouncyCastleRegistered() || SecurityUtils.hasEcc();
        }
    },
    nistp521(KeyPairProvider.ECDSA_SHA2_NISTP521) {
        @Override
        public Signature create() {
            return new SignatureECDSA("SHA512withECDSA");
        }
        
        @Override
        public boolean isSupported() {
            return SecurityUtils.isBouncyCastleRegistered() || SecurityUtils.hasEcc();
        }
    };

    private final String factoryName;

    public static Signature getByCurveSize(ECParameterSpec params) {
        int curveSize = ECCurves.getCurveSize(params);
        if (curveSize <= 256) {
            return nistp256.create();
        } else if (curveSize <= 384) {
            return nistp384.create();
        } else {
            return nistp521.create();
        }
    }

    @Override
    public final String getName() {
        return factoryName;
    }

    @Override
    public final String toString() {
        return getName();
    }

    BuiltinSignatures(String facName) {
        factoryName = facName;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    public static final Set<BuiltinSignatures> VALUES = 
            Collections.unmodifiableSet(EnumSet.allOf(BuiltinSignatures.class));
    private static final Map<String,SignatureFactory>   extensions = 
            new TreeMap<String,SignatureFactory>(String.CASE_INSENSITIVE_ORDER);

    /**
     * Registered a {@link NamedFactory} to be available besides the built-in
     * ones when parsing configuration
     * @param extension The factory to register
     * @throws IllegalArgumentException if factory instance is {@code null},
     * or overrides a built-in one or overrides another registered factory
     * with the same name (case <U>insensitive</U>).
     */
    public static final void registerExtension(SignatureFactory extension) {
        String  name=ValidateUtils.checkNotNull(extension, "No extension provided", GenericUtils.EMPTY_OBJECT_ARRAY).getName();
        ValidateUtils.checkTrue(fromFactoryName(name) == null, "Extension overrides built-in: %s", name);

        synchronized(extensions) {
            ValidateUtils.checkTrue(!extensions.containsKey(name), "Extension overrides existinh: %s", name);
            extensions.put(name, extension);
        }
    }

    /**
     * @return A {@link SortedSet} of the currently registered extensions, sorted
     * according to the factory name (case <U>insensitive</U>)
     */
    public static final SortedSet<SignatureFactory> getRegisteredExtensions() {
        // TODO for JDK-8 return Collections.emptySortedSet()
        synchronized(extensions) {
            return GenericUtils.asSortedSet(NamedResource.BY_NAME_COMPARATOR, extensions.values());
        }
    }

    /**
     * Unregisters specified extension
     * @param name The factory name - ignored if {@code null}/empty
     * @return The registered extension - {@code null} if not found
     */
    public static final SignatureFactory unregisterExtension(String name) {
        if (GenericUtils.isEmpty(name)) {
            return null;
        }
        
        synchronized(extensions) {
            return extensions.remove(name);
        }
    }

    /**
     * @param s The {@link Enum}'s name - ignored if {@code null}/empty
     * @return The matching {@link org.apache.sshd.common.signature.BuiltinSignatures} whose {@link Enum#name()} matches
     * (case <U>insensitive</U>) the provided argument - {@code null} if no match
     */
    public static BuiltinSignatures fromString(String s) {
        if (GenericUtils.isEmpty(s)) {
            return null;
        }

        for (BuiltinSignatures c : VALUES) {
            if (s.equalsIgnoreCase(c.name())) {
                return c;
            }
        }

        return null;
    }

    /**
     * @param factory The {@link org.apache.sshd.common.NamedFactory} for the cipher - ignored if {@code null}
     * @return The matching {@link org.apache.sshd.common.signature.BuiltinSignatures} whose factory name matches
     * (case <U>insensitive</U>) the digest factory name
     * @see #fromFactoryName(String)
     */
    public static BuiltinSignatures fromFactory(NamedFactory<Digest> factory) {
        if (factory == null) {
            return null;
        } else {
            return fromFactoryName(factory.getName());
        }
    }

    /**
     * @param n The factory name - ignored if {@code null}/empty
     * @return The matching {@link org.apache.sshd.common.signature.BuiltinSignatures} whose factory name matches
     * (case <U>insensitive</U>) the provided name - {@code null} if no match
     */
    public static BuiltinSignatures fromFactoryName(String n) {
        if (GenericUtils.isEmpty(n)) {
            return null;
        }

        for (BuiltinSignatures c : VALUES) {
            if (n.equalsIgnoreCase(c.getName())) {
                return c;
            }
        }

        return null;
    }
    
    /**
     * @param sigs A comma-separated list of signatures' names - ignored
     * if {@code null}/empty
     * @return A {@link ParseResult} of all the {@link NamedFactory} whose
     * name appears in the string and represent a built-in signature. Any
     * unknown name is <U>ignored</I>. The order of the returned result
     * is the same as the original order - bar the unknown signatures.
     * <B>Note:</B> it is up to caller to ensure that the list does not
     * contain duplicates
     */
    public static final ParseResult parseSignatureList(String sigs) {
        return parseSignatureList(GenericUtils.split(sigs, ','));
    }

    public static final ParseResult parseSignatureList(String ... sigs) {
        return parseSignatureList(GenericUtils.isEmpty((Object[]) sigs) ? Collections.<String>emptyList() : Arrays.asList(sigs));
    }

    public static final ParseResult parseSignatureList(Collection<String> sigs) {
        if (GenericUtils.isEmpty(sigs)) {
            return ParseResult.EMPTY;
        }
        
        List<SignatureFactory>  factories=new ArrayList<SignatureFactory>(sigs.size());
        List<String>            unknown=Collections.<String>emptyList();
        for (String name : sigs) {
            SignatureFactory s=resolveFactory(name);
            if (s != null) {
                factories.add(s);
            } else {
                // replace the (unmodifiable) empty list with a real one
                if (unknown.isEmpty()) {
                    unknown = new ArrayList<String>();
                }
                unknown.add(name);
            }
        }
        
        return new ParseResult(factories, unknown);
    }

    /**
     * @param name The factory name
     * @return The factory or {@code null} if it is neither a built-in one
     * or a registered extension 
     */
    public static final SignatureFactory resolveFactory(String name) {
        if (GenericUtils.isEmpty(name)) {
            return null;
        }

        SignatureFactory  s=fromFactoryName(name);
        if (s != null) {
            return s;
        }
        
        synchronized(extensions) {
            return extensions.get(name);
        }
    }

    /**
     * Holds the result of the {@link BuiltinSignatures#parseSignatureList(String)}
     * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
     */
    public static final class ParseResult extends NamedFactoriesListParseResult<Signature,SignatureFactory> {
        public static final ParseResult EMPTY=new ParseResult(Collections.<SignatureFactory>emptyList(), Collections.<String>emptyList());
        
        public ParseResult(List<SignatureFactory> parsed, List<String> unsupported) {
            super(parsed, unsupported);
        }
    }
}
