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

import android.content.Context
import ch.threema.base.utils.getThreemaLogger
import java.io.File

private val logger = getThreemaLogger("SystemUpdateToVersion115")

class SystemUpdateToVersion115(
    private val appContext: Context,
) : SystemUpdate {
    override fun run() {
        try {
            // Deletes the directory that was already deleted in SystemUpdateToVersion63, but might have been recreated afterwards
            File(appContext.getExternalFilesDir(null), "tmp").deleteRecursively()
        } catch (e: Exception) {
            // If deletion fails, we just continue, as the directory might generally not be accessible anymore
            logger.error("Failed to delete external tmp directory", e)
        }
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "delete obsolete external tmp directory"

    companion object {
        const val VERSION = 115
    }
}
