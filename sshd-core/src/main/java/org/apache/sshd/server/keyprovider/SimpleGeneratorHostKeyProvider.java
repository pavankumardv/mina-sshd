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
package org.apache.sshd.server.keyprovider;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.KeyPair;

/**
 * TODO Add javadoc
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class SimpleGeneratorHostKeyProvider extends AbstractGeneratorHostKeyProvider {

    public SimpleGeneratorHostKeyProvider() {
        super();
    }

    public SimpleGeneratorHostKeyProvider(String path) {
        super(path);
    }

    public SimpleGeneratorHostKeyProvider(String path, String algorithm) {
        super(path, algorithm);
    }

    public SimpleGeneratorHostKeyProvider(String path, String algorithm, int keySize) {
        super(path, algorithm, keySize);
    }

    @Override
    protected KeyPair doReadKeyPair(InputStream is) throws Exception {
        try(ObjectInputStream r = new ObjectInputStream(is)) {
            return (KeyPair) r.readObject();
        }
    }

    @Override
    protected void doWriteKeyPair(KeyPair kp, OutputStream os) throws Exception {
        try(ObjectOutputStream w = new ObjectOutputStream(os)) {
            w.writeObject(kp);
        }
    }
}
