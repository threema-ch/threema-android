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

package ch.threema.app.webclient.converter;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.storage.models.ConversationModel;

@AnyThread
public class NotificationSettings extends Converter {

    private final static String SOUND = "sound";
    private final static String DND = "dnd";
    private final static String MODE = "mode";
    private final static String MENTION_ONLY = "mentionOnly";
    private final static String UNTIL = "until";
    private final static String MODE_DEFAULT = "default";
    private final static String MODE_MUTED = "muted";
    private final static String MODE_ON = "on";
    private final static String MODE_OFF = "off";
    private final static String MODE_UNTIL = "until";

    @NonNull
    public static MsgpackObjectBuilder convert(@NonNull ConversationModel conversation) throws ConversionException {
        // Prepare objects
        final MsgpackObjectBuilder data = new MsgpackObjectBuilder();
        final MsgpackObjectBuilder sound = new MsgpackObjectBuilder();
        final MsgpackObjectBuilder dnd = new MsgpackObjectBuilder();

        // Services
        final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            throw new ConversionException("Could not get service manager");
        }
        final RingtoneService ringtoneService = serviceManager.getRingtoneService();

        // Conversation UID
        final String uid = conversation.getReceiver().getUniqueIdString();

        // Sound settings
        if (ringtoneService.hasCustomRingtone(uid)
            && ringtoneService.isSilent(uid, conversation.isGroupConversation())) {
            sound.put(MODE, MODE_MUTED);
        } else {
            sound.put(MODE, MODE_DEFAULT);
        }

        // DND settings
        final @Nullable NotificationTriggerPolicyOverride notificationTriggerPolicyOverride = conversation.getReceiver().getNotificationTriggerPolicyOverrideOrNull();
        if (notificationTriggerPolicyOverride != null) {
            if (notificationTriggerPolicyOverride instanceof NotificationTriggerPolicyOverride.NotMuted) {
                dnd.put(MODE, MODE_OFF);
                dnd.put(MENTION_ONLY, false);
            } else if (notificationTriggerPolicyOverride instanceof NotificationTriggerPolicyOverride.MutedIndefinite) {
                dnd.put(MODE, MODE_ON);
                dnd.put(MENTION_ONLY, false);
            } else if (notificationTriggerPolicyOverride instanceof NotificationTriggerPolicyOverride.MutedIndefiniteExceptMentions) {
                dnd.put(MODE, MODE_ON);
                dnd.put(MENTION_ONLY, true);
            } else if (notificationTriggerPolicyOverride instanceof NotificationTriggerPolicyOverride.MutedUntil) {
                dnd.put(MODE, MODE_UNTIL);
                dnd.put(UNTIL, ((NotificationTriggerPolicyOverride.MutedUntil) notificationTriggerPolicyOverride).getUtcMillis());
                dnd.put(MENTION_ONLY, false);
            }
        } else {
            dnd.put(MODE, MODE_OFF);
        }

        data.put(SOUND, sound);
        data.put(DND, dnd);
        return data;
    }
}
