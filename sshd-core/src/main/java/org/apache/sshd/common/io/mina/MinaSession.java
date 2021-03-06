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
package org.apache.sshd.common.io.mina;

import java.net.SocketAddress;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.sshd.common.Closeable;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.future.DefaultCloseFuture;
import org.apache.sshd.common.future.DefaultSshFuture;
import org.apache.sshd.common.io.IoService;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.util.CloseableUtils;
import org.apache.sshd.common.util.buffer.Buffer;

/**
 */
public class MinaSession extends CloseableUtils.AbstractInnerCloseable implements IoSession {

    private final MinaService service;
    private final org.apache.mina.core.session.IoSession session;

    public MinaSession(MinaService service, org.apache.mina.core.session.IoSession session) {
        this.service = service;
        this.session = session;
    }

    public org.apache.mina.core.session.IoSession getSession() {
        return session;
    }

    public void suspend() {
        session.suspendRead();
        session.suspendWrite();
    }

    @Override
    public Object getAttribute(Object key) {
        return session.getAttribute(key);
    }

    @Override
    public Object setAttribute(Object key, Object value) {
        return session.setAttribute(key, value);
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return session.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return session.getLocalAddress();
    }

    @Override
    public long getId() {
        return session.getId();
    }

    public WriteFuture write(byte[] data, int offset, int len) {
        IoBuffer buffer = IoBuffer.wrap(data, offset, len);
        return session.write(buffer);
    }

    @Override
    protected Closeable getInnerCloseable() {
        return new CloseableUtils.IoBaseCloseable() {
            @SuppressWarnings("synthetic-access")
            @Override
            public boolean isClosing() {
                return session.isClosing();
            }
            @SuppressWarnings("synthetic-access")
            @Override
            public boolean isClosed() {
                return !session.isConnected();
            }
            @SuppressWarnings("synthetic-access")
            @Override
            public org.apache.sshd.common.future.CloseFuture close(boolean immediately) {
                final DefaultCloseFuture future = new DefaultCloseFuture(lock);
                session.close(false).addListener(new IoFutureListener<IoFuture>() {
                    @Override
                    public void operationComplete(IoFuture f) {
                        future.setValue(Boolean.TRUE);
                    }
                });
                return future;
            }
        };
    }

    @Override
    public IoWriteFuture write(Buffer buffer) {
        class Future extends DefaultSshFuture<IoWriteFuture> implements IoWriteFuture {
            Future(Object lock) {
                super(lock);
            }

            @Override
            public void verify() throws SshException {
                try {
                    await();
                }
                catch (InterruptedException e) {
                    throw new SshException("Interrupted", e);
                }
                if (!isWritten()) {
                    throw new SshException("Write failed", getException());
                }
            }

            @Override
            public boolean isWritten() {
                return getValue() instanceof Boolean;
            }

            public void setWritten() {
                setValue(Boolean.TRUE);
            }

            @Override
            public Throwable getException() {
                Object v = getValue();
                return v instanceof Throwable ? (Throwable) v : null;
            }

            public void setException(Throwable exception) {
                if (exception == null) {
                    throw new IllegalArgumentException("exception");
                }
                setValue(exception);
            }
        }
        final Future future = new Future(null);
        session.write(MinaSupport.asIoBuffer(buffer)).addListener(new IoFutureListener<WriteFuture>() {
            @Override
            public void operationComplete(WriteFuture cf) {
                if (cf.getException() != null) {
                    future.setException(cf.getException());
                } else {
                    future.setWritten();
                }
            }
        });
        return future;
    }

    @Override
    public IoService getService() {
        return service;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[local=" + session.getLocalAddress() + ", remote=" + session.getRemoteAddress() + "]";
    }
}
