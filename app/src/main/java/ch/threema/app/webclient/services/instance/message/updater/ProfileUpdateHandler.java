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

package ch.threema.app.webclient.services.instance.message.updater;

import android.graphics.Bitmap;

import org.slf4j.Logger;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.listeners.ProfileListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Profile;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.taskmanager.TriggerSource;

@WorkerThread
public class ProfileUpdateHandler extends MessageUpdater {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ProfileUpdateHandler");

    // Handler
    private final @NonNull HandlerExecutor handler;

    // Listeners
    private final ProfileListener listener;

    // Dispatchers
    private final @NonNull MessageDispatcher updateDispatcher;

    // Services
    private final @NonNull UserService userService;
    private final @NonNull ContactService contactService;

    @AnyThread
    public ProfileUpdateHandler(@NonNull HandlerExecutor handler,
                                @NonNull MessageDispatcher updateDispatcher,
                                @NonNull UserService userService,
                                @NonNull ContactService contactService) {
        super(Protocol.SUB_TYPE_PROFILE);
        this.handler = handler;
        this.updateDispatcher = updateDispatcher;
        this.userService = userService;
        this.contactService = contactService;
        this.listener = new Listener();
    }

    @Override
    public void register() {
        logger.debug("register()");
        ListenerManager.profileListeners.add(this.listener);
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    @Override
    public void unregister() {
        logger.debug("unregister()");
        ListenerManager.profileListeners.remove(this.listener);
    }

    /**
     * Send the updated profile to the peer.
     */
    private void sendProfile(String nickname, boolean sendAvatar) {
        MsgpackObjectBuilder data;
        if (sendAvatar) {
            byte[] avatar = null;
            final Bitmap avatarBitmap = this.contactService.getAvatar(userService.getIdentity(), true);
            if (avatarBitmap != null) {
                avatar = BitmapUtil.bitmapToByteArray(avatarBitmap, Protocol.FORMAT_AVATAR, Protocol.QUALITY_AVATAR_HIRES);
            }
            data = Profile.convert(nickname, avatar);
        } else {
            data = Profile.convert(nickname);
        }

        // Send message
        logger.debug("Sending profile update");
        this.send(this.updateDispatcher, data, null);
    }

    @AnyThread
    private class Listener implements ProfileListener {
        @Override
        public void onAvatarChanged(@NonNull TriggerSource triggerSource) {
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    ProfileUpdateHandler.this.sendProfile(userService.getPublicNickname(), true);
                }
            });
        }

        @Override
        public void onNicknameChanged(String newNickname) {
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    ProfileUpdateHandler.this.sendProfile(newNickname, false);
                }
            });
        }
    }
}
