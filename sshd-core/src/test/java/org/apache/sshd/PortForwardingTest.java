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

import static org.apache.sshd.util.Utils.getFreePort;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.sshd.client.channel.ChannelDirectTcpip;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.FactoryManagerUtils;
import org.apache.sshd.common.SshdSocketAddress;
import org.apache.sshd.util.BaseTestSupport;
import org.apache.sshd.util.BogusForwardingFilter;
import org.apache.sshd.util.BogusPasswordAuthenticator;
import org.apache.sshd.util.EchoShellFactory;
import org.apache.sshd.util.JSchLogger;
import org.apache.sshd.util.SimpleUserInfo;
import org.apache.sshd.util.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Port forwarding tests
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PortForwardingTest extends BaseTestSupport {

    private final org.slf4j.Logger log = LoggerFactory.getLogger(getClass());

    private SshServer sshd;
    private int sshPort;
    private int echoPort;
    private IoAcceptor acceptor;
    private SshClient client;

    @Before
    public void setUp() throws Exception {
        sshd = SshServer.setUpDefaultServer();
        FactoryManagerUtils.updateProperty(sshd, FactoryManager.WINDOW_SIZE, 2048);
        FactoryManagerUtils.updateProperty(sshd, FactoryManager.MAX_PACKET_SIZE, 256);
        sshd.setKeyPairProvider(Utils.createTestHostKeyProvider());
        sshd.setShellFactory(new EchoShellFactory());
        sshd.setPasswordAuthenticator(BogusPasswordAuthenticator.INSTANCE);
        sshd.setTcpipForwardingFilter(new BogusForwardingFilter());
        sshd.start();
        sshPort = sshd.getPort();

        NioSocketAcceptor acceptor = new NioSocketAcceptor();
        acceptor.setHandler(new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object message) throws Exception {
                IoBuffer recv = (IoBuffer) message;
                IoBuffer sent = IoBuffer.allocate(recv.remaining());
                sent.put(recv);
                sent.flip();
                session.write(sent);
            }
        });
        acceptor.setReuseAddress(true);
        acceptor.bind(new InetSocketAddress(0));
        echoPort = acceptor.getLocalAddress().getPort();
        this.acceptor = acceptor;
    }

    @After
    public void tearDown() throws Exception {
        if (sshd != null) {
            sshd.stop(true);
        }
        if (acceptor != null) {
            acceptor.dispose(true);
        }
        if (client != null) {
            client.stop();
        }
    }

    @Test
    public void testRemoteForwarding() throws Exception {
        Session session = createSession();
        try {
            int forwardedPort = getFreePort();
            session.setPortForwardingR(forwardedPort, "localhost", echoPort);
            Thread.sleep(100);
    
            try(Socket s = new Socket("localhost", forwardedPort)) {
                s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                String  expected = getCurrentTestName();
                byte[]  bytes = expected.getBytes();
                s.getOutputStream().write(bytes);
                s.getOutputStream().flush();

                byte[]  buf = new byte[bytes.length + Long.SIZE];
                int     n = s.getInputStream().read(buf);
                String  res = new String(buf, 0, n);
                assertEquals("Mismatched data", expected, res);
            }
    
            session.delPortForwardingR(forwardedPort);
        } finally {
            session.disconnect();
        }
    }

    @Test
    public void testRemoteForwardingNative() throws Exception {
        try(ClientSession session = createNativeSession()) {
            SshdSocketAddress remote = new SshdSocketAddress("", 0);
            SshdSocketAddress local = new SshdSocketAddress("localhost", echoPort);
            SshdSocketAddress bound = session.startRemotePortForwarding(remote, local);
    
            try(Socket s = new Socket(bound.getHostName(), bound.getPort())) {
                s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                String  expected = getCurrentTestName();
                byte[]  bytes = expected.getBytes();
                s.getOutputStream().write(bytes);
                s.getOutputStream().flush();

                byte[]  buf = new byte[bytes.length + Long.SIZE];
                int     n = s.getInputStream().read(buf);
                String  res = new String(buf, 0, n);
                assertEquals("Mismatched data", expected, res);
            }

            session.stopRemotePortForwarding(remote);
            session.close(false).await();
        }
    }

    @Test
    public void testRemoteForwardingNativeBigPayload() throws Exception {
        try(ClientSession session = createNativeSession()) {
            SshdSocketAddress remote = new SshdSocketAddress("", 0);
            SshdSocketAddress local = new SshdSocketAddress("localhost", echoPort);
            SshdSocketAddress bound = session.startRemotePortForwarding(remote, local);

            try(Socket s = new Socket(bound.getHostName(), bound.getPort())) {
                s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                String  expected = getCurrentTestName();
                byte[]  bytes = expected.getBytes();
                byte[]  buf = new byte[bytes.length + Long.SIZE];

                for (int i = 0; i < 1000; i++) {
                    s.getOutputStream().write(bytes);
                    s.getOutputStream().flush();

                    int     n = s.getInputStream().read(buf);
                    String  res = new String(buf, 0, n);
                    assertEquals("Mismatched data at iteration #" + i, expected, res);
                }
            }
    
            session.stopRemotePortForwarding(remote);
            session.close(false).await();
        }
    }

    @Test
    public void testLocalForwarding() throws Exception {
        Session session = createSession();
        try {
            int forwardedPort = getFreePort();
            session.setPortForwardingL(forwardedPort, "localhost", echoPort);
    
            try(Socket s = new Socket("localhost", forwardedPort)) {
                s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                String  expected = getCurrentTestName();
                byte[]  bytes = expected.getBytes();

                s.getOutputStream().write(bytes);
                s.getOutputStream().flush();

                byte[]  buf = new byte[bytes.length + Long.SIZE];
                int     n = s.getInputStream().read(buf);
                String  res = new String(buf, 0, n);
                assertEquals("Mismatched data", expected, res);
            }
    
            session.delPortForwardingL(forwardedPort);
        } finally {
            session.disconnect();
        }
    }

    @Test
    public void testLocalForwardingNative() throws Exception {
        try(ClientSession session = createNativeSession()) {
            SshdSocketAddress local = new SshdSocketAddress("", 0);
            SshdSocketAddress remote = new SshdSocketAddress("localhost", echoPort);
            SshdSocketAddress bound = session.startLocalPortForwarding(local, remote);

            try(Socket s = new Socket(bound.getHostName(), bound.getPort())) {
                s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                String  expected = getCurrentTestName();
                byte[]  bytes = expected.getBytes();

                s.getOutputStream().write(bytes);
                s.getOutputStream().flush();

                byte[]  buf = new byte[bytes.length + Long.SIZE];
                int     n = s.getInputStream().read(buf);
                String  res = new String(buf, 0, n);
                assertEquals("Mismatched data", expected, res);
            }

            session.stopLocalPortForwarding(bound);
            session.close(false).await();
        }
    }

    @Test
    public void testLocalForwardingNativeReuse() throws Exception {
        try(ClientSession session = createNativeSession()) {
            SshdSocketAddress local = new SshdSocketAddress("", 0);
            SshdSocketAddress remote = new SshdSocketAddress("localhost", echoPort);
            SshdSocketAddress bound = session.startLocalPortForwarding(local, remote);

            session.stopLocalPortForwarding(bound);
    
            SshdSocketAddress bound2 = session.startLocalPortForwarding(local, remote);
            session.stopLocalPortForwarding(bound2);
    
            session.close(false).await();
        }
    }

    @Test
    public void testLocalForwardingNativeBigPayload() throws Exception {
        try(ClientSession session = createNativeSession()) {
            SshdSocketAddress local = new SshdSocketAddress("", 0);
            SshdSocketAddress remote = new SshdSocketAddress("localhost", echoPort);
            SshdSocketAddress bound = session.startLocalPortForwarding(local, remote);

            String  expected = getCurrentTestName();
            byte[]  bytes = expected.getBytes();
            byte[]  buf = new byte[bytes.length + Long.SIZE];
            try(Socket s = new Socket(bound.getHostName(), bound.getPort())) {
                s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                for (int i = 0; i < 1000; i++) {
                    s.getOutputStream().write(bytes);
                    s.getOutputStream().flush();

                    int     n = s.getInputStream().read(buf);
                    String  res = new String(buf, 0, n);
                    assertEquals("Mismatched data at iteration #" + i, expected, res);
                }
            }
    
            session.stopLocalPortForwarding(bound);
            session.close(false).await();
        }
    }

    @Test
    public void testForwardingChannel() throws Exception {
        try(ClientSession session = createNativeSession()) {
            SshdSocketAddress local = new SshdSocketAddress("", 0);
            SshdSocketAddress remote = new SshdSocketAddress("localhost", echoPort);

            try(ChannelDirectTcpip channel = session.createDirectTcpipChannel(local, remote)) {
                channel.open().await();

                String  expected = getCurrentTestName();
                byte[]  bytes = expected.getBytes();

                channel.getInvertedIn().write(bytes);
                channel.getInvertedIn().flush();

                byte[]  buf = new byte[bytes.length + Long.SIZE];
                int     n = channel.getInvertedOut().read(buf);
                String  res = new String(buf, 0, n);
                assertEquals("Mismatched data", expected, res);
                channel.close(false);
            }

            session.close(false).await();
        }
    }

    @Test(timeout = 20000)
    public void testRemoteForwardingWithDisconnect() throws Exception {
        Session session = createSession();
        try {
            // 1. Create a Port Forward
            int forwardedPort = getFreePort();
            session.setPortForwardingR(forwardedPort, "localhost", echoPort);
    
            // 2. Establish a connection through it
            try(Socket s = new Socket("localhost", forwardedPort)) {
                s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                // 3. Simulate the client going away
                rudelyDisconnectJschSession(session);
        
                // 4. Make sure the NIOprocessor is not stuck
                {
                    Thread.sleep(1000);
                    // from here, we need to check all the threads running and find a
                    // "NioProcessor-"
                    // that is stuck on a PortForward.dispose
                    ThreadGroup root = Thread.currentThread().getThreadGroup().getParent();
                    while (root.getParent() != null) {
                        root = root.getParent();
                    }
                    boolean stuck;
                    do {
                        stuck = false;
                        for (Thread t : findThreads(root, "NioProcessor-")) {
                            stuck = true;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            // ignored
                        }
                    } while (stuck);
                }
        
                session.delPortForwardingR(forwardedPort);
            }
        } finally {
            session.disconnect();
        }
    }

    /**
     * Close the socket inside this JSCH session. Use reflection to find it and
     * just close it.
     *
     * @param session
     *            the Session to violate
     * @throws Exception
     */
    private void rudelyDisconnectJschSession(Session session) throws Exception {
        Field fSocket = session.getClass().getDeclaredField("socket");
        fSocket.setAccessible(true);
        
        try(Socket socket = (Socket) fSocket.get(session)) {
            assertTrue("socket is not connected", socket.isConnected());
            assertFalse("socket should not be closed", socket.isClosed());
            socket.close();
            assertTrue("socket has not closed", socket.isClosed());
        }
    }

    private Set<Thread> findThreads(ThreadGroup group, String name) {
        HashSet<Thread> ret = new HashSet<Thread>();
        int numThreads = group.activeCount();
        Thread[] threads = new Thread[numThreads * 2];
        numThreads = group.enumerate(threads, false);
        // Enumerate each thread in `group'
        for (int i = 0; i < numThreads; ++i) {
            // Get thread
            // log.debug("Thread name: " + threads[i].getName());
            if (checkThreadForPortForward(threads[i], name)) {
                ret.add(threads[i]);
            }
        }
        // didn't find the thread to check the
        int numGroups = group.activeGroupCount();
        ThreadGroup[] groups = new ThreadGroup[numGroups * 2];
        numGroups = group.enumerate(groups, false);
        for (int i = 0; i < numGroups; ++i) {
            ret.addAll(findThreads(groups[i], name));
        }
        return ret;
    }

    private boolean checkThreadForPortForward(Thread thread, String name) {
        if (thread == null)
            return false;
        // does it contain the name we're looking for?
        if (thread.getName().contains(name)) {
            // look at the stack
            StackTraceElement[] stack = thread.getStackTrace();
            if (stack.length == 0)
                return false;
            else {
                // does it have
                // 'org.apache.sshd.server.session.TcpipForwardSupport.close'?
                for (int i = 0; i < stack.length; ++i) {
                    String clazzName = stack[i].getClassName();
                    String methodName = stack[i].getMethodName();
                    // log.debug("Class: " + clazzName);
                    // log.debug("Method: " + methodName);
                    if (clazzName
                            .equals("org.apache.sshd.server.session.TcpipForwardSupport")
                            && (methodName.equals("close") || methodName
                            .equals("sessionCreated"))) {
                        log.warn(thread.getName() + " stuck at " + clazzName
                                + "." + methodName + ": "
                                + stack[i].getLineNumber());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected Session createSession() throws JSchException {
        JSchLogger.init();
        JSch sch = new JSch();
        Session session = sch.getSession("sshd", "localhost", sshPort);
        session.setUserInfo(new SimpleUserInfo("sshd"));
        session.connect();
        return session;
    }

    protected ClientSession createNativeSession() throws Exception {
        client = SshClient.setUpDefaultClient();
        client.getProperties().put(FactoryManager.WINDOW_SIZE, "2048");
        client.getProperties().put(FactoryManager.MAX_PACKET_SIZE, "256");
        client.setTcpipForwardingFilter(new BogusForwardingFilter());
        client.start();

        ClientSession session = client.connect("sshd", "localhost", sshPort).await().getSession();
        session.addPasswordIdentity("sshd");
        session.auth().verify();
        return session;
    }


}


