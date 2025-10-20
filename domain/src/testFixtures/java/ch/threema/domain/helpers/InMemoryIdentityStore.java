/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.helpers;

import androidx.annotation.NonNull;
import ch.threema.domain.stores.IdentityStore;

import ch.threema.base.crypto.NaCl;
import ch.threema.libthreema.CryptoException;

/**
 * An in-memory identity store, used for testing.
 */
public class InMemoryIdentityStore implements IdentityStore {
    private String identity;
    private String serverGroup;
    private byte[] publicKey;
    private byte[] privateKey;
    private final String publicNickname;

    public InMemoryIdentityStore(String identity, String serverGroup, byte[] privateKey, String publicNickname) {
        this.identity = identity;
        this.serverGroup = serverGroup;
        try {
            this.publicKey = NaCl.derivePublicKey(privateKey);
        } catch (CryptoException e) {
            throw new RuntimeException(e);
        }
        this.privateKey = privateKey;
        this.publicNickname = publicNickname;
    }

    @Override
    public byte[] encryptData(@NonNull byte[] plaintext, @NonNull byte[] nonce, @NonNull byte[] receiverPublicKey) {
        NaCl nacl = new NaCl(privateKey, receiverPublicKey);
        try {
            return nacl.encrypt(plaintext, nonce);
        } catch (CryptoException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] decryptData(@NonNull byte[] ciphertext, @NonNull byte[] nonce, @NonNull byte[] senderPublicKey) {
        NaCl nacl = new NaCl(privateKey, senderPublicKey);
        try {
            return nacl.decrypt(ciphertext, nonce);
        } catch (CryptoException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] calcSharedSecret(@NonNull byte[] publicKey) {
        NaCl nacl = new NaCl(privateKey, publicKey);
        return nacl.sharedSecret;
    }

    @Override
    public String getIdentity() {
        return identity;
    }

    @Override
    public String getServerGroup() {
        return serverGroup;
    }

    @Override
    public byte[] getPublicKey() {
        return publicKey;
    }

    @Override
    public byte[] getPrivateKey() {
        return privateKey;
    }

    @Override
    @NonNull
    public String getPublicNickname() {
        if (publicNickname == null) {
            return "";
        }
        return publicNickname;
    }

    @Override
    public void setPublicNickname(@NonNull String publicNickname) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void storeIdentity(
        @NonNull String identity,
        @NonNull String serverGroup,
        @NonNull byte[] privateKey
    ) {
        this.identity = identity;
        this.serverGroup = serverGroup;
        try {
            this.publicKey = NaCl.derivePublicKey(privateKey);
        } catch (CryptoException e) {
            throw new RuntimeException(e);
        }
        this.privateKey = privateKey;
    }

    @Override
    public void clear() {
        throw new RuntimeException("not implemented");
    }
}
