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
import android.widget.Toast
import ch.threema.app.R
import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.tasks.TaskCreator
import ch.threema.domain.types.Identity
import java.lang.ref.WeakReference

class BlockedIdentitiesServiceImpl(
    private val preferenceService: PreferenceService,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskCreator: TaskCreator,
) : BlockedIdentitiesService {
    private val uniqueListName: String = "identity_list_blacklist"

    private var blockedIdentitiesCache: WeakReference<Set<String>> = WeakReference(null)

    @Synchronized
    override fun blockIdentity(identity: Identity, context: Context?) {
        if (isBlocked(identity)) {
            return
        }
        persistAndReflectIdentities(getBlockedIdentities() + identity)
        if (context != null) {
            Toast.makeText(
                context,
                context.getString(R.string.contact_now_blocked),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    @Synchronized
    override fun unblockIdentity(identity: Identity, context: Context?) {
        if (!isBlocked(identity)) {
            return
        }

        persistAndReflectIdentities(getBlockedIdentities() - identity)
        if (context != null) {
            Toast.makeText(
                context,
                context.getString(R.string.contact_now_unblocked),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    @Synchronized
    override fun isBlocked(identity: Identity): Boolean {
        return getBlockedIdentities().contains(identity)
    }

    @Synchronized
    override fun getAllBlockedIdentities(): Set<Identity> {
        return HashSet(getBlockedIdentities())
    }

    @Synchronized
    override fun persistBlockedIdentities(blockedIdentities: Set<Identity>) {
        // Find all modified identities (used for triggering the correct listeners)
        val modifiedIdentities = getAllBlockedIdentities().let { previouslyBlockedIdentities ->
            val newUnblockedIdentities = previouslyBlockedIdentities - blockedIdentities
            val newBlockedIdentities = blockedIdentities - previouslyBlockedIdentities
            newUnblockedIdentities + newBlockedIdentities
        }

        preferenceService.setList(uniqueListName, blockedIdentities.toTypedArray())
        blockedIdentitiesCache.clear()

        // Notify the listeners
        modifiedIdentities.forEach { blockedIdentity ->
            ListenerManager.contactListeners.handle { listener: ContactListener ->
                listener.onModified(blockedIdentity)
            }
        }
    }

    override fun toggleBlocked(identity: Identity, context: Context?) {
        if (isBlocked(identity)) {
            unblockIdentity(identity, context)
        } else {
            blockIdentity(identity, context)
        }
    }

    private fun persistAndReflectIdentities(blockedIdentities: Set<Identity>) {
        persistBlockedIdentities(blockedIdentities)
        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleReflectBlockedIdentitiesTask()
        }
    }

    private fun getBlockedIdentities(): Set<Identity> {
        return blockedIdentitiesCache.get() ?: run {
            val blockedIdentitiesSet = getFromPreferences()
            blockedIdentitiesCache = WeakReference(blockedIdentitiesSet)
            blockedIdentitiesSet
        }
    }

    private fun getFromPreferences(): MutableSet<Identity> {
        return preferenceService.getList(uniqueListName).toMutableSet()
    }
}
