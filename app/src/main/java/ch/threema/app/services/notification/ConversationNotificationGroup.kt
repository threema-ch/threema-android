/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.app.services.notification

import android.graphics.Bitmap
import ch.threema.app.messagereceiver.MessageReceiver

data class ConversationNotificationGroup(

    @JvmField
    val uid: String,

    @JvmField
    var name: String,

    @JvmField
    var shortName: String?,

    @JvmField
    val messageReceiver: MessageReceiver<*>,

    private val onFetchAvatar: () -> Bitmap?
) {

    @JvmField
    var lastNotificationDate: Long = 0L

    @JvmField
    val conversations: MutableList<NotificationService.ConversationNotification> = mutableListOf()

    @JvmField
    val notificationId: Int = messageReceiver.uniqueId

    fun loadAvatar(): Bitmap? = onFetchAvatar()
}
