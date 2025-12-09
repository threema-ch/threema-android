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

package ch.threema.app.logging

import android.content.SharedPreferences
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppVersionHistoryManagerTest {
    private var persistedLatestRecord: String? = null
    private var persistedHistory: String? = null

    private lateinit var sharedPreferencesMock: SharedPreferences
    private lateinit var sharedPreferencesEditorMock: SharedPreferences.Editor
    private lateinit var timeProvider: TestTimeProvider
    private lateinit var appVersionHistoryManager: AppVersionHistoryManager

    @BeforeTest
    fun setUp() {
        sharedPreferencesEditorMock = mockk(relaxed = true)
        sharedPreferencesMock = mockk {
            every { getString("latest_record", any()) } answers { persistedLatestRecord }
            every { getString("history", any()) } answers { persistedHistory }
            every { edit() } returns sharedPreferencesEditorMock
        }
        timeProvider = TestTimeProvider()
        appVersionHistoryManager = AppVersionHistoryManager(
            appContext = mockk {
                every { getSharedPreferences("app_version_history", 0) } returns sharedPreferencesMock
            },
            timeProvider = timeProvider,
            currentVersionName = CURRENT_VERSION_NAME,
            currentVersionCode = CURRENT_VERSION_CODE,
        )
    }

    @Test
    fun `check when no previous record exists`() {
        persistedLatestRecord = null

        val result = appVersionHistoryManager.check()

        assertEquals(
            AppVersionCheckResult.NoPreviousVersion,
            result,
        )
    }

    @Test
    fun `check when previous record has different version code as current`() {
        persistedLatestRecord = "123;;1.2.3;;1761835882123"

        val result = appVersionHistoryManager.check()

        assertEquals(
            AppVersionCheckResult.SameVersion,
            result,
        )
    }

    @Test
    fun `check when previous record has same version code as current`() {
        persistedLatestRecord = "122;;1.2.2;;1761835882123"

        val result = appVersionHistoryManager.check()

        assertEquals(
            AppVersionCheckResult.DifferentVersion(
                previous = AppVersionRecord(
                    versionCode = 122,
                    versionName = "1.2.2",
                    time = Instant.ofEpochMilli(1761835882123L),
                ),
            ),
            result,
        )
    }

    @Test
    fun `create record`() {
        persistedHistory = "121;;1.2.1;;1761835800000\n122;;1.2.2;;1761835882000"
        timeProvider.set(1761835882123L)

        val newRecord = appVersionHistoryManager.record()

        verifyOrder {
            sharedPreferencesEditorMock.putString("latest_record", "123;;1.2.3;;1761835882123")
            sharedPreferencesEditorMock.putString("history", "121;;1.2.1;;1761835800000\n122;;1.2.2;;1761835882000\n123;;1.2.3;;1761835882123")
            sharedPreferencesEditorMock.apply()
        }
        assertEquals(
            AppVersionRecord(
                versionCode = 123,
                versionName = "1.2.3",
                time = Instant.ofEpochMilli(1761835882123L),
            ),
            newRecord,
        )
    }

    @Test
    fun `get history`() {
        persistedHistory = "121;;1.2.1;;1761835800000\n122;;1.2.2;;1761835882000\n123;;1.2.3;;1761835882123"

        val history = appVersionHistoryManager.getHistory()

        assertEquals(
            listOf(
                AppVersionRecord(
                    versionCode = 121,
                    versionName = "1.2.1",
                    time = Instant.ofEpochMilli(1761835800000L),
                ),
                AppVersionRecord(
                    versionCode = 122,
                    versionName = "1.2.2",
                    time = Instant.ofEpochMilli(1761835882000L),
                ),
                AppVersionRecord(
                    versionCode = 123,
                    versionName = "1.2.3",
                    time = Instant.ofEpochMilli(1761835882123L),
                ),
            ),
            history,
        )
    }

    @Test
    fun `get empty history`() {
        persistedHistory = null
        assertEquals(
            emptyList(),
            appVersionHistoryManager.getHistory(),
        )
    }

    companion object {
        private const val CURRENT_VERSION_NAME = "1.2.3"
        private const val CURRENT_VERSION_CODE = 123
    }
}
