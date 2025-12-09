/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.logging.backend

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import ch.threema.app.BuildConfig
import ch.threema.app.DangerousTest
import ch.threema.app.ThreemaApplication
import ch.threema.app.getReadWriteExternalStoragePermissionRule
import ch.threema.logging.LogLevel
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@DangerousTest(reason = "Deletes logfile")
class DebugLogFileBackendTest {

    @JvmField
    @Rule
    val permissionRule: GrantPermissionRule = getReadWriteExternalStoragePermissionRule()

    @BeforeTest
    fun disableLogfile() {
        DebugLogFileBackend.setEnabled(false)
    }

    /**
     * Make sure that logging into the debug log file actually creates the debug log file.
     * Also test that the file is only created when enabled.
     */
    @Test
    fun testEnable() {
        val logFilePath = DebugLogFileBackend.getLogFilePath()

        // Log with the debug log file disabled
        val backend = DebugLogFileBackend(Log.INFO)
        backend.printSomething(level = Log.WARN)

        // Enabling the debug log file won't create the log file just yet
        assertFalse(logFilePath.exists())
        DebugLogFileBackend.setEnabled(true)
        assertFalse(logFilePath.exists())

        // Logs below the min log level are filtered
        backend.printSomething(level = Log.DEBUG)
        assertFalse(logFilePath.exists())

        // Log with the debug log file enabled
        backend.printSomething(level = Log.WARN)
        assertTrue(logFilePath.exists())

        // Verify that the fallback file is not created when not needed
        assertFalse(DebugLogFileBackend.getFallbackLogFilePath().exists())
    }

    /**
     * Make sure that the fallback log file is deleted when the default log file can be created successfully.
     */
    @Test
    fun testFallbackFileIsDeletedIfDefaultFileCanBeCreated() {
        // Create the fallback log file
        val fallbackLogFilePath = DebugLogFileBackend.getFallbackLogFilePath()
        assertTrue(fallbackLogFilePath.createNewFile(), "Could not create fallback logfile")

        // Enable logging and write a log message
        DebugLogFileBackend.setEnabled(true)
        val backend = DebugLogFileBackend(Log.INFO)
        backend.printSomething(level = Log.WARN)

        // Verify that the fallback file is now deleted, as it is not needed
        assertFalse(fallbackLogFilePath.exists())
    }

    /**
     * Make sure that disabling the debug log actually deletes the debug log file.
     */
    @Test
    fun testDisableRemovesFile() {
        val logFilePath = DebugLogFileBackend.getLogFilePath()
        assertFalse(logFilePath.exists())
        assertTrue(logFilePath.createNewFile(), "Could not create logfile")
        assertTrue(logFilePath.exists())
        DebugLogFileBackend.setEnabled(false)
        assertFalse(logFilePath.exists())
    }

    /**
     * Make sure that disabling the debug log actually deletes the fallback debug log file.
     */
    @Test
    fun testDisableRemovesFallbackFile() {
        val fallbackLogFilePath = DebugLogFileBackend.getFallbackLogFilePath()
        assertFalse(fallbackLogFilePath.exists())
        assertTrue(fallbackLogFilePath.createNewFile(), "Could not create fallback logfile")
        assertTrue(fallbackLogFilePath.exists())
        DebugLogFileBackend.setEnabled(false)
        assertFalse(fallbackLogFilePath.exists())
    }

    private fun DebugLogFileBackend.printSomething(@LogLevel level: Int) {
        printAsync(level, BuildConfig.LOG_TAG, null, "hi").get(500, TimeUnit.MILLISECONDS)
    }

    companion object {
        /**
         * On one of our CI devices, the access to the external storage directory is inexplicably broken, which leads these tests to fail.
         * Since the external storage is only used for the debug log file, and a fallback is already in place to write the debug log into
         * a different location if writing to the external storage fails, it is acceptable for the time being to simply skip these tests
         * based on the precondition that the external storage directory exists.
         */
        @BeforeClass
        @JvmStatic
        fun assumeDeviceHasAccessToExternalStorage() {
            Assume.assumeTrue(
                try {
                    ThreemaApplication.getAppContext().getExternalFilesDir(null)?.exists() == true
                } catch (_: Exception) {
                    false
                },
            )
        }
    }
}
