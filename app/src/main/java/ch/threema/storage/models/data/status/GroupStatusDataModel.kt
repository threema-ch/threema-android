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

package ch.threema.storage.models.data.status

import android.util.JsonWriter
import ch.threema.domain.types.Identity
import ch.threema.storage.models.data.status.StatusDataModel.StatusType

class GroupStatusDataModel : StatusDataModel.StatusDataModelInterface {
    private val statusKey = "status"
    private val identityKey = "identity"
    private val ballotNameKey = "ballotName"
    private val newGroupNameKey = "newGroupName"

    enum class GroupStatusType(
        val type: Int,
        val requiresIdentity: Boolean = false,
        val requiresBallotName: Boolean = false,
        val requiresNewGroupName: Boolean = false,
    ) {
        /** Group has been created */
        CREATED(0),

        /** Group has been renamed */
        RENAMED(1, requiresNewGroupName = true),

        /** Group picture has been updated */
        PROFILE_PICTURE_UPDATED(2),

        /** A member has been added */
        MEMBER_ADDED(3, requiresIdentity = true),

        /** A member has left the group */
        MEMBER_LEFT(4, requiresIdentity = true),

        /** A member has been kicked */
        MEMBER_KICKED(5, requiresIdentity = true),

        /** A group is now a notes group */
        IS_NOTES_GROUP(6),

        /** A group is not a notes group anymore */
        IS_PEOPLE_GROUP(7),

        /** A member has cast a vote */
        FIRST_VOTE(8, requiresBallotName = true, requiresIdentity = true),

        /** A member has changed a vote */
        MODIFIED_VOTE(9, requiresBallotName = true, requiresIdentity = true),

        /** A member has cast a vote anonymously */
        RECEIVED_VOTE(10, requiresBallotName = true),

        /** Votes are complete */
        VOTES_COMPLETE(11, requiresBallotName = true),

        /** Group description changed */
        GROUP_DESCRIPTION_CHANGED(12),

        /** The creator left the group */
        ORPHANED(13),
        ;

        companion object {
            fun fromInt(value: Int) = GroupStatusType.values().first { it.type == value }
        }
    }

    var statusType: GroupStatusType = GroupStatusType.CREATED
        private set
    var identity: Identity? = null
        private set
    var ballotName: String? = null
        private set
    var newGroupName: String? = null
        private set

    @StatusType
    override fun getType(): Int = TYPE

    override fun readData(key: String, value: String) {
        when (key) {
            identityKey -> identity = value
            ballotNameKey -> ballotName = value
            newGroupNameKey -> newGroupName = value
        }
    }

    override fun readData(key: String?, value: Long) {
        if (statusKey == key) {
            statusType = GroupStatusType.fromInt(value.toInt())
        }
    }

    override fun readData(key: String?, value: Boolean) {
        // Group status messages do not contain boolean values
    }

    override fun readDataNull(key: String?) {
        // Group status messages do not contain null values
    }

    override fun writeData(j: JsonWriter) {
        j.name(statusKey).value(statusType.type.toLong())
        if (statusType.requiresIdentity && identity != null) {
            j.name(identityKey).value(identity)
        }
        if (statusType.requiresBallotName && ballotName != null) {
            j.name(ballotNameKey).value(ballotName)
        }
        if (statusType.requiresNewGroupName && newGroupName != null) {
            j.name(newGroupNameKey).value(newGroupName)
        }
    }

    companion object {
        const val TYPE = 4

        /**
         * Create a group status model of the given type.
         */
        @JvmStatic
        fun create(
            type: GroupStatusType,
            identity: Identity?,
            ballotName: String?,
            newGroupName: String?,
        ): GroupStatusDataModel {
            return GroupStatusDataModel().apply {
                this.statusType = type
                if (type.requiresIdentity) {
                    this.identity = identity
                }
                if (type.requiresBallotName) {
                    this.ballotName = ballotName
                }
                if (type.requiresNewGroupName) {
                    this.newGroupName = newGroupName
                }
            }
        }
    }
}
