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
package org.apache.sshd.common.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.FactoryManagerUtils;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;

/**
 * TODO Add javadoc
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class ChannelPipedInputStream extends InputStream implements ChannelPipedSink {
    public static final long DEFAULT_TIMEOUT = 0;    // infinite

    private final Window localWindow;
    private final Buffer buffer = new ByteArrayBuffer();
    private final byte[] b = new byte[1];
    private boolean closed;
    private boolean eofSent;

    private final Lock lock = new ReentrantLock();
    private final Condition dataAvailable = lock.newCondition();

    private long timeout;

    /**
     * {@link ChannelPipedOutputStream} is already closed and so we will not receive additional data.
     * This is different from the {@link #closed}, which indicates that the reader of this {@link InputStream}
     * will not be reading data any more.
     */
    private boolean writerClosed;

    public ChannelPipedInputStream(Window localWindow) {
        this.localWindow = ValidateUtils.checkNotNull(localWindow, "No local window provided", GenericUtils.EMPTY_OBJECT_ARRAY);
        this.timeout = FactoryManagerUtils.getLongProperty(localWindow.getProperties(), FactoryManager.WINDOW_TIMEOUT, DEFAULT_TIMEOUT);
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public int available() throws IOException {
        lock.lock();
        try {
            int avail = buffer.available();
            if (avail == 0 && writerClosed) {
                return -1;
            }
            return avail;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read() throws IOException {
        synchronized (b) {
            int l = read(b, 0, 1);
            if (l == -1) {
                return -1;
            }
            return (b[0] & 0xff);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        long startTime = System.currentTimeMillis();
        lock.lock();
        try {
            for (int index=0;; index++) {
                if ((closed && writerClosed && eofSent) || (closed && !writerClosed)) {
                    throw new IOException("Pipe closed after " + index + " cycles");
                }
                if (buffer.available() > 0) {
                    break;
                }
                if (writerClosed) {
                    eofSent = true;
                    return -1; // no more data to read
                }

                try {
                    if (timeout > 0) {
                        long remaining = timeout - (System.currentTimeMillis() - startTime);
                        if (remaining <= 0) {
                            throw new SocketException("Timeout (" + timeout + ") exceeded after " + index + " cycles");
                        }
                        dataAvailable.await(remaining, TimeUnit.MILLISECONDS);
                    } else {
                        dataAvailable.await();
                    }
                } catch (InterruptedException e) {
                    throw (IOException) new InterruptedIOException("Interrupted at cycle #" + index + " while waiting for data to become available").initCause(e);
                }
            }
            if (len > buffer.available()) {
                len = buffer.available();
            }
            buffer.getRawBytes(b, off, len);
            if ((buffer.rpos() > localWindow.getPacketSize()) || (buffer.available() == 0)) {
                buffer.compact();
            }
        } finally {
            lock.unlock();
        }
        localWindow.consumeAndCheck(len);
        return len;
    }

    @Override
    public void eof() {
        lock.lock();
        try {
            writerClosed = true;
            dataAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            dataAvailable.signalAll();
        } finally {
            closed = true;
            lock.unlock();
        }
    }

    @Override
    public void receive(byte[] bytes, int off, int len) throws IOException {
        lock.lock();
        try {
            if (writerClosed || closed) {
                throw new IOException("Pipe closed");
            }
            buffer.putRawBytes(bytes, off, len);
            dataAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
