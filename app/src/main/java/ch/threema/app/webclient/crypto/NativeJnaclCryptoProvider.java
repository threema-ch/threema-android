/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.webclient.crypto;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.base.utils.LoggingUtil;

import com.neilalexander.jnacl.NaCl;

import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.crypto.CryptoInstance;
import org.saltyrtc.client.crypto.CryptoProvider;
import org.slf4j.Logger;

@AnyThread
public class NativeJnaclCryptoProvider implements CryptoProvider {
    private static final Logger logger = LoggingUtil.getThreemaLogger("NativeJnaclCryptoProvider");

    /**
     * @see CryptoProvider#generateKeypair(byte[], byte[])
     */
    @Override
    public void generateKeypair(@NonNull byte[] publickey, @NonNull byte[] privatekey) {
        logger.debug("generateKeypair");
        NaCl.genkeypair(publickey, privatekey);
    }

    /**
     * @see CryptoProvider#derivePublicKey(byte[])
     */
    @NonNull
    @Override
    public byte[] derivePublicKey(@NonNull byte[] privateKey) throws CryptoException {
        logger.debug("derivePublicKey");
        try {
            return NaCl.derivePublicKey(privateKey);
        } catch (Error e) {
            throw new CryptoException("Deriving public key from private key failed: " + e.toString(), e);
        }
    }

    /**
     * @see CryptoProvider#symmetricEncrypt(byte[], byte[], byte[])
     */
    @NonNull
    @Override
    public byte[] symmetricEncrypt(@NonNull byte[] data, @NonNull byte[] key, @NonNull byte[] nonce) throws CryptoException {
        try {
            return NaCl.symmetricEncryptData(data, key, nonce);
        } catch (Error e) {
            throw new CryptoException("Could not encrypt data: " + e.toString(), e);
        }
    }

    /**
     * @see CryptoProvider#symmetricDecrypt(byte[], byte[], byte[])
     */
    @NonNull
    @Override
    public byte[] symmetricDecrypt(@NonNull byte[] data, @NonNull byte[] key, @NonNull byte[] nonce) throws CryptoException {
        final byte[] decrypted;
        try {
            decrypted = NaCl.symmetricDecryptData(data, key, nonce);
        } catch (Error e) {
            throw new CryptoException("Could not decrypt data: " + e.toString(), e);
        }
        if (decrypted == null) {
            throw new CryptoException("Could not decrypt data (data is null)");
        }
        return decrypted;
    }

    /**
     * @see CryptoProvider#getInstance(byte[], byte[])
     */
    @NonNull
    @Override
    public CryptoInstance getInstance(@NonNull byte[] ownPrivateKey, @NonNull byte[] otherPublicKey) throws CryptoException {
        logger.debug("getInstance");
        return new NativeJnaclCryptoInstance(ownPrivateKey, otherPublicKey);
    }
}
