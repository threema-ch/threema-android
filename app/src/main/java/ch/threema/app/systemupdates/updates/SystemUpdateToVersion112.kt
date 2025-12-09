/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.systemupdates.updates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.profilepicture.CheckedProfilePicture
import ch.threema.app.profilepicture.RawProfilePicture
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.TriggerSource

private val logger = getThreemaLogger("SystemUpdateToVersion112")

class SystemUpdateToVersion112(private val serviceManager: ServiceManager) : SystemUpdate {

    override fun run() {
        val userService = serviceManager.userService

        val userProfilePicture = userService.userProfilePicture
        if (userProfilePicture == null) {
            logger.info("No user profile picture is set. Aborting migration.")
            return
        }

        val checkedProfilePicture = when (userProfilePicture) {
            is RawProfilePicture -> userProfilePicture.toChecked()
            is CheckedProfilePicture -> userProfilePicture
        }
        if (checkedProfilePicture == null) {
            logger.warn("Profile picture could not be converted. Removing profile picture.")
            userService.removeUserProfilePicture(TriggerSource.LOCAL)
            return
        }

        if (checkedProfilePicture.bytes.contentEquals(userProfilePicture.bytes)) {
            logger.info("No migration required as profile picture is already a valid jpeg.")
        } else {
            userService.setUserProfilePicture(checkedProfilePicture, TriggerSource.LOCAL).let { success ->
                if (success) {
                    logger.info("Converted profile picture successfully set")
                } else {
                    logger.warn("Could not set converted profile picture")
                }
            }
        }
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "convert user profile picture to jpeg"

    companion object {
        const val VERSION = 112
    }
}
