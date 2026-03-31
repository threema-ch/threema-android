package ch.threema.app.systemupdates.updates

import android.content.Context
import java.io.File
import kotlin.getValue
import org.koin.core.component.inject

class SystemUpdateToVersion126 : SystemUpdate {
    private val appContext: Context by inject()

    override fun run() {
        val errorRecordsDirectory = File(appContext.filesDir, "error-records")
        if (!errorRecordsDirectory.isDirectory) {
            return
        }
        errorRecordsDirectory.listFiles { file ->
            file.name.endsWith("_v1.json")
        }
            ?.forEach { errorRecordFile ->
                errorRecordFile.delete()
            }
    }

    override val version = 126

    override fun getDescription() = "delete v1 error records"
}
