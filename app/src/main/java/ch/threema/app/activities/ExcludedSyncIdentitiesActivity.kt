/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.activities

import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("ExcludedSyncIdentitiesActivity")

class ExcludedSyncIdentitiesActivity : IdentityListActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val identityList: IdentityList? by lazy {
        val listService = ThreemaApplication.getServiceManager()?.excludedSyncIdentitiesService
            ?: return@lazy null

        object : IdentityList {
            override fun getAll(): Set<String> {
                return listService.all?.toSet() ?: emptySet()
            }

            override fun addIdentity(identity: String) {
                listService.add(identity)
            }

            override fun removeIdentity(identity: String) {
                listService.remove(identity)
            }
        }
    }

    override fun getIdentityListHandle(): IdentityList? {
        return identityList
    }

    override fun getBlankListText(): String {
        return this.getString(R.string.prefs_sum_excluded_sync_identities)
    }

    override fun getTitleText(): String {
        return this.getString(R.string.prefs_title_excluded_sync_identities)
    }
}
