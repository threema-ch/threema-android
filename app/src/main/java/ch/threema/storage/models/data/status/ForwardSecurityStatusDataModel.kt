package ch.threema.storage.models.data.status

import android.util.JsonWriter
import androidx.annotation.IntDef
import ch.threema.storage.models.data.status.StatusDataModel.StatusDataModelInterface
import ch.threema.storage.models.data.status.StatusDataModel.StatusType
import java.io.IOException

class ForwardSecurityStatusDataModel : StatusDataModelInterface {
    @IntDef(
        value = [
            ForwardSecurityStatusType.STATIC_TEXT,
            ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY,
            ForwardSecurityStatusType.FORWARD_SECURITY_RESET,
            ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED,
            ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED_RX,
            ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED,
            ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER,
            ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE,
            ForwardSecurityStatusType.FORWARD_SECURITY_ILLEGAL_SESSION_STATE,
            // TODO(ANDR-2519): Can this be removed when md supports fs?
            //  Maybe not, because theses statuses might already be saved to the database...
            ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED,
        ],
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ForwardSecurityStatusType {
        companion object {
            const val STATIC_TEXT = 0
            const val MESSAGE_WITHOUT_FORWARD_SECURITY = 1
            const val FORWARD_SECURITY_RESET = 2
            const val FORWARD_SECURITY_ESTABLISHED = 3
            const val FORWARD_SECURITY_ESTABLISHED_RX =
                4 // As of version 1.1 this status is not created anymore
            const val FORWARD_SECURITY_MESSAGES_SKIPPED = 5
            const val FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER = 6
            const val FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE = 7
            const val FORWARD_SECURITY_ILLEGAL_SESSION_STATE = 8

            // TODO(ANDR-2519): Can this be removed when md supports fs?
            //  Maybe not, because theses statuses might already be saved to the database...
            const val FORWARD_SECURITY_DISABLED = 9
        }
    }

    @ForwardSecurityStatusType
    var status = 0
        private set
    var quantity = 0
        private set
    var staticText: String? = null
        private set

    @StatusType
    override fun getType(): Int {
        return TYPE
    }

    override fun readData(key: String, value: Long) {
        when (key) {
            "status" -> status = value.toInt()
            "quantity" -> quantity = value.toInt()
        }
    }

    override fun readData(key: String, value: Boolean) {}
    override fun readData(key: String, value: String) {
        when (key) {
            "staticText" -> staticText = value
        }
    }

    @Throws(IOException::class)
    override fun writeData(j: JsonWriter) {
        j.name("status").value(status.toLong())
        j.name("quantity").value(quantity.toLong())
        j.name("staticText").value(staticText)
    }

    override fun readDataNull(key: String) {}

    companion object {
        const val TYPE = 3

        @JvmStatic
        fun create(type: Int, quantity: Int, staticText: String?): ForwardSecurityStatusDataModel {
            val status = ForwardSecurityStatusDataModel()
            status.status = type
            status.quantity = quantity
            status.staticText = staticText
            return status
        }
    }
}
