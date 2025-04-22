/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.receivers

import android.content.Context
import android.content.Intent
import ch.threema.app.managers.ListenerManager
import ch.threema.app.utils.IntentDataUtil

/**
 * Receive the message models that could not be sent when the notification has been explicitly
 * canceled.
 */
class CancelResendMessagesBroadcastReceiver : ActionBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // It is sufficient to trigger the listener. If the home activity (that manages resending
        // the messages) is not available, this event can be dismissed anyway.
        IntentDataUtil.getAbstractMessageModels(intent, messageService).forEach { messageModel ->
            ListenerManager.messageListeners.handle {
                it.onResendDismissed(messageModel)
            }
        }
    }
}
