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

package ch.threema.app.webclient.services.instance.message.receiver;

import android.graphics.Bitmap;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Profile;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * Webclient sending the user profile.
 */
@WorkerThread
public class ProfileRequestHandler extends MessageReceiver {
    private static final Logger logger = getThreemaLogger("ProfileRequestHandler");

    // Dispatchers
    @NonNull
    private final MessageDispatcher responseDispatcher;

    // Services
    @NonNull
    private final UserService userService;
    @NonNull
    private final ContactService contactService;

    // Listener
    @WorkerThread
    public interface Listener {
        void onReceived();

        void onAnswered();
    }

    private final Listener listener;

    @AnyThread
    public ProfileRequestHandler(@NonNull MessageDispatcher responseDispatcher,
                                 @NonNull UserService userService,
                                 @NonNull ContactService contactService,
                                 @NonNull Listener listener) {
        super(Protocol.SUB_TYPE_PROFILE);
        this.responseDispatcher = responseDispatcher;
        this.userService = userService;
        this.contactService = contactService;
        this.listener = listener;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received profile request");

        // Notify listener, so we can register for changes
        if (this.listener != null) {
            this.listener.onReceived();
        }

        // Send initial response
        this.respond();
    }

    /**
     * Respond with profile.
     */
    private void respond() {
        logger.debug("Respond with profile");

        // Collect required information
        final String identity = this.userService.getIdentity();
        final byte[] publicKey = this.userService.getPublicKey();
        final String nickname = this.userService.getPublicNickname();
        final Bitmap avatarBitmap = this.contactService.getAvatar(identity, true);
        byte[] avatar = null;
        if (avatarBitmap != null) {
            avatar = BitmapUtil.bitmapToByteArray(avatarBitmap, Protocol.FORMAT_AVATAR, Protocol.QUALITY_AVATAR_HIRES);
        }

        // Create msgpack object
        final MsgpackObjectBuilder data = Profile.convert(identity, publicKey, nickname, avatar);

        // Send message
        this.send(this.responseDispatcher, data, null);

        // Notify listeners
        if (this.listener != null) {
            this.listener.onAnswered();
        }
    }

    @Override
    protected boolean maybeNeedsConnection() {
        // A profile request should not result in any new messages
        return false;
    }
}
