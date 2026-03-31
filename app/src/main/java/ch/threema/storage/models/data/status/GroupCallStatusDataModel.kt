package ch.threema.storage.models.data.status

import android.util.JsonWriter
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.data.status.StatusDataModel.StatusDataModelInterface
import ch.threema.storage.models.data.status.StatusDataModel.StatusType
import java.io.IOException

open class GroupCallStatusDataModel protected constructor() : StatusDataModelInterface {
    var callId: String? = null
        private set
    var groupId = 0
        private set
    var callerIdentity: IdentityString? = null
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
            callerIdentity: IdentityString,
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
