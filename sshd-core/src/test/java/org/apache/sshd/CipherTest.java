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
package org.apache.sshd;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.apache.sshd.common.Cipher;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Random;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.random.BouncyCastleRandom;
import org.apache.sshd.util.BaseTestSupport;
import org.apache.sshd.util.BogusPasswordAuthenticator;
import org.apache.sshd.util.EchoShellFactory;
import org.apache.sshd.util.JSchLogger;
import org.apache.sshd.util.SimpleUserInfo;
import org.apache.sshd.util.Utils;
import org.junit.After;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.jcraft.jsch.JSch;

/**
 * Test Cipher algorithms.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CipherTest extends BaseTestSupport {

    private SshServer sshd;
    private int port;

    @Test
    public void testAES128CBC() throws Exception {
        if (BuiltinCiphers.aes128cbc.isSupported()
                && checkCipher(com.jcraft.jsch.jce.AES128CBC.class.getName())) {
            setUp(BuiltinCiphers.aes128cbc);
            runTest();
        }
    }

    @Test
    public void testAES192CBC() throws Exception {
        if (BuiltinCiphers.aes192cbc.isSupported()
                && checkCipher(com.jcraft.jsch.jce.AES192CBC.class.getName())) {
            setUp(BuiltinCiphers.aes192cbc);
            runTest();
        }
    }

    @Test
    public void testAES256CBC() throws Exception {
        if (BuiltinCiphers.aes256cbc.isSupported()
                && checkCipher(com.jcraft.jsch.jce.AES256CBC.class.getName())) {
            setUp(BuiltinCiphers.aes256cbc);
            runTest();
        }
    }

    @Test
    public void testBlowfishCBC() throws Exception {
        if (BuiltinCiphers.blowfishcbc.isSupported()
                && checkCipher(com.jcraft.jsch.jce.BlowfishCBC.class.getName())) {
            setUp(BuiltinCiphers.blowfishcbc);
            runTest();
        }
    }

    @Test
    public void testTripleDESCBC() throws Exception {
        if (BuiltinCiphers.tripledescbc.isSupported()
                && checkCipher(com.jcraft.jsch.jce.TripleDESCBC.class.getName())) {
            setUp(BuiltinCiphers.tripledescbc);
            runTest();
        }
    }

    @Test
    public void loadTest() throws Exception {
        Random random = new BouncyCastleRandom();
        loadTest(BuiltinCiphers.aes128cbc, random);
        loadTest(BuiltinCiphers.blowfishcbc, random);
        loadTest(BuiltinCiphers.tripledescbc, random);
    }

    protected void loadTest(NamedFactory<Cipher> factory, Random random) throws Exception {
        Cipher cipher = factory.create();
        byte[] key = new byte[cipher.getBlockSize()];
        byte[] iv = new byte[cipher.getIVSize()];
        random.fill(key, 0, key.length);
        random.fill(iv, 0, iv.length);
        cipher.init(Cipher.Mode.Encrypt, key, iv);

        byte[] input = new byte[cipher.getBlockSize()];
        random.fill(input, 0, input.length);
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            cipher.update(input, 0, input.length);
        }
        long t1 = System.currentTimeMillis();
        System.err.println(factory.getName() + ": " + (t1 - t0) + " ms");
    }


    protected void setUp(NamedFactory<org.apache.sshd.common.Cipher> cipher) throws Exception {
        sshd = SshServer.setUpDefaultServer();
        sshd.setKeyPairProvider(Utils.createTestHostKeyProvider());
        sshd.setCipherFactories(Arrays.<NamedFactory<org.apache.sshd.common.Cipher>>asList(cipher));
        sshd.setShellFactory(new EchoShellFactory());
        sshd.setPasswordAuthenticator(BogusPasswordAuthenticator.INSTANCE);
        sshd.start();
        port = sshd.getPort();
    }

    @After
    public void tearDown() throws Exception {
        if (sshd != null) {
            sshd.stop(true);
        }
    }

    protected void runTest() throws Exception {
        JSchLogger.init();
        JSch sch = new JSch();
        JSch.setConfig("cipher.s2c", "aes128-cbc,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc,none");
        JSch.setConfig("cipher.c2s", "aes128-cbc,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc,none");
        com.jcraft.jsch.Session s = sch.getSession(getCurrentTestName(), "localhost", port);
        s.setUserInfo(new SimpleUserInfo(getCurrentTestName()));
        s.connect();
        com.jcraft.jsch.Channel c = s.openChannel("shell");
        c.connect();
        OutputStream os = c.getOutputStream();
        InputStream is = c.getInputStream();
        for (int i = 0; i < 10; i++) {
            os.write("this is my command\n".getBytes());
            os.flush();
            byte[] data = new byte[512];
            int len = is.read(data);
            String str = new String(data, 0, len);
            assertEquals("this is my command\n", str);
        }
        c.disconnect();
        s.disconnect();
    }

    static boolean checkCipher(String cipher){
        try{
            Class<?> c=Class.forName(cipher);
            com.jcraft.jsch.Cipher _c = (com.jcraft.jsch.Cipher)(c.newInstance());
            _c.init(com.jcraft.jsch.Cipher.ENCRYPT_MODE,
                    new byte[_c.getBlockSize()],
                    new byte[_c.getIVSize()]);
            return true;
        }
        catch(Exception e){
            return false;
        }
    }
}
