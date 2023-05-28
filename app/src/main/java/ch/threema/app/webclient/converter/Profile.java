/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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

package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@AnyThread
public class Profile extends Converter {
	private static final String FIELD_IDENTITY = "identity";
	private static final String FIELD_PUBKEY = "publicKey";
	private static final String FIELD_NICKNAME= "publicNickname";
	private static final String FIELD_AVATAR = "avatar";

	/**
	 * Create profile response containing all profile info,
	 * including identity and public key.
	 */
	public static MsgpackObjectBuilder convert(@NonNull String identity,
	                                           @NonNull byte[] publicKey,
	                                           @Nullable String nickname,
	                                           @Nullable byte[] avatar) {
		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(FIELD_IDENTITY, identity);
		builder.put(FIELD_PUBKEY, publicKey);
		builder.put(FIELD_NICKNAME, nickname == null ? "" : nickname);
		builder.maybePut(FIELD_AVATAR, avatar);
		return builder;
	}

	/**
	 * Create profile response containing only nickname and avatar.
	 */
	public static MsgpackObjectBuilder convert(@Nullable String nickname,
	                                           @Nullable byte[] avatar) {
		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(FIELD_NICKNAME, nickname == null ? "" : nickname);
		builder.put(FIELD_AVATAR, avatar);
		return builder;
	}

	/**
	 * Create profile response containing only nickname.
	 */
	public static MsgpackObjectBuilder convert(@Nullable String nickname) {
		final MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
		builder.put(FIELD_NICKNAME, nickname == null ? "" : nickname);
		return builder;
	}
}
