/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.services.systemupdate

import ch.threema.app.services.UpdateSystemService

internal class SystemUpdateToVersion105 : UpdateSystemService.SystemUpdate {
    companion object {
        const val VERSION = 105
    }

    override fun runAsync() = true

    override fun runDirectly(): Boolean {
        // In a previous version of this migration there was also an update of the message uid
        // indices.
        // - `messageUidIdx` was dropped and a unique index `message_uid_idx` on
        //   `message.uid` was created
        // - `groupMessageUidIdx` was dropped and a unique index `m_group_message_uid_idx` on
        //   `m_group_message.uid` was created
        //
        // This yielded problems in the db migration on some devices where the `uid` was not unique
        // and an app crash was the result. Therefore this index change was removed from this
        // system update.
        // For devices where the migration succeeded without problems, the index uniqueness will be
        // fixed in the SystemUpdateToVersion106
        //
        // Additionally the original database scheme for reactions does not work if the foreign key
        // references a non-unique field (which is the case if the index is not unique anymore).
        // Therefore the original creation of the tables was also removed from this migration.
        // The _correct_ scheme will be created in migration to version 106 where also data migration
        // will be taken care of if the migration to 105 was already executed.
        //
        // With indices and table creations removed this migration is now empty, but kept to ensure
        // consistency in database versioning on devices where the migration was already successfully
        // executed.
        return true
    }

    override fun getText() = "version $VERSION (empty reaction migration)"
}
