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

package org.apache.sshd.common.config;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.sshd.SshBuilder;
import org.apache.sshd.common.AbstractFactoryManager;
import org.apache.sshd.common.Cipher;
import org.apache.sshd.common.Closeable;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.Mac;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.Signature;
import org.apache.sshd.common.Transformer;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.compression.Compression;
import org.apache.sshd.common.compression.CompressionFactory;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.mac.BuiltinMacs;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.util.BaseTestSupport;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SshConfigFileReaderTest extends BaseTestSupport {
    public SshConfigFileReaderTest() {
        super();
    }

    @Test
    public void testReadFromURL() throws IOException {
        URL url=getClass().getResource("sshd_config");
        assertNotNull("Cannot locate test file", url);
        
        Properties  props=SshConfigFileReader.readConfigFile(url);
        assertFalse("No properties read", props.isEmpty());
        assertTrue("Unexpected commented property data", GenericUtils.isEmpty(props.getProperty("ListenAddress")));
        assertTrue("Unexpected non-existing property data", GenericUtils.isEmpty(props.getProperty(getCurrentTestName())));

        String  keysList=props.getProperty("HostKey");
        assertFalse("No host keys", GenericUtils.isEmpty(keysList));

        String[]    keys=GenericUtils.split(keysList, ',');
        assertTrue("No multiple keys", GenericUtils.length((Object[]) keys) > 1);
    }

    @Test
    public void testParseCiphersList() {
        List<? extends NamedResource>   expected=SshBuilder.BaseBuilder.DEFAULT_CIPHERS_PREFERENCE;
        Properties                      props=initNamedResourceProperties(SshConfigFileReader.CIPHERS_CONFIG_PROP, expected);
        BuiltinCiphers.ParseResult      result=SshConfigFileReader.getCiphers(props);
        testParsedFactoriesList(expected, result.getParsedFactories(), result.getUnsupportedFactories());
    }

    @Test
    public void testParseMacsList() {
        List<? extends NamedResource>   expected=SshBuilder.BaseBuilder.DEFAULT_MAC_PREFERENCE;
        Properties                      props=initNamedResourceProperties(SshConfigFileReader.MACS_CONFIG_PROP, expected);
        BuiltinMacs.ParseResult         result=SshConfigFileReader.getMacs(props);
        testParsedFactoriesList(expected, result.getParsedFactories(), result.getUnsupportedFactories());
    }

    @Test
    public void testParseSignaturesList() {
        List<? extends NamedResource>   expected=SshBuilder.BaseBuilder.DEFAULT_SIGNATURE_PREFERENCE;
        Properties                      props=initNamedResourceProperties(SshConfigFileReader.HOST_KEY_ALGORITHMS_CONFIG_PROP, expected);
        BuiltinSignatures.ParseResult   result=SshConfigFileReader.getSignatures(props);
        testParsedFactoriesList(expected, result.getParsedFactories(), result.getUnsupportedFactories());
    }

    @Test
    public void testParseKexFactoriesList() {
        List<? extends NamedResource>   expected=SshBuilder.BaseBuilder.DEFAULT_KEX_PREFERENCE;
        Properties                      props=initNamedResourceProperties(SshConfigFileReader.KEX_ALGORITHMS_CONFIG_PROP, expected);
        BuiltinDHFactories.ParseResult  result=SshConfigFileReader.getKexFactories(props);
        testParsedFactoriesList(expected, result.getParsedFactories(), result.getUnsupportedFactories());
    }

    @Test
    public void testGetCompression() {
        Properties  props=new Properties();
        for (CompressionConfigValue expected : CompressionConfigValue.VALUES) {
            props.setProperty(SshConfigFileReader.COMPRESSION_PROP, expected.name().toLowerCase());
            
            NamedResource   actual=SshConfigFileReader.getCompression(props);
            assertNotNull("No match for " + expected.name(), actual);
            assertEquals(expected.name(), expected.getName(), actual.getName());
        }
    }

    @Test
    public void testConfigureAbstractFactoryManagerWithDefaults() {
        Properties              props=new Properties();   // empty means use defaults
        AbstractFactoryManager  expected=new AbstractFactoryManager() {
                @Override
                protected Closeable getInnerCloseable() {
                    return null;
                }
            };
        // must be lenient since we do not cover the full default spectrum
        AbstractFactoryManager  actual=SshConfigFileReader.configure(expected, props, true, true);
        assertSame("Mismatched configured result", expected, actual);
        validateAbstractFactoryManagerConfiguration(expected, props, true);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNonLenientCiphersConfiguration() {
        FactoryManager  manager=SshConfigFileReader.configureCiphers(
                new AbstractFactoryManager() {
                    @Override
                    protected Closeable getInnerCloseable() {
                        return null;
                    }
                },
                getCurrentTestName(),
                false,
                true);
        fail("Unexpected success: " + NamedResource.Utils.getNames(manager.getCipherFactories()));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNonLenientSignaturesConfiguration() {
        FactoryManager  manager=SshConfigFileReader.configureSignatures(
                new AbstractFactoryManager() {
                    @Override
                    protected Closeable getInnerCloseable() {
                        return null;
                    }
                },
                getCurrentTestName(),
                false,
                true);
        fail("Unexpected success: " + NamedResource.Utils.getNames(manager.getSignatureFactories()));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNonLenientMacsConfiguration() {
        FactoryManager  manager=SshConfigFileReader.configureMacs(
                new AbstractFactoryManager() {
                    @Override
                    protected Closeable getInnerCloseable() {
                        return null;
                    }
                },
                getCurrentTestName(),
                false,
                true);
        fail("Unexpected success: " + NamedResource.Utils.getNames(manager.getMacFactories()));
    }

    @Test
    public void testConfigureCompressionFromStringAcceptsCombinedValues() {
        testConfigureCompressionFromStringAcceptsCombinedValues(CompressionConfigValue.class, Transformer.ENUM_NAME_EXTRACTOR);
        testConfigureCompressionFromStringAcceptsCombinedValues(BuiltinCompressions.class, NamedResource.NAME_EXTRACTOR);
    }

    private static <E extends Enum<E> & CompressionFactory> void testConfigureCompressionFromStringAcceptsCombinedValues(
            Class<E> facs, Transformer<? super E,String> configValueXformer) {
        for (E expected : facs.getEnumConstants()) {
            String          value=configValueXformer.transform(expected);
            String          prefix=facs.getSimpleName() + "[" + expected.name() + "][" + value + "]";
            FactoryManager  manager=SshConfigFileReader.configureCompression(
                    new AbstractFactoryManager() {
                        @Override
                        protected Closeable getInnerCloseable() {
                            return null;
                        }
                    },
                    value,
                    false,
                    true);
            List<NamedFactory<Compression>> compressions=manager.getCompressionFactories();
            assertEquals(prefix + "(size)", 1, GenericUtils.size(compressions));
            assertSame(prefix + "[instance]", expected, compressions.get(0));
        }
    }

    private static <M extends FactoryManager> M validateAbstractFactoryManagerConfiguration(M manager, Properties props, boolean lenient) {
        validateFactoryManagerCiphers(manager, props);
        validateFactoryManagerSignatures(manager, props);
        validateFactoryManagerMacs(manager, props);
        validateFactoryManagerCompressions(manager, props, lenient);
        return manager;
    }

    private static <M extends FactoryManager> M validateFactoryManagerCiphers(M manager, Properties props) {
        return validateFactoryManagerCiphers(manager, props.getProperty(SshConfigFileReader.CIPHERS_CONFIG_PROP, SshConfigFileReader.DEFAULT_CIPHERS));
    }

    private static <M extends FactoryManager> M validateFactoryManagerCiphers(M manager, String value) {
        BuiltinCiphers.ParseResult  result=BuiltinCiphers.parseCiphersList(value);
        validateFactoryManagerFactories(Cipher.class, result.getParsedFactories(), manager.getCipherFactories());
        return manager;
    }

    private static <M extends FactoryManager> M validateFactoryManagerSignatures(M manager, Properties props) {
        return validateFactoryManagerSignatures(manager, props.getProperty(SshConfigFileReader.HOST_KEY_ALGORITHMS_CONFIG_PROP, SshConfigFileReader.DEFAULT_HOST_KEY_ALGORITHMS));
    }

    private static <M extends FactoryManager> M validateFactoryManagerSignatures(M manager, String value) {
        BuiltinSignatures.ParseResult   result=BuiltinSignatures.parseSignatureList(value);
        validateFactoryManagerFactories(Signature.class, result.getParsedFactories(), manager.getSignatureFactories());
        return manager;
    }

    private static <M extends FactoryManager> M validateFactoryManagerMacs(M manager, Properties props) {
        return validateFactoryManagerMacs(manager, props.getProperty(SshConfigFileReader.MACS_CONFIG_PROP, SshConfigFileReader.DEFAULT_MACS));
    }

    private static <M extends FactoryManager> M validateFactoryManagerMacs(M manager, String value) {
        BuiltinMacs.ParseResult   result=BuiltinMacs.parseMacsList(value);
        validateFactoryManagerFactories(Mac.class, result.getParsedFactories(), manager.getMacFactories());
        return manager;
    }

    private static <M extends FactoryManager> M validateFactoryManagerCompressions(M manager, Properties props, boolean lenient) {
        return validateFactoryManagerCompressions(manager, props.getProperty(SshConfigFileReader.COMPRESSION_PROP, SshConfigFileReader.DEFAULT_COMPRESSION), lenient);
    }

    private static <M extends FactoryManager> M validateFactoryManagerCompressions(M manager, String value, boolean lenient) {
        NamedFactory<Compression>   factory=CompressionConfigValue.fromName(value);
        assertTrue("Unknown compression: " + value, lenient || (factory != null));
        if (factory != null) {
            validateFactoryManagerFactories(Compression.class, Collections.singletonList(factory), manager.getCompressionFactories());
        }
        return manager;
    }

    private static <T,F extends NamedFactory<T>> void validateFactoryManagerFactories(Class<T> type, List<? extends F> expected, List<? extends F> actual) {
        validateFactoryManagerSettings(type, expected, actual);
    }

    private static <R extends NamedResource> void validateFactoryManagerSettings(Class<?> type, List<? extends R> expected, List<? extends R> actual) {
        validateFactoryManagerSettings(type.getSimpleName(), expected, actual);
    }

    private static <R extends NamedResource> void validateFactoryManagerSettings(String type, List<? extends R> expected, List<? extends R> actual) {
        assertListEquals(type, expected, actual);
    }

    private static <T extends NamedResource> List<T> testParsedFactoriesList(
            List<? extends NamedResource> expected, List<T> actual, Collection<String> unsupported) {
        assertTrue("Unexpected unsupported factories: " + unsupported, GenericUtils.isEmpty(unsupported));
        assertEquals("Mismatched list size", expected.size(), GenericUtils.size(actual));
        for (int index=0; index < expected.size(); index++) {
            NamedResource   e=expected.get(index), a=actual.get(index);
            String          n1=e.getName(), n2=a.getName();
            assertEquals("Mismatched name at index=" + index, n1, n2);
        }
        
        return actual;
    }
    
    private static <R extends NamedResource> Properties initNamedResourceProperties(String key, Collection<? extends R> values) {
        return initProperties(key, NamedResource.Utils.getNames(values));
    }

    private static Properties initProperties(String key, String value) {
        Properties  props=new Properties();
        props.setProperty(key, value);
        return props;
    }
}
