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

package org.apache.sshd.common.config.keys;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;

import org.apache.sshd.common.util.Base64;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.buffer.BufferUtils;

/**
 * <P>Represents a {@link PublicKey} whose data is formatted according to
 * the <A HREF="http://en.wikibooks.org/wiki/OpenSSH">OpenSSH</A> format:</P></BR>
 * <CODE><PRE>
 *      <key-type> <base64-encoded-public-key-data>
 * </CODE></PRE>
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class PublicKeyEntry implements Serializable {
    private static final long serialVersionUID = -585506072687602760L;

    private String  keyType;
    private byte[]  keyData;

    public PublicKeyEntry() {
        super();
    }

    public PublicKeyEntry(String keyType, byte ... keyData) {
        this.keyType = keyType;
        this.keyData = keyData;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String value) {
        this.keyType = value;
    }

    public byte[] getKeyData() {
        return keyData;
    }

    public void setKeyData(byte[] value) {
        this.keyData = value;
    }

    /**
     * @return The resolved {@link PublicKey} - never {@code null}.
     * <B>Note:</B> may be called only after key type and data bytes have
     * been set or exception(s) may be thrown
     * @throws IOException If failed to decode the key
     * @throws GeneralSecurityException If failed to generate the key
     */
    public PublicKey resolvePublicKey() throws IOException, GeneralSecurityException {
        String kt = getKeyType();
        PublicKeyEntryDecoder<? extends PublicKey> decoder = KeyUtils.getPublicKeyEntryDecoder(kt);
        if (decoder == null) {
            throw new InvalidKeySpecException("No decoder registered for key type=" + kt);
        }
        
        byte[] data = getKeyData();
        PublicKey key = decoder.decodePublicKey(data);
        if (key == null) {
            throw new InvalidKeyException("No key of type=" + kt + " decoded for data=" + BufferUtils.printHex(':', data));
        }
        
        return key;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getKeyType())
             + Arrays.hashCode(getKeyData())
             ;
    }

    /*
     * In case some derived class wants to define some "extended" equality
     * without having to repeat this code
     */
    protected boolean isEquivalent(PublicKeyEntry e) {
        if (this == e) {
            return true;
        }
        
        if (Objects.equals(getKeyType(), e.getKeyType())
         && Arrays.equals(getKeyData(), e.getKeyData())) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (this == obj)
            return true;
        if (getClass() != obj.getClass())
            return false;

        if (isEquivalent((PublicKeyEntry) obj)) {
            return true;
        } else {
            return false;
        }
    }
    
    @Override
    public String toString() {
        byte[] data = getKeyData();
        return getKeyType() + " " + (GenericUtils.isEmpty(data) ? "<no-key>" : Base64.encodeToString(data));
    }
    
    /**
     * @param data Assumed to contain at least {@code key-type base64-data} (anything
     * beyond the BASE64 data is ignored) - ignored if {@code null}/empty
     * @return A {@link PublicKeyEntry} or {@code null} if no data
     * @throws IllegalArgumentException if bad format found
     * @see #parsePublicKeyEntry(PublicKeyEntry, String)
     */
    public static final PublicKeyEntry parsePublicKeyEntry(String data) throws IllegalArgumentException {
        if (GenericUtils.isEmpty(data)) {
            return null;
        } else {
            return parsePublicKeyEntry(new PublicKeyEntry(), data);
        }
    }

    /**
     * @param entry The {@link PublicKeyEntry} whose contents are to be
     * updated - ignored if {@code null}
     * @param data Assumed to contain at least {@code key-type base64-data} (anything
     * beyond the BASE64 data is ignored) - ignored if {@code null}/empty
     * @return The updated entry instance
     * @throws IllegalArgumentException if bad format found
     */
    public static final <E extends PublicKeyEntry> E parsePublicKeyEntry(E entry, String data) throws IllegalArgumentException {
        if (GenericUtils.isEmpty(data) || (entry == null)) {
            return entry;
        }
        
        int startPos = data.indexOf(' ');
        if (startPos <= 0) {
            throw new IllegalArgumentException("Bad format (no key data delimiter): " + data);
        }

        int endPos = data.indexOf(' ', startPos + 1);
        if (endPos <= startPos) {   // OK if no continuation beyond the BASE64 encoded data
            endPos = data.length();
        }

        String  keyType = data.substring(0, startPos);
        String  b64Data = data.substring(startPos + 1, endPos).trim();
        byte[]  keyData = Base64.decodeString(b64Data);
        if (GenericUtils.isEmpty(keyData)) {
            throw new IllegalArgumentException("Bad format (no BASE64 key data): " + data);
        }
        
        entry.setKeyType(keyType);
        entry.setKeyData(keyData);
        return entry;
    }

    /**
     * Character used to denote a comment line in the keys file
     */
    public static final char COMMENT_CHAR='#';


    /**
     * Standard folder name used by OpenSSH to hold key files
     */
    public static final String STD_KEYFILE_FOLDER_NAME=".ssh";

    private static final class LazyDefaultKeysFolderHolder {
        private static final File   folder=
                new File(System.getProperty("user.home") + File.separator + STD_KEYFILE_FOLDER_NAME);
    }

    /**
     * @return The default OpenSSH folder used to hold key files - e.g.,
     * {@code known_hosts}, {@code authorized_keys}, etc.
     */
    @SuppressWarnings("synthetic-access")
    public static final File getDefaultKeysFolder() {
        return LazyDefaultKeysFolderHolder.folder;
    }
}
