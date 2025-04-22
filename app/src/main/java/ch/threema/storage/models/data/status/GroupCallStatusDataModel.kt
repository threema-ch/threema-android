/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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
import ch.threema.storage.models.data.status.StatusDataModel.StatusDataModelInterface
import ch.threema.storage.models.data.status.StatusDataModel.StatusType
import java.io.IOException

open class GroupCallStatusDataModel protected constructor() : StatusDataModelInterface {
    var callId: String? = null
        private set
    var groupId = 0
        private set
    var callerIdentity: String? = null
        private set
    var status = 0
        private set

    @StatusType
    override fun getType(): Int {
        return TYPE
    }

    override fun readData(key: String, value: Long) {
        when (key) {
            "status" -> status = value.toInt()
            "groupId" -> groupId = value.toInt()
            else -> {
                // ignore all other keys
            }
        }
    }

    override fun readData(key: String, value: Boolean) {
        // not used
    }

    override fun readData(key: String, value: String) {
        when (key) {
            "callId" -> callId = value
            "callerIdentity" -> callerIdentity = value
            else -> {
                // ignore all other keys
            }
        }
    }

    @Throws(IOException::class)
    override fun writeData(j: JsonWriter) {
        j.name("status").value(status.toLong())
        if (callId != null) {
            j.name("callId").value(callId)
        }
        if (callerIdentity != null) {
            j.name("callerIdentity").value(callerIdentity)
        }
        if (groupId != 0) {
            j.name("groupId").value(groupId.toLong())
        }
    }

    override fun readDataNull(key: String) {
        // not used
    }

    companion object {
        const val STATUS_STARTED = 1
        const val STATUS_ENDED = 2

        const val TYPE = 2

        fun createStarted(
            callId: String,
            groupId: Int,
            callerIdentity: String,
        ): GroupCallStatusDataModel {
            val status = GroupCallStatusDataModel()
            status.callId = callId
            status.groupId = groupId
            status.callerIdentity = callerIdentity
            status.status = STATUS_STARTED
            return status
        }

        fun createEnded(
            callId: String,
        ): GroupCallStatusDataModel {
            val status = GroupCallStatusDataModel()
            status.callId = callId
            status.callerIdentity = null
            status.status = STATUS_ENDED
            return status
        }
    }
}
