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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * Port forwarding tests
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PortForwardingLoadTest extends BaseTestSupport {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private SshServer sshd;
    private int sshPort;
    private int echoPort;
    private IoAcceptor acceptor;

    public PortForwardingLoadTest() {
        super();
    }

    @Before
    public void setUp() throws Exception {
        sshd = SshServer.setUpDefaultServer();
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
    }

    @Test
    public void testLocalForwardingPayload() throws Exception {
        final int NUM_ITERATIONS = 100;
        final String PAYLOAD_TMP = "This is significantly longer Test Data. This is significantly "+
                "longer Test Data. This is significantly longer Test Data. This is significantly "+
                "longer Test Data. This is significantly longer Test Data. This is significantly "+
                "longer Test Data. This is significantly longer Test Data. This is significantly "+
                "longer Test Data. This is significantly longer Test Data. This is significantly "+
                "longer Test Data. ";
        StringBuilder sb = new StringBuilder(PAYLOAD_TMP.length() * 1000);
        for (int i = 0; i < 1000; i++) {
            sb.append(PAYLOAD_TMP);
        }
        final String PAYLOAD = sb.toString();

        Session session = createSession();        
        try(final ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress((InetAddress) null, 0));
            int forwardedPort = ss.getLocalPort();
            int sinkPort = session.setPortForwardingL(0, "localhost", forwardedPort);
            final AtomicInteger conCount = new AtomicInteger(0);
    
            Thread tAcceptor = new Thread(getCurrentTestName() + "Acceptor") {
                    @SuppressWarnings("synthetic-access")
                    @Override
                    public void run() {
                        try {
                            byte[] buf = new byte[8192];
                            log.info("Started...");
                            for (int i = 0; i < NUM_ITERATIONS; ++i) {
                                try(Socket s = ss.accept()) {
                                    conCount.incrementAndGet();
                                    
                                    try(InputStream sockIn = s.getInputStream();
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                    
                                        int l;
                                        while ((baos.size() < PAYLOAD.length()) && ((l = sockIn.read(buf)) > 0)) {
                                            baos.write(buf, 0, l);
                                        }
                                    
                                        assertEquals("Mismatched received data at iteration #" + i, PAYLOAD, baos.toString());
        
                                        try(InputStream inputCopy = new ByteArrayInputStream(baos.toByteArray());
                                            OutputStream sockOut = s.getOutputStream()) {
                                            
                                            while ((l = sockIn.read(buf)) > 0) {
                                                sockOut.write(buf, 0, l);
                                            }
                                        }
                                    }
                                }
                            }
                            log.info("Done");
                        } catch (Exception e) {
                            log.error("Failed to complete run loop", e);
                        }
                    }
                };
            tAcceptor.start();
            Thread.sleep(50);
    
            byte[]  buf = new byte[8192];
            byte[]  bytes = PAYLOAD.getBytes();
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                log.info("Iteration {}", Integer.valueOf(i));
                try(Socket s = new Socket("localhost", sinkPort);
                    OutputStream sockOut = s.getOutputStream()) {

                    s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                    sockOut.write(bytes);
                    sockOut.flush();
    
                    try(InputStream sockIn = s.getInputStream();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length)) {
                        int l;
                        while ((baos.size() < PAYLOAD.length()) && ((l = sockIn.read(buf)) > 0)) {
                            baos.write(buf, 0, l);
                        }
                        assertEquals("Mismatched payload at iteration #" + i, PAYLOAD, baos.toString());
                    }
                } catch (Exception e) {
                    log.error("Error in iteration #" + i, e);
                }
            }
            session.delPortForwardingL(sinkPort);
            
            ss.close();
            tAcceptor.join(TimeUnit.SECONDS.toMillis(5L));
        } finally {
            session.disconnect();
        }
    }

    @Test
    public void testRemoteForwardingPayload() throws Exception {
        final int NUM_ITERATIONS = 100;
        final String PAYLOAD = "This is significantly longer Test Data. This is significantly "+
                "longer Test Data. This is significantly longer Test Data. This is significantly "+
                "longer Test Data. This is significantly longer Test Data. This is significantly "+
                "longer Test Data. This is significantly longer Test Data. This is significantly "+
                "longer Test Data. This is significantly longer Test Data. This is significantly "+
                "longer Test Data. ";
        Session session = createSession();
        try (final ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress((InetAddress) null, 0));
            int forwardedPort = ss.getLocalPort();
            int sinkPort = getFreePort();
            session.setPortForwardingR(sinkPort, "localhost", forwardedPort);
            final boolean started[] = new boolean[1];
            started[0] = false;
            final AtomicInteger conCount = new AtomicInteger(0);
    
            Thread tWriter = new Thread(getCurrentTestName() + "Writer") {
                    @SuppressWarnings("synthetic-access")
                    @Override
                    public void run() {
                        started[0] = true;
                        try {
                            byte[]  bytes=PAYLOAD.getBytes();
                            for (int i = 0; i < NUM_ITERATIONS; ++i) {
                                try(Socket s = ss.accept()) {
                                    conCount.incrementAndGet();
                                    
                                    try(OutputStream sockOut=s.getOutputStream()) {
                                        sockOut.write(bytes);
                                        sockOut.flush();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("Failed to complete run loop", e);
                        }
                    }
                };
            tWriter.start();
            Thread.sleep(50);
            assertTrue("Server not started", started[0]);
    
            final boolean lenOK[] = new boolean[NUM_ITERATIONS];
            final boolean dataOK[] = new boolean[NUM_ITERATIONS];
            byte b2[] = new byte[PAYLOAD.length()];
            byte b1[] = new byte[b2.length / 2];

            for (int i = 0; i < NUM_ITERATIONS; i++) {
                final int ii = i;
                try(Socket s = new Socket("localhost", sinkPort);
                    InputStream sockIn = s.getInputStream()) {

                    s.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10L));

                    int read1 = sockIn.read(b1);
                    String part1 = new String(b1, 0, read1);
                    Thread.sleep(50);

                    int read2 = sockIn.read(b2);
                    String part2 = new String(b2, 0, read2);
                    int totalRead = read1 + read2;
                    lenOK[ii] = PAYLOAD.length() == totalRead;

                    String readData = part1 + part2;
                    dataOK[ii] = PAYLOAD.equals(readData);
                    if (!lenOK[ii]) {
                        throw new IndexOutOfBoundsException("Mismatched length: expected=" + PAYLOAD.length() + ", actual=" + totalRead);
                    }
                    
                    if (!dataOK[ii]) {
                        throw new IllegalStateException("Mismatched content");
                    }
                } catch (Exception e) {
                    log.error("Failed to complete iteration #" + i, e);
                }
            }
            int ok = 0;
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                ok += lenOK[i] ? 1 : 0;
            }
            Thread.sleep(50);
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                assertTrue("Bad length at iteration " + i, lenOK[i]);
                assertTrue("Bad data at iteration " + i, dataOK[i]);
            }
            session.delPortForwardingR(forwardedPort);
            ss.close();
            tWriter.join(TimeUnit.SECONDS.toMillis(5L));
        } finally {
            session.disconnect();
        }
    }

    @Test
    public void testForwardingOnLoad() throws Exception {
//        final String path = "/history/recent/troubles/";
//        final String host = "www.bbc.co.uk";
//        final String path = "";
//        final String host = "www.bahn.de";
        final String path = "";
        final String host = "localhost";
        final int nbThread = 2;
        final int nbDownloads = 2;
        final int nbLoops = 2;

        StringBuilder resp = new StringBuilder();
        resp.append("<html><body>\n");
        for (int i = 0; i < 1000; i++) {
            resp.append("0123456789\n");
        }
        resp.append("</body></html>\n");
        final StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK").append('\n');
        sb.append("Content-Type: text/HTML").append('\n');
        sb.append("Content-Length: ").append(resp.length()).append('\n');
        sb.append('\n');
        sb.append(resp);
        NioSocketAcceptor acceptor = new NioSocketAcceptor();
        acceptor.setHandler(new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object message) throws Exception {
                session.write(IoBuffer.wrap(sb.toString().getBytes()));
            }
        });
        acceptor.setReuseAddress(true);
        acceptor.bind(new InetSocketAddress(0));
        final int port = acceptor.getLocalAddress().getPort();

        Session session = createSession();
        try {
            final int forwardedPort1 = session.setPortForwardingL(0, host, port);
            final int forwardedPort2 = getFreePort();
            session.setPortForwardingR(forwardedPort2, "localhost", forwardedPort1);
            System.err.println("URL: http://localhost:" + forwardedPort2);
    
            final CountDownLatch latch = new CountDownLatch(nbThread * nbDownloads * nbLoops);
            final Thread[] threads = new Thread[nbThread];
            final List<Throwable> errors = new CopyOnWriteArrayList<Throwable>();
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(getCurrentTestName() + "[" + i + "]") {
                    @Override
                    public void run() {
                        for (int j = 0; j < nbLoops; j++)  {
                            final MultiThreadedHttpConnectionManager mgr = new MultiThreadedHttpConnectionManager();
                            final HttpClient client = new HttpClient(mgr);
                            client.getHttpConnectionManager().getParams().setDefaultMaxConnectionsPerHost(100);
                            client.getHttpConnectionManager().getParams().setMaxTotalConnections(1000);
                            for (int i = 0; i < nbDownloads; i++) {
                                try {
                                    checkHtmlPage(client, new URL("http://localhost:" + forwardedPort2 + path));
                                } catch (Throwable e) {
                                    errors.add(e);
                                } finally {
                                    latch.countDown();
                                    System.err.println("Remaining: " + latch.getCount());
                                }
                            }
                            mgr.shutdown();
                        }
                    }
                };
            }
            for (int i = 0; i < threads.length; i++) {
                threads[i].start();
            }
            latch.await();
            for (Throwable t : errors) {
                t.printStackTrace();
            }
            assertEquals(0, errors.size());
        } finally {
            session.disconnect();
        }
    }

    protected Session createSession() throws JSchException {
        JSchLogger.init();
        JSch sch = new JSch();
        Session session = sch.getSession("sshd", "localhost", sshPort);
        session.setUserInfo(new SimpleUserInfo("sshd"));
        session.connect();
        return session;
    }

    protected void checkHtmlPage(HttpClient client, URL url) throws IOException {
        client.setHostConfiguration(new HostConfiguration());
        client.getHostConfiguration().setHost(url.getHost(), url.getPort());
        GetMethod get = new GetMethod("");
        get.getParams().setVersion(HttpVersion.HTTP_1_1);
        client.executeMethod(get);
        String str = get.getResponseBodyAsString();
        if (str.indexOf("</html>") <= 0) {
            System.err.println(str);
        }
        assertTrue((str.indexOf("</html>") > 0));
        get.releaseConnection();
//        url.openConnection().setDefaultUseCaches(false);
//        Reader reader = new BufferedReader(new InputStreamReader(url.openStream()));
//        try {
//            StringWriter sw = new StringWriter();
//            char[] buf = new char[8192];
//            while (true) {
//                int len = reader.read(buf);
//                if (len < 0) {
//                    break;
//                }
//                sw.write(buf, 0, len);
//            }
//            assertTrue(sw.toString().indexOf("</html>") > 0);
//        } finally {
//            reader.close();
//        }
    }


}


