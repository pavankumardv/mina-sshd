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

package org.apache.sshd.server.config.keys;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StreamCorruptedException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryDecoder;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.io.NoCloseInputStream;
import org.apache.sshd.common.util.io.NoCloseReader;
import org.apache.sshd.server.PublickeyAuthenticator;

/**
 * Represents an entry in the user's {@code authorized_keys} file according
 * to the <A HREF="http://en.wikibooks.org/wiki/OpenSSH/Client_Configuration_Files#.7E.2F.ssh.2Fauthorized_keys">OpenSSH format</A>.
 * <B>Note:</B> {@code equals/hashCode} check only the key type and data - the
 * comment and/or login options are not considered part of equality
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class AuthorizedKeyEntry extends PublicKeyEntry {
    private static final long serialVersionUID = -9007505285002809156L;

    private String  comment;
    // for options that have no value, "true" is used
    private Map<String,String> loginOptions=Collections.<String,String>emptyMap();

    public AuthorizedKeyEntry() {
        super();
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String value) {
        this.comment = value;
    }

    public Map<String,String> getLoginOptions() {
        return loginOptions;
    }

    public void setLoginOptions(Map<String,String> value) {
        if ((this.loginOptions=value) == null) {
            this.loginOptions = Collections.<String,String>emptyMap();
        }
    }

    @Override
    public String toString() {
        String   entry = super.toString();
        String   kc = getComment();
        Map<?,?> ko=getLoginOptions();
        return (GenericUtils.isEmpty(ko) ? "" : ko.toString() + " ")
                + entry
                + (GenericUtils.isEmpty(kc) ? "" : " " + kc)
                ;
    }
    
    public static final PublickeyAuthenticator fromAuthorizedEntries(Collection<? extends AuthorizedKeyEntry> entries) throws IOException, GeneralSecurityException  {
        Collection<PublicKey> keys = resolveAuthorizedKeys(entries); 
        if (GenericUtils.isEmpty(keys)) {
            return PublickeyAuthenticator.RejectAllPublickeyAuthenticator.INSTANCE;
        } else {
            return new PublickeyAuthenticator.KeySetPublickeyAuthenticator(keys);
        }
    }
    
    public static final List<PublicKey> resolveAuthorizedKeys(Collection<? extends AuthorizedKeyEntry> entries) throws IOException, GeneralSecurityException  {
        if (GenericUtils.isEmpty(entries)) {
            return Collections.emptyList();
        }

        List<PublicKey> keys = new ArrayList<PublicKey>(entries.size());
        for (AuthorizedKeyEntry e : entries) {
            PublicKey k = e.resolvePublicKey();
            keys.add(k);
        }
        
        return keys;
    }

    /**
     * Standard OpenSSH authorized keys file name
     */
    public static final String  STD_AUTHORIZED_KEYS_FILENAME="authorized_keys";
    private static final class LazyDefaultAuthorizedKeysFileHolder {
        private static final File   keysFile=new File(PublicKeyEntry.getDefaultKeysFolder(), STD_AUTHORIZED_KEYS_FILENAME);
    }

    /**
     * @return The default {@link File} location of the OpenSSH authorized keys file
     */
    @SuppressWarnings("synthetic-access")
    public static final File getDefaultAuthorizedKeysFile() {
        return LazyDefaultAuthorizedKeysFileHolder.keysFile;
    }
    /**
     * Reads read the contents of the default OpenSSH <code>authorized_keys</code> file
     * @return A {@link Collection} of all the {@link AuthorizedKeyEntry}-ies found there -
     * or empty if file does not exist
     * @throws IOException If failed to read keys from file
     */
    public static final Collection<AuthorizedKeyEntry> readDefaultAuthorizedKeys() throws IOException {
        File    keysFile=getDefaultAuthorizedKeysFile();
        if (keysFile.exists()) {
            return readAuthorizedKeys(keysFile);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Reads read the contents of an <code>authorized_keys</code> file
     * @param url The {@link URL} to read from
     * @return A {@link Collection} of all the {@link AuthorizedKeyEntry}-ies found there
     * @throws IOException If failed to read or parse the entries
     * @see #readAuthorizedKeys(InputStream, boolean)
     */
    public static final Collection<AuthorizedKeyEntry> readAuthorizedKeys(URL url) throws IOException {
        return readAuthorizedKeys(url.openStream(), true);
    }

    /**
     * Reads read the contents of an <code>authorized_keys</code> file
     * @param file The {@link File} to read from
     * @return A {@link Collection} of all the {@link AuthorizedKeyEntry}-ies found there
     * @throws IOException If failed to read or parse the entries
     * @see #readAuthorizedKeys(InputStream, boolean)
     */
    public static final Collection<AuthorizedKeyEntry> readAuthorizedKeys(File file) throws IOException {
        return readAuthorizedKeys(new FileInputStream(file), true);
    }

    /**
     * Reads read the contents of an <code>authorized_keys</code> file
     * @param path {@link Path} to read from
     * @param options The {@link OpenOption}s to use - if unspecified then appropriate
     * defaults assumed 
     * @return A {@link Collection} of all the {@link AuthorizedKeyEntry}-ies found there
     * @throws IOException If failed to read or parse the entries
     * @see #readAuthorizedKeys(InputStream, boolean)
     * @see Files#newInputStream(Path, OpenOption...)
     */
    public static final Collection<AuthorizedKeyEntry> readAuthorizedKeys(Path path, OpenOption ... options) throws IOException {
        return readAuthorizedKeys(Files.newInputStream(path, options), true);
    }

    /**
     * Reads read the contents of an <code>authorized_keys</code> file
     * @param filePath The file path to read from
     * @return A {@link Collection} of all the {@link AuthorizedKeyEntry}-ies found there
     * @throws IOException If failed to read or parse the entries
     * @see #readAuthorizedKeys(InputStream, boolean)
     */
    public static final Collection<AuthorizedKeyEntry> readAuthorizedKeys(String filePath) throws IOException {
        return readAuthorizedKeys(new FileInputStream(filePath), true);
    }

    /**
     * Reads read the contents of an <code>authorized_keys</code> file
     * @param in The {@link InputStream}
     * @param okToClose <code>true</code> if method may close the input stream
     * regardless of whether successful or failed
     * @return A {@link Collection} of all the {@link AuthorizedKeyEntry}-ies found there
     * @throws IOException If failed to read or parse the entries
     * @see #readAuthorizedKeys(Reader, boolean)
     */
    public static final Collection<AuthorizedKeyEntry> readAuthorizedKeys(InputStream in, boolean okToClose) throws IOException {
        try(Reader  rdr=new InputStreamReader(NoCloseInputStream.resolveInputStream(in, okToClose), StandardCharsets.UTF_8)) {
            return readAuthorizedKeys(rdr, true);
        }
    }

    /**
     * Reads read the contents of an <code>authorized_keys</code> file
     * @param rdr The {@link Reader}
     * @param okToClose <code>true</code> if method may close the input stream
     * regardless of whether successful or failed
     * @return A {@link Collection} of all the {@link AuthorizedKeyEntry}-ies found there
     * @throws IOException If failed to read or parse the entries
     * @see #readAuthorizedKeys(BufferedReader)
     */
    public static final Collection<AuthorizedKeyEntry> readAuthorizedKeys(Reader rdr, boolean okToClose) throws IOException {
        try(BufferedReader  buf=new BufferedReader(NoCloseReader.resolveReader(rdr, okToClose))) {
            return readAuthorizedKeys(buf);
        }
    }

    /**
     * @param rdr The {@link BufferedReader} to use to read the contents of
     * an <code>authorized_keys</code> file
     * @return A {@link Collection} of all the {@link AuthorizedKeyEntry}-ies found there
     * @throws IOException If failed to read or parse the entries
     * @see #parseAuthorizedKeyEntry(String)
     */
    public static final Collection<AuthorizedKeyEntry> readAuthorizedKeys(BufferedReader rdr) throws IOException {
        Collection<AuthorizedKeyEntry>  entries=null;

        for (String line=rdr.readLine(); line != null; line=rdr.readLine()) {
            final AuthorizedKeyEntry  entry;
            try {
                if ((entry=parseAuthorizedKeyEntry(line.trim())) == null) {
                    continue;
                }
            } catch(IllegalArgumentException e) {
                throw new StreamCorruptedException(e.getMessage());
            }

            if (entries == null) {
                entries = new LinkedList<AuthorizedKeyEntry>();
            }

            entries.add(entry);
        }

        if (entries == null) {
            return Collections.emptyList();
        } else {
            return entries;
        }
    }

    /**
     * @param line Original line from an <code>authorized_keys</code> file
     * @return {@link AuthorizedKeyEntry} or <code>null</code> if the line is
     * <code>null</code>/empty or a comment line
     * @throws IllegalArgumentException If failed to parse/decode the line
     * @see #COMMENT_CHAR
     */
    public static final AuthorizedKeyEntry parseAuthorizedKeyEntry(String line) throws IllegalArgumentException {
        if (GenericUtils.isEmpty(line) || (line.charAt(0) == COMMENT_CHAR) /* comment ? */) {
            return null;
        }

        int startPos=line.indexOf(' ');
        if (startPos <= 0) {
            throw new IllegalArgumentException("Bad format (no key data delimiter): " + line);
        }

        int endPos=line.indexOf(' ', startPos + 1);
        if (endPos <= startPos) {
            endPos = line.length();
        }

        String keyType = line.substring(0, startPos);
        PublicKeyEntryDecoder<? extends PublicKey> decoder = KeyUtils.getPublicKeyEntryDecoder(keyType);
        final AuthorizedKeyEntry    entry;
        if (decoder == null) {  // assume this is due to the fact that it starts with login options
            if ((entry=parseAuthorizedKeyEntry(line.substring(startPos + 1).trim())) == null) {
                throw new IllegalArgumentException("Bad format (no key data after login options): " + line);
            }

            entry.setLoginOptions(parseLoginOptions(keyType));
        } else {
            String encData = (endPos < (line.length() - 1)) ? line.substring(0, endPos).trim() : line;
            String comment = (endPos < (line.length() - 1)) ? line.substring(endPos + 1).trim() : null;
            entry = parsePublicKeyEntry(new AuthorizedKeyEntry(), encData);
            entry.setComment(comment);
        }

        return entry;
    }
    
    public static final Map<String,String> parseLoginOptions(String options) {
        // TODO add support if quoted values contain ','
        String[]    pairs=GenericUtils.split(options, ',');
        if (GenericUtils.isEmpty(pairs)) {
            return Collections.emptyMap();
        }
        
        Map<String,String>  optsMap=new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (String p : pairs) {
            p = GenericUtils.trimToEmpty(p);
            if (GenericUtils.isEmpty(p)) {
                continue;
            }
            
            int             pos=p.indexOf('=');
            String          name=(pos < 0) ? p : GenericUtils.trimToEmpty(p.substring(0, pos));
            CharSequence    value=(pos < 0) ? null : GenericUtils.trimToEmpty(p.substring(pos + 1));
            value = GenericUtils.stripQuotes(value);
            if (value == null) {
                value = Boolean.TRUE.toString();
            }
            
            String  prev=optsMap.put(name, value.toString());
            if (prev != null) {
                throw new IllegalArgumentException("Multiple values for key=" + name + ": old=" + prev + ", new=" + value);
            }
        }
        
        return optsMap;
    }
}
