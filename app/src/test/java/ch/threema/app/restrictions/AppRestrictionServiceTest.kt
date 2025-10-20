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

package ch.threema.app.restrictions

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.work.WorkInfo
import androidx.work.impl.WorkManagerImpl
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ServiceManager
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.RuntimeUtil
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.work.WorkData
import ch.threema.domain.protocol.api.work.WorkMDMSettings
import com.google.common.util.concurrent.AbstractFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.json.JSONObject
import org.junit.Assume
import org.junit.BeforeClass

class AppRestrictionServiceTest {

    @BeforeTest
    fun setUp() {
        mockkObject(ThreemaApplication)
        mockkStatic(WorkManagerImpl::class)
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(ThreemaApplication)
        unmockkStatic(WorkManagerImpl::class)
        unmockkStatic(RuntimeUtil::class)
    }

    @Test
    fun testReload_AppRestriction_NoWorkMDM() {
        val mockContext = mockk<Context>(relaxed = true)
        val restrictionManagerMock = mockk<RestrictionsManager>(relaxed = true)
        val service = mockk<AppRestrictionService>(relaxed = true)

        // AppRestriction Bundle
        val bundle = mockk<Bundle>(relaxed = true)

        every { ThreemaApplication.getAppContext() } returns mockContext
        every { mockContext.getSystemService(Context.RESTRICTIONS_SERVICE) } returns restrictionManagerMock
        every { restrictionManagerMock.applicationRestrictions } returns bundle

        val workMDMSettings = WorkMDMSettings()
        every { service.workMDMSettings } returns workMDMSettings

        mockStaticWorkManagerImpl()

        // Partial mock
        every { service.reload() } answers { callOriginal() }
        every { service.appRestrictions } answers { callOriginal() }
        service.reload()

        // required calls
        verify(exactly = 1) { mockContext.getSystemService(Context.RESTRICTIONS_SERVICE) }

        // no overrides
        verify(exactly = 0) { bundle.putInt(any(), any()) }
        verify(exactly = 0) { bundle.putBoolean(any(), any()) }
        verify(exactly = 0) { bundle.putString(any(), any()) }
        verify(exactly = 0) { bundle.putLong(any(), any()) }
        verify(exactly = 0) { bundle.putDouble(any(), any()) }

        val loadedRestrictions = service.appRestrictions

        assertNotNull(loadedRestrictions)
        assertEquals(bundle, loadedRestrictions)
    }

    @Test
    fun testReload_AppRestriction_WorkMDM_OverrideFalse() {
        val mockContext = mockk<Context>(relaxed = true)
        val restrictionManagerMock = mockk<RestrictionsManager>(relaxed = true)
        val service = mockk<AppRestrictionService>(relaxed = true)

        // AppRestriction Bundle
        val bundle = mockk<Bundle>(relaxed = true)

        every { ThreemaApplication.getAppContext() } returns mockContext
        every { mockContext.getSystemService(Context.RESTRICTIONS_SERVICE) } returns restrictionManagerMock
        every { restrictionManagerMock.applicationRestrictions } returns bundle

        every { bundle.containsKey("param1") } returns true
        every { bundle.containsKey("param2") } returns false
        every { bundle.containsKey("param3") } returns false

        mockStaticWorkManagerImpl()

        val workMDMSettings = WorkMDMSettings()
        workMDMSettings.override = false
        // should not be written
        workMDMSettings.parameters["param1"] = "work-param-1"
        // should be written
        workMDMSettings.parameters["param2"] = 22
        // should be written
        workMDMSettings.parameters["param3"] = true

        every { service.workMDMSettings } returns workMDMSettings

        // Partial mock
        every { service.reload() } answers { callOriginal() }
        every { service.appRestrictions } answers { callOriginal() }
        service.reload()

        // required calls
        verify(exactly = 1) { mockContext.getSystemService(Context.RESTRICTIONS_SERVICE) }

        // no overrides
        verify(exactly = 1) { bundle.putInt("param2", 22) }
        verify(exactly = 1) { bundle.putBoolean("param3", true) }
        verify(exactly = 0) { bundle.putString(any(), any()) }
        verify(exactly = 0) { bundle.putLong(any(), any()) }
        verify(exactly = 0) { bundle.putDouble(any(), any()) }

        val loadedRestrictions = service.appRestrictions

        assertNotNull(loadedRestrictions)
        assertEquals(bundle, loadedRestrictions)
    }

    @Test
    fun testReload_AppRestriction_WorkMDM_OverrideTrue() {
        val mockContext = mockk<Context>(relaxed = true)
        val restrictionManagerMock = mockk<RestrictionsManager>(relaxed = true)
        val service = mockk<AppRestrictionService>(relaxed = true)

        // AppRestriction Bundle
        val bundle = mockk<Bundle>(relaxed = true)
        every { ThreemaApplication.getAppContext() } returns mockContext
        every { mockContext.getSystemService(Context.RESTRICTIONS_SERVICE) } returns restrictionManagerMock
        every { restrictionManagerMock.applicationRestrictions } returns bundle

        every { bundle.containsKey("param1") } returns true
        every { bundle.containsKey("param2") } returns false
        every { bundle.containsKey("param3") } returns false

        mockStaticWorkManagerImpl()

        val workMDMSettings = WorkMDMSettings()
        workMDMSettings.override = true
        // should not be written
        workMDMSettings.parameters["param1"] = "work-param-1"
        // should be written
        workMDMSettings.parameters["param2"] = 22
        // should be written
        workMDMSettings.parameters["param3"] = true

        every { service.workMDMSettings } returns workMDMSettings

        // Partial mock
        every { service.reload() } answers { callOriginal() }
        every { service.appRestrictions } answers { callOriginal() }
        service.reload()

        // required calls
        verify(exactly = 1) { mockContext.getSystemService(Context.RESTRICTIONS_SERVICE) }

        // no overrides
        verify(exactly = 1) { bundle.putInt("param2", 22) }
        verify(exactly = 1) { bundle.putBoolean("param3", true) }
        verify(exactly = 1) { bundle.putString("param1", "work-param-1") }
        verify(exactly = 0) { bundle.putLong(any(), any()) }
        verify(exactly = 0) { bundle.putDouble(any(), any()) }

        val loadedRestrictions = service.appRestrictions

        assertNotNull(loadedRestrictions)
        assertEquals(bundle, loadedRestrictions)
    }

    @Test
    fun testConvert_Json() {
        val service = mockk<AppRestrictionService> {
            every { convertJSONToWorkMDM(any()) } answers { callOriginal() }
        }

        val json = JSONObject(
            mapOf(
                "override" to true,
                "parameters" to mapOf(
                    "param1" to "param1-value",
                    "param2" to "param2-value",
                ),
            ),
        )

        val result = service.convertJSONToWorkMDM(json)
        assertNotNull(result)
        assertTrue(result.override)
        assertNotNull(result.parameters)
        assertEquals(2, result.parameters.size.toLong())
        assertTrue(result.parameters.containsKey("param1"))
        assertEquals("param1-value", result.parameters["param1"])
        assertTrue(result.parameters.containsKey("param2"))
        assertEquals("param2-value", result.parameters["param2"])
    }

    @Test
    fun testConvert_WorkMDMSettings() {
        val service = mockk<AppRestrictionService> {
            every { convertWorkMDMToJSON(any()) } answers { callOriginal() }
        }

        val s = WorkMDMSettings()
        s.override = true
        s.parameters["param1"] = "param1-value"
        s.parameters["param2"] = "param2-value"

        val r = service.convertWorkMDMToJSON(s)

        assertNotNull(r)
        assertTrue(r.getBoolean("override"))
        assertTrue(r.has("parameters"))
        assertEquals("param1-value", (r.getJSONObject("parameters").getString("param1")))
        assertEquals("param2-value", (r.getJSONObject("parameters").getString("param2")))
    }

    @Test
    fun testStoreWorkMDMSettings() {
        val service = mockk<AppRestrictionService>(relaxed = true) {
            every { storeWorkMDMSettings(any()) } answers { callOriginal() }
        }

        val mockContext = mockk<Context> {
            every { getString(any()) } returns ""
        }

        val serviceManagerMock = mockk<ServiceManager>()
        val encryptedPreferenceStoreMock = mockEncryptedPreferenceStore(null)

        every { ThreemaApplication.getServiceManager() } returns serviceManagerMock
        every { ThreemaApplication.getAppContext() } returns mockContext
        every { serviceManagerMock.encryptedPreferenceStore } returns encryptedPreferenceStoreMock

        val jsonObject = JSONObject()
        val s = WorkMDMSettings()
        s.override = true
        s.parameters["param1"] = "param1-value"
        s.parameters["param2"] = "param2-value"

        every { service.convertWorkMDMToJSON(s) } returns jsonObject
        service.storeWorkMDMSettings(s)

        // Check if the store method is called
        verify(exactly = 1) { encryptedPreferenceStoreMock.save("wrk_app_restriction", jsonObject) }
        verify(exactly = 1) { service.reload() }
    }

    @Test
    fun testGetWorkMDMSettings() {
        val service = mockk<AppRestrictionService>(relaxed = true) {
            every { workMDMSettings } answers { callOriginal() }
            every { filterWorkMdmSettings(any()) } answers { callOriginal() }
        }

        val mockContext = mockk<Context> {
            every { getString(any()) } returns ""
        }

        val serviceManagerMock = mockk<ServiceManager>()
        val workRestrictionsJson = JSONObject()
        val encryptedPreferenceStoreMock = mockEncryptedPreferenceStore(workRestrictionsJson)

        every { ThreemaApplication.getServiceManager() } returns serviceManagerMock
        every { ThreemaApplication.getAppContext() } returns mockContext
        every { serviceManagerMock.encryptedPreferenceStore } returns encryptedPreferenceStoreMock

        val s = WorkMDMSettings()

        every { service.convertJSONToWorkMDM(workRestrictionsJson) } returns s
        assertEquals(s, service.workMDMSettings)

        // Check if the store method is called
        verify(exactly = 1) { encryptedPreferenceStoreMock.getJSONObject("wrk_app_restriction") }
    }

    @Test
    fun testFetchAndStoreWorkMDMSettings() {
        val service = mockk<AppRestrictionService>(relaxed = true) {
            every { fetchAndStoreWorkMDMSettings(any(), any()) } answers { callOriginal() }
        }

        mockkStatic(RuntimeUtil::class)
        every { RuntimeUtil.isOnUiThread() } returns false

        val apiConnectorMock = mockk<APIConnector>()
        val credentials = UserCredentials("hans", "dampf")

        val workData = WorkData()
        every { apiConnectorMock.fetchWorkData("hans", "dampf", any()) } returns workData
        service.fetchAndStoreWorkMDMSettings(apiConnectorMock, credentials)

        verify(exactly = 1) { apiConnectorMock.fetchWorkData("hans", "dampf", any()) }
        verify(exactly = 1) { service.storeWorkMDMSettings(workData.mdm) }
    }

    @Test
    fun testFilterNonWorkMdmParametersFromPreferences() {
        // arrange
        val bundleMock = mockBundle()

        val restrictionManagerMock = mockk<RestrictionsManager> {
            every { applicationRestrictions } returns bundleMock
        }

        val mockContext = mockk<Context> {
            every { getSystemService(Context.RESTRICTIONS_SERVICE) } returns restrictionManagerMock
            every { getString(any()) } returnsMany listOf(
                "th_id_backup",
                "th_id_backup_password",
                "th_safe_password",
                "th_license_username",
                "th_license_password",
            )
        }

        val parameters = JSONObject().apply {
            put("th_id_backup", "ABCD1234")
            put("th_id_backup_password", "T0p\$ecr3t")
            put("th_safe_password", "T0p\$ecr3t")
            put("th_license_username", "<username>")
            put("th_license_password", "T0p\$ecr3t")
            put("th_safe_enable", true)
            put("th_firstname", "John")
            put("th_lastname", "Doe")
        }
        val workRestrictionsJson = JSONObject().apply {
            put("override", true)
            put("parameters", parameters)
        }
        val encryptedPreferenceStoreMock = mockEncryptedPreferenceStore(workRestrictionsJson)

        val serviceManagerMock = mockk<ServiceManager> {
            every { encryptedPreferenceStore } returns encryptedPreferenceStoreMock
            every { multiDeviceManager } returns mockk()
            every { taskManager } returns mockk()
        }
        val taskCreatorMock = TaskCreator(serviceManagerMock)
        every { serviceManagerMock.taskCreator } returns taskCreatorMock

        every { ThreemaApplication.getAppContext() } returns mockContext
        every { ThreemaApplication.getServiceManager() } returns serviceManagerMock

        mockStaticWorkManagerImpl()

        // Act
        val service = AppRestrictionService()
        val settings = service.workMDMSettings
        service.reload()
        val appRestrictions = service.appRestrictions

        // Assert
        verify(exactly = 1) { encryptedPreferenceStoreMock.getJSONObject("wrk_app_restriction") }
        verify(exactly = 5) { mockContext.getString(any()) }

        assertTrue(settings.override)
        assertEquals(3, settings.parameters.size.toLong())
        assertEquals(true, settings.parameters["th_safe_enable"])
        assertEquals("John", settings.parameters["th_firstname"])
        assertEquals("Doe", settings.parameters["th_lastname"])

        assertEquals(3, appRestrictions.size().toLong())
        assertTrue(appRestrictions.getBoolean("th_safe_enable"))
        assertEquals("John", appRestrictions.getString("th_firstname"))
        assertEquals("Doe", appRestrictions.getString("th_lastname"))
    }

    /**
     * This tests whether work mdm parameter override application restrictions correctly when
     * `"override"` is set to `true` in the mdm work restrictions.
     * Parameters not available in work mdm must not override application restrictions.
     */
    @Test
    fun testMergeParams_workMdmParametersMustOverrideApplicationRestrictions() {
        // Arrange
        val applicationRestrictions = mockBundle()
        val mdmWorkParameters = JSONObject()

        // Application restrictions that must not be overridden by work mdm parameters:
        applicationRestrictions.putString("th_id_backup", "restriction_id_backup") // #1
        mdmWorkParameters.put("th_id_backup", "work_mdm_id_backup") // #1

        applicationRestrictions.putString("th_id_backup_password", "restriction_id_backup_password") // #2
        mdmWorkParameters.put("th_id_backup_password", "work_mdm_id_backup_password") // #2

        applicationRestrictions.putString("th_safe_password", "restriction_safe_password") // #3
        mdmWorkParameters.put("th_safe_password", "work_mdm_safe_password") // #3

        applicationRestrictions.putString("th_license_password", "restriction_license_username") // #4
        mdmWorkParameters.put("th_license_username", "work_mdm_license_username") // #4

        applicationRestrictions.putString("th_license_username", "restriction_license_username") // #5
        mdmWorkParameters.put("th_license_password", "work_mdm_license_password") // #5

        // Application restrictions that will be overridden by work mdm parameters:
        applicationRestrictions.putBoolean("th_disable_screenshots", false) // #6
        mdmWorkParameters.put("th_disable_screenshots", true) // #6

        applicationRestrictions.putBoolean("th_disable_calls", true) // #7
        mdmWorkParameters.put("th_disable_calls", false) // #7

        applicationRestrictions.putString("th_nickname", "restriction_nickname") // #8
        mdmWorkParameters.put("th_nickname", "work_mdm_nickname") // #8

        applicationRestrictions.putString("th_web_hosts", "restriction_web_hosts") // #9
        mdmWorkParameters.put("th_web_hosts", "work_mdm_web_hosts") // #9

        applicationRestrictions.putInt("th_keep_messages_days", 365) // #10
        mdmWorkParameters.put("th_keep_messages_days", 7) // #10

        // Application restrictions that are not touched by work mdm parameters and must therefore not change:
        applicationRestrictions.putBoolean("th_disable_export", true) // #11
        applicationRestrictions.putString("th_safe_password_message", "restriction_safe_password_message") // #12

        // Work mdm parameters not set by application restrictions:
        mdmWorkParameters.put("th_safe_enable", true) // #13
        mdmWorkParameters.put("th_firstname", "work_mdm_firstname") // #14
        mdmWorkParameters.put("th_lastname", "work_mdm_lastname") // #15
        mdmWorkParameters.put("th_job_title", "work_mdm_job_title") // #16
        mdmWorkParameters.put("th_department", "work_mdm_department") // #17

        val restrictionManagerMock = mockk<RestrictionsManager>()
        every { restrictionManagerMock.applicationRestrictions } returns applicationRestrictions

        val workRestrictionsJson = JSONObject().apply {
            put("override", true)
            put("parameters", mdmWorkParameters)
        }
        val encryptedPreferenceStoreMock = mockEncryptedPreferenceStore(workRestrictionsJson)

        val mockContext = mockk<Context> {
            every { getSystemService(Context.RESTRICTIONS_SERVICE) } returns restrictionManagerMock
            every { getString(R.string.restriction__id_backup) } returns "th_id_backup"
            every { getString(R.string.restriction__id_backup_password) } returns "th_id_backup_password"
            every { getString(R.string.restriction__safe_password) } returns "th_safe_password"
            every { getString(R.string.restriction__license_username) } returns "th_license_username"
            every { getString(R.string.restriction__license_password) } returns "th_license_password"
        }

        val serviceManagerMock = mockk<ServiceManager>(relaxed = true) {
            every { encryptedPreferenceStore } returns encryptedPreferenceStoreMock
            every { context } returns mockContext
        }
        val taskCreatorMock = TaskCreator(serviceManagerMock)
        every { serviceManagerMock.taskCreator } returns taskCreatorMock
        every { serviceManagerMock.multiDeviceManager } returns mockk()
        every { serviceManagerMock.taskManager } returns mockk()

        every { ThreemaApplication.getAppContext() } returns mockContext
        every { ThreemaApplication.getServiceManager() } returns serviceManagerMock

        mockStaticWorkManagerImpl()

        // Act
        val service = AppRestrictionService()
        service.reload()
        val appRestrictions = service.appRestrictions

        // Assert
        verify(exactly = 1) { encryptedPreferenceStoreMock.getJSONObject("wrk_app_restriction") }
        verify(exactly = 7) { mockContext.getString(any()) }

        assertEquals(17, appRestrictions.size().toLong())
        // Application restrictions that must not be overridden by work mdm parameters:
        assertEquals("restriction_id_backup", appRestrictions.getString("th_id_backup"))
        assertEquals("restriction_id_backup_password", appRestrictions.getString("th_id_backup_password"))
        assertEquals("restriction_safe_password", appRestrictions.getString("th_safe_password"))
        assertEquals("restriction_license_username", appRestrictions.getString("th_license_password"))
        assertEquals("restriction_license_username", appRestrictions.getString("th_license_username"))

        // Application restrictions that are overridden by work mdm parameters:
        assertTrue(appRestrictions.getBoolean("th_disable_screenshots"))
        assertFalse(appRestrictions.getBoolean("th_disable_calls"))
        assertEquals("work_mdm_nickname", appRestrictions.getString("th_nickname"))
        assertEquals("work_mdm_web_hosts", appRestrictions.getString("th_web_hosts"))
        assertEquals(7, appRestrictions.getInt("th_keep_messages_days").toLong())

        // Application restrictions that are not set in work mdm and therefore not overridden:
        assertTrue(appRestrictions.getBoolean("th_disable_export"))
        assertEquals("restriction_safe_password_message", appRestrictions.getString("th_safe_password_message"))

        // Work mdm parameters that are not set in application restrictions:
        assertTrue(appRestrictions.getBoolean("th_safe_enable"))
        assertEquals("work_mdm_firstname", appRestrictions.getString("th_firstname"))
        assertEquals("work_mdm_lastname", appRestrictions.getString("th_lastname"))
        assertEquals("work_mdm_job_title", appRestrictions.getString("th_job_title"))
        assertEquals("work_mdm_department", appRestrictions.getString("th_department"))
    }

    private fun mockStaticWorkManagerImpl() {
        val workManagerImplMock = mockk<WorkManagerImpl>(relaxed = true) {
            every { getWorkInfosForUniqueWork("ApplyAppRestrictions") } returns object : AbstractFuture<List<WorkInfo>>() {
                override fun get(): List<WorkInfo> = emptyList()
            }
        }
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns workManagerImplMock
    }

    private fun mockEncryptedPreferenceStore(workRestrictionsJson: JSONObject?): EncryptedPreferenceStore =
        mockk<EncryptedPreferenceStore>(relaxed = true) {
            every { containsKey("wrk_app_restriction") } returns (workRestrictionsJson != null)
            every { getJSONObject("wrk_app_restriction") } returns workRestrictionsJson
        }

    private fun mockBundle(): Bundle {
        val values = mutableMapOf<String, Any>()
        val bundle = mockk<Bundle>()

        every { bundle.containsKey(any()) } answers { values.containsKey(firstArg()) }
        every { bundle.isEmpty } answers { values.isEmpty() }
        every { bundle.size() } answers { values.size }

        every { bundle.putInt(any(), any()) } answers { values[firstArg()] = secondArg() }
        every { bundle.putLong(any(), any()) } answers { values[firstArg()] = secondArg() }
        every { bundle.putDouble(any(), any()) } answers { values[firstArg()] = secondArg() }
        every { bundle.putBoolean(any(), any()) } answers { values[firstArg()] = secondArg() }
        every { bundle.putString(any(), any()) } answers { values[firstArg()] = secondArg() }

        every { bundle.getInt(any()) } answers { values[firstArg()] as? Int? ?: 0 }
        every { bundle.getLong(any()) } answers { values[firstArg()] as? Long? ?: 0L }
        every { bundle.getDouble(any()) } answers { values[firstArg()] as? Double? ?: 0.0 }
        every { bundle.getBoolean(any()) } answers { values[firstArg()] as? Boolean? ?: false }
        every { bundle.getString(any()) } answers { values[firstArg()] as? String }

        return bundle
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun assumeWorkBuild() {
            Assume.assumeTrue(ConfigUtils.isWorkBuild())
        }
    }
}
