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

package ch.threema.domain.fs;

import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.libthreema.CryptoException;
import ch.threema.libthreema.LibthreemaKt;

/**
 * Key-derivation function ratchet for generating ephemeral message encryption keys
 */
public class KDFRatchet {

    private final static Logger logger = LoggingUtil.getThreemaLogger("KDFRatchet");

    /**
     * Upper limit on how many times we are willing to turn the ratchet to catch up with a peer.
     */
    private static final long MAX_COUNTER_INCREMENT = 25000;

    private static final String KDF_SALT_CK = "kdf-ck";
    private static final String KDF_SALT_AEK = "kdf-aek";

    private @NonNull byte[] currentChainKey;

    private long counter;

    public KDFRatchet(long counter, @NonNull byte[] initialChainKey) {
        this.currentChainKey = initialChainKey;
        this.counter = counter;
    }

    /**
     * Turn the ratchet once.
     */
    public void turn() {
        try {
            this.currentChainKey = LibthreemaKt.blake2bMac256(
                currentChainKey,
                DHSession.KDF_PERSONAL.getBytes(StandardCharsets.UTF_8),
                KDF_SALT_CK.getBytes(StandardCharsets.UTF_8),
                new byte[0]
            );
            this.counter++;
        } catch (CryptoException cryptoException) {
            logger.error("Failed to compute blake2b hash", cryptoException);
            throw new Error("Failed to compute blake2b hash", cryptoException);
        }
    }

    /**
     * Turn the ratchet until the desired counter value has been reached.
     *
     * @param targetCounterValue Target counter value that should be reached
     * @return the number of turns required
     */
    public int turnUntil(long targetCounterValue) throws RatchetRotationException {
        if (counter == targetCounterValue) {
            return 0;
        } else if (counter > targetCounterValue) {
            throw new RatchetRotationException("Target counter value is lower than current counter value");
        } else if ((targetCounterValue - counter) > MAX_COUNTER_INCREMENT) {
            throw new RatchetRotationException("Target counter value is too far ahead");
        }

        int numTurns = 0;
        while (this.counter < targetCounterValue) {
            this.turn();
            numTurns++;
        }
        return numTurns;
    }

    public long getCounter() {
        return counter;
    }

    public byte[] getCurrentChainKey() {
        return this.currentChainKey;
    }

    /**
     * The encryption key is derived from the chain key, but separate, so that
     * a leaked encryption key cannot be used to calculate any chain keys
     */
    public byte[] getCurrentEncryptionKey() {
        try {
            return LibthreemaKt.blake2bMac256(
                currentChainKey,
                DHSession.KDF_PERSONAL.getBytes(StandardCharsets.UTF_8),
                KDF_SALT_AEK.getBytes(StandardCharsets.UTF_8),
                new byte[0]
            );
        } catch (CryptoException cryptoException) {
            logger.error("Failed to compute blake2b hash", cryptoException);
            throw new Error("Failed to compute blake2b hash", cryptoException);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KDFRatchet that = (KDFRatchet) o;
        return getCounter() == that.getCounter() && MessageDigest.isEqual(getCurrentChainKey(), that.getCurrentChainKey());
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(getCounter());
        result = 31 * result + Arrays.hashCode(getCurrentChainKey());
        return result;
    }

    public static class RatchetRotationException extends ThreemaException {
        public RatchetRotationException(String msg) {
            super(msg);
        }
    }
}
