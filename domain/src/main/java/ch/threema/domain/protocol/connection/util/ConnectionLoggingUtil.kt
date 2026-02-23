package ch.threema.domain.protocol.connection.util

import ch.threema.base.utils.getThreemaLogger
import org.slf4j.Logger

class ConnectionLoggingUtil {
    companion object {
        fun getConnectionLogger(name: String): Logger {
            return getThreemaLogger("ServerConnection.$name")
        }
    }
}
