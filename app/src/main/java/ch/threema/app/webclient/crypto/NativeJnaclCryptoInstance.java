/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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

import com.neilalexander.jnacl.NaCl;

import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.crypto.CryptoInstance;

@AnyThread
public class NativeJnaclCryptoInstance implements CryptoInstance {
	public static String TAG = "CryptoInstance";

    @NonNull private final NaCl nacl;

    NativeJnaclCryptoInstance(@NonNull byte[] ownPrivateKey, @NonNull byte[] otherPublicKey) throws CryptoException {
		try {
            this.nacl = new NaCl(ownPrivateKey, otherPublicKey);
        } catch (Error e) {
            throw new CryptoException("Could not create NaCl instance: " + e.toString(), e);
        }
    }

    @NonNull
    @Override
    public byte[] encrypt(@NonNull byte[] data, @NonNull byte[] nonce) throws CryptoException {
        try {
            return this.nacl.encrypt(data, nonce);
        } catch (Error e) {
            throw new CryptoException("Could not encrypt data: " + e.toString(), e);
        }
    }

    @NonNull
    @Override
    public byte[] decrypt(@NonNull byte[] data, @NonNull byte[] nonce) throws CryptoException {
	    final byte[] decrypted;
        try {
            decrypted = this.nacl.decrypt(data, nonce);
        } catch (Error e) {
            throw new CryptoException("Could not decrypt data: " + e.toString(), e);
        }
        if (decrypted == null) {
            throw new CryptoException("Could not decrypt data (data is null)");
        }
        return decrypted;
    }
}
