/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.services

import android.content.Context
import ch.threema.app.tasks.ReflectSettingsSyncTask
import ch.threema.base.SessionScoped
import ch.threema.domain.types.Identity

/**
 * Manage blocked identities.
 */
@SessionScoped
interface BlockedIdentitiesService {
    /**
     * Block an identity. The identity will be persisted regardless whether a contact with this
     * identity exists or not. If a [context] is provided, a toast is shown.
     *
     * Note that this method reflects a [ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate] with
     * the currently blocked identities.
     *
     * @param identity the identity to block
     * @param context if provided, a toast is shown
     */
    fun blockIdentity(identity: Identity, context: Context? = null)

    /**
     * Unblock an identity. If a [context] is provided, a toast is shown.
     *
     * Note that this method reflects a [ReflectSettingsSyncTask.ReflectBlockedIdentitiesSyncUpdate] with
     * the currently blocked identities.
     *
     * @param identity the identity to unblock
     * @param context if provided, a toast is shown
     */
    fun unblockIdentity(identity: Identity, context: Context? = null)

    /**
     * Checks whether the [identity] is blocked or not.
     */
    fun isBlocked(identity: Identity): Boolean

    /**
     * Blocks the identity if currently not blocked or vice versa. If a [context] is provided, this
     * additionally shows a toast.
     *
     * @param identity the identity that will change from blocked to unblocked or vice versa
     * @param context if provided, a toast is shown
     */
    fun toggleBlocked(identity: Identity, context: Context? = null)

    /**
     * Get all blocked identities.
     */
    fun getAllBlockedIdentities(): Set<Identity>

    /**
     * Persist the blocked identities. This replaces all currently blocked identities with
     * [blockedIdentities]. Note that there is no reflection done.
     */
    fun persistBlockedIdentities(blockedIdentities: Set<Identity>)
}
