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

package ch.threema.domain.protocol.csp.messages.protobuf;

import androidx.annotation.NonNull;

import com.google.protobuf.MessageLite;

/**
 * Interface for protobuf payload data classes.
 * <p>
 * When implementing this interface, classes must have static {@code parse}-methods returning a new (Non-Null) object
 * of itself (type T) according to these signatures:
 * <ul>
 *   <li>{@code @NonNull T fromProtobuf(@NonNull byte[] rawProtobufMessage) throws BadMessageException}</li>
 *   <li>{@code @NonNull T fromProtobuf(@NonNull P protobufMessage) throws BadMessageException}</li>
 * </ul>
 *
 * @param <P> Protobuf Message Type
 */
public interface ProtobufDataInterface<P extends MessageLite> {
    @NonNull
    P toProtobufMessage();

    default byte[] toProtobufBytes() {
        return toProtobufMessage().toByteArray();
    }
}
