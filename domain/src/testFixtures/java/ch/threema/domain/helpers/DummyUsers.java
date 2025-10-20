/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import ch.threema.base.crypto.NaCl;

import androidx.annotation.NonNull;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.BasicContact;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.libthreema.CryptoException;

public class DummyUsers {
    public static final User ALICE = new User("000ALICE", Utils.hexStringToByteArray("6eda2ebb8527ff5bd0e8719602f710c13e162a3be612de0ad2a2ff66f5050630"));
    public static final User BOB = new User("00000BOB", Utils.hexStringToByteArray("533058227925006d86bb8dd88b0442ed73fbc49216b6e94b0870a7761d979eca"));

    private static final long featureMask = new ThreemaFeature.Builder()
        .audio(true)
        .group(true)
        .ballot(true)
        .file(true)
        .voip(true)
        .videocalls(true)
        .forwardSecurity(true)
        .groupCalls(true)
        .editMessages(true)
        .deleteMessages(true)
        .build();

    @NonNull
    public static IdentityStore getIdentityStoreForUser(@NonNull User user) {
        return new InMemoryIdentityStore(user.identity, null, user.privateKey, user.identity);
    }

    @NonNull
    public static Contact getContactForUser(@NonNull User user) {
        try {
            return new DummyContact(user.identity, NaCl.derivePublicKey(user.privateKey));
        } catch (CryptoException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public static BasicContact getBasicContactForUser(@NonNull User user) {
        try {
            return BasicContact.javaCreate(
                user.identity,
                NaCl.derivePublicKey(user.privateKey),
                featureMask,
                IdentityState.ACTIVE,
                IdentityType.NORMAL
            );
        } catch (CryptoException e) {
            throw new RuntimeException(e);
        }
    }

    public static class User {
        final String identity;
        final byte[] privateKey;

        User(String identity, byte[] privateKey) {
            this.identity = identity;
            this.privateKey = privateKey;
        }

        public String getIdentity() {
            return identity;
        }

        public byte[] getPrivateKey() {
            return privateKey;
        }
    }

    public static class DummyContact extends Contact {
        public DummyContact(String identity, byte[] publicKey) {
            super(identity, publicKey, VerificationLevel.UNVERIFIED);
        }
    }
}
