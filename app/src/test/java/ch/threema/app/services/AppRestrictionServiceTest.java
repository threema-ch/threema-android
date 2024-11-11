/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.stores.PreferenceStoreInterface;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.api.work.WorkData;
import ch.threema.domain.protocol.api.work.WorkMDMSettings;

@RunWith(PowerMockRunner.class)
public class AppRestrictionServiceTest {

	@BeforeClass
	public static void assumeWorkBuild() {
		Assume.assumeTrue(ConfigUtils.isWorkBuild());
	}

	@Test
	@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
	@PrepareForTest({ThreemaApplication.class, Build.VERSION.class})
	public void testReload_AppRestriction_NoWorkMDM() {
		Whitebox.setInternalState(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.LOLLIPOP_MR1);

		Context mockContext = PowerMockito.mock(Context.class);
		RestrictionsManager restrictionManagerMock = PowerMockito.mock(RestrictionsManager.class);
		AppRestrictionService service = PowerMockito.mock(AppRestrictionService.class);

		// AppRestriction Bundle
		Bundle bundle = PowerMockito.mock(Bundle.class);

		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getAppContext()).thenReturn(mockContext);

		when(mockContext.getSystemService(Context.RESTRICTIONS_SERVICE))
				.thenReturn(restrictionManagerMock);

		when(restrictionManagerMock.getApplicationRestrictions())
				.thenReturn(bundle);

		WorkMDMSettings workMDMSettings = new WorkMDMSettings();
		when(service.getWorkMDMSettings()).thenReturn(workMDMSettings);

		// Partial mock
		doCallRealMethod().when(service).reload();
		doCallRealMethod().when(service).getAppRestrictions();
		service.reload();


		// required calls
		verify(mockContext, times(1)).getSystemService(Context.RESTRICTIONS_SERVICE);

		// no overrides
		verify(bundle, never()).putInt(anyString(), anyInt());
		verify(bundle, never()).putBoolean(anyString(), anyBoolean());
		verify(bundle, never()).putString(anyString(), anyString());
		verify(bundle, never()).putLong(anyString(), anyLong());
		verify(bundle, never()).putDouble(anyString(), anyDouble());

		Bundle loadedRestrictions = service.getAppRestrictions();

		Assert.assertNotNull(loadedRestrictions);
		Assert.assertEquals(bundle, loadedRestrictions);
	}

	@Test
	@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
	@PrepareForTest({ThreemaApplication.class, Build.VERSION.class})
	public void testReload_AppRestriction_WorkMDM_OverrideFalse() {
		Whitebox.setInternalState(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.LOLLIPOP_MR1);
		Context mockContext = PowerMockito.mock(Context.class);
		RestrictionsManager restrictionManagerMock = PowerMockito.mock(RestrictionsManager.class);
		AppRestrictionService service = PowerMockito.mock(AppRestrictionService.class);

		// AppRestriction Bundle
		Bundle bundle = PowerMockito.mock(Bundle.class);

		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getAppContext()).thenReturn(mockContext);

		when(mockContext.getSystemService(Context.RESTRICTIONS_SERVICE))
				.thenReturn(restrictionManagerMock);

		when(restrictionManagerMock.getApplicationRestrictions())
				.thenReturn(bundle);

		when(bundle.containsKey(eq("param1"))).thenReturn(true);
		when(bundle.containsKey(eq("param2"))).thenReturn(false);
		when(bundle.containsKey(eq("param3"))).thenReturn(false);

		WorkMDMSettings workMDMSettings = new WorkMDMSettings();
		workMDMSettings.override = false;
		// should not be written
		workMDMSettings.parameters.put("param1", "work-param-1");
		// should be written
		workMDMSettings.parameters.put("param2", 22);
		// should be written
		workMDMSettings.parameters.put("param3", true);

		when(service.getWorkMDMSettings()).thenReturn(workMDMSettings);

		// Partial mock
		doCallRealMethod().when(service).reload();
		doCallRealMethod().when(service).getAppRestrictions();

		service.reload();

		// required calls
		verify(mockContext, times(1)).getSystemService(Context.RESTRICTIONS_SERVICE);

		// no overrides
		verify(bundle, times(1)).putInt(eq("param2"), eq(22));
		verify(bundle, times(1)).putBoolean(eq("param3"), eq(true));
		verify(bundle, never()).putString(anyString(), anyString());
		verify(bundle, never()).putLong(anyString(), anyLong());
		verify(bundle, never()).putDouble(anyString(), anyDouble());

		Bundle loadedRestrictions = service.getAppRestrictions();

		Assert.assertNotNull(loadedRestrictions);
		Assert.assertEquals(bundle, loadedRestrictions);
	}

	@Test
	@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
	@PrepareForTest({ThreemaApplication.class, Build.VERSION.class})
	public void testReload_AppRestriction_WorkMDM_OverrideTrue() {
		Whitebox.setInternalState(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.LOLLIPOP_MR1);
		Context mockContext = PowerMockito.mock(Context.class);
		RestrictionsManager restrictionManagerMock = PowerMockito.mock(RestrictionsManager.class);
		AppRestrictionService service = PowerMockito.mock(AppRestrictionService.class);

		// AppRestriction Bundle
		Bundle bundle = PowerMockito.mock(Bundle.class);

		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getAppContext()).thenReturn(mockContext);

		when(mockContext.getSystemService(Context.RESTRICTIONS_SERVICE))
				.thenReturn(restrictionManagerMock);

		when(restrictionManagerMock.getApplicationRestrictions())
				.thenReturn(bundle);

		when(bundle.containsKey(eq("param1"))).thenReturn(true);
		when(bundle.containsKey(eq("param2"))).thenReturn(false);
		when(bundle.containsKey(eq("param3"))).thenReturn(false);

		WorkMDMSettings workMDMSettings = new WorkMDMSettings();
		workMDMSettings.override = true;
		// should not be written
		workMDMSettings.parameters.put("param1", "work-param-1");
		// should be written
		workMDMSettings.parameters.put("param2", 22);
		// should be written
		workMDMSettings.parameters.put("param3", true);

		when(service.getWorkMDMSettings()).thenReturn(workMDMSettings);

		// Partial mock
		doCallRealMethod().when(service).reload();
		doCallRealMethod().when(service).getAppRestrictions();

		service.reload();

		// required calls
		verify(mockContext, times(1)).getSystemService(Context.RESTRICTIONS_SERVICE);

		// no overrides
		verify(bundle, times(1)).putInt(eq("param2"), eq(22));
		verify(bundle, times(1)).putBoolean(eq("param3"), eq(true));
		verify(bundle, times(1)).putString(eq("param1"), eq("work-param-1"));
		verify(bundle, never()).putLong(anyString(), anyLong());
		verify(bundle, never()).putDouble(anyString(), anyDouble());

		Bundle loadedRestrictions = service.getAppRestrictions();

		Assert.assertNotNull(loadedRestrictions);
		Assert.assertEquals(bundle, loadedRestrictions);
	}

	@Test
	public void testConvert_Json() throws JSONException {
		AppRestrictionService service = PowerMockito.mock(AppRestrictionService.class);
		doCallRealMethod().when(service).convertJSONToWorkMDM(any(JSONObject.class));

		JSONObject json = PowerMockito.mock(JSONObject.class);
		when(json.has("override")).thenReturn(true);
		when(json.getBoolean("override")).thenReturn(true);
		when(json.has("parameters")).thenReturn(true);

		JSONObject parameters = PowerMockito.mock(JSONObject.class);
		when(json.getJSONObject("parameters")).thenReturn(parameters);

		Iterator<String> keys = PowerMockito.mock(Iterator.class);
		when(keys.hasNext()).thenReturn(true,true,false);
		when(keys.next()).thenReturn("param1", "param2");
		when(parameters.keys()).thenReturn(keys);
		when(parameters.get(eq("param1"))).thenReturn("param1-value");
		when(parameters.get(eq("param2"))).thenReturn("param2-value");

		WorkMDMSettings result = service.convertJSONToWorkMDM(json);
		Assert.assertNotNull(result);
		Assert.assertTrue(result.override);
		Assert.assertNotNull(result.parameters);
		Assert.assertEquals(2, result.parameters.size());
		Assert.assertTrue(result.parameters.containsKey("param1"));
		Assert.assertEquals("param1-value", result.parameters.get("param1"));
		Assert.assertTrue(result.parameters.containsKey("param2"));
		Assert.assertEquals("param2-value", result.parameters.get("param2"));
	}

	@Test
	public void testConvert_WorkMDMSettings() throws JSONException {
		AppRestrictionService service = PowerMockito.mock(AppRestrictionService.class);
		doCallRealMethod().when(service).convertWorkMDMToJSON(any(WorkMDMSettings.class));

		WorkMDMSettings s = new WorkMDMSettings();
		s.override = true;
		s.parameters.put("param1", "param1-value");
		s.parameters.put("param2", "param2-value");

		JSONObject r = service.convertWorkMDMToJSON(s);

		Assert.assertNotNull(r);
		Assert.assertTrue(r.getBoolean("override"));
		Assert.assertTrue(r.has("parameters"));
		Assert.assertEquals("param1-value", (r.getJSONObject("parameters").getString("param1")));
		Assert.assertEquals("param2-value", (r.getJSONObject("parameters").getString("param2")));
	}

	@Test
	@PrepareForTest(ThreemaApplication.class)
	public void testStoreWorkMDMSettings() {
		AppRestrictionService service = PowerMockito.mock(AppRestrictionService.class);
		doCallRealMethod().when(service).storeWorkMDMSettings(any(WorkMDMSettings.class));

		Context mockContext = PowerMockito.mock(Context.class);
		when(mockContext.getString(anyInt())).thenReturn("");

		ServiceManager serviceManagerMock = PowerMockito.mock(ServiceManager.class);
		PreferenceStoreInterface preferenceStoreMock = mockPreferenceStore(null);
		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getServiceManager()).thenReturn(serviceManagerMock);
		when(ThreemaApplication.getAppContext()).thenReturn(mockContext);
		when(serviceManagerMock.getPreferenceStore()).thenReturn(preferenceStoreMock);

		JSONObject jsonObject = new JSONObject();
		WorkMDMSettings s = new WorkMDMSettings();
		s.override = true;
		s.parameters.put("param1", "param1-value");
		s.parameters.put("param2", "param2-value");

		when(service.convertWorkMDMToJSON(eq(s))).thenReturn(jsonObject);
		service.storeWorkMDMSettings(s);

		// Check if the store method is called
		verify(preferenceStoreMock, times(1)).save(eq("wrk_app_restriction"), eq(jsonObject), eq(true));
		verify(service, times(1)).reload();
	}

	@Test
	@PrepareForTest(ThreemaApplication.class)
	public void testGetWorkMDMSettings() {
		AppRestrictionService service = PowerMockito.mock(AppRestrictionService.class);
		doCallRealMethod().when(service).getWorkMDMSettings();

		Context mockContext = PowerMockito.mock(Context.class);
		when(mockContext.getString(anyInt())).thenReturn("");

		ServiceManager serviceManagerMock = PowerMockito.mock(ServiceManager.class);
		JSONObject workRestrictionsJson = new JSONObject();
		PreferenceStoreInterface preferenceStoreMock = mockPreferenceStore(workRestrictionsJson);
		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getServiceManager()).thenReturn(serviceManagerMock);
		when(ThreemaApplication.getAppContext()).thenReturn(mockContext);
		when(serviceManagerMock.getPreferenceStore()).thenReturn(preferenceStoreMock);

		WorkMDMSettings s = new WorkMDMSettings();

		when(service.convertJSONToWorkMDM(eq(workRestrictionsJson))).thenReturn(s);
		Assert.assertEquals(s, service.getWorkMDMSettings());
		Assert.assertEquals(s, service.getWorkMDMSettings());
		Assert.assertEquals(s, service.getWorkMDMSettings());
		Assert.assertEquals(s, service.getWorkMDMSettings());

		// Check if the store method is called
		verify(preferenceStoreMock, times(1)).getJSONObject(eq("wrk_app_restriction"), eq(true));
	}

	@Test
	@PrepareForTest(RuntimeUtil.class)
	public void testFetchAndStoreWorkMDMSettings() throws Exception {
		AppRestrictionService service = PowerMockito.mock(AppRestrictionService.class);
		doCallRealMethod().when(service).fetchAndStoreWorkMDMSettings(any(APIConnector.class), any(UserCredentials.class));

		PowerMockito.mockStatic(RuntimeUtil.class);
		when(RuntimeUtil.isOnUiThread()).thenReturn(false);

		APIConnector apiConnectorMock = PowerMockito.mock(APIConnector.class);
		UserCredentials credentials = new UserCredentials("hans", "dampf");

		WorkData workData = new WorkData();
		when(apiConnectorMock.fetchWorkData(eq("hans"), eq("dampf"), any(String[].class)))
				.thenReturn(workData);
		service.fetchAndStoreWorkMDMSettings(apiConnectorMock, credentials);

		verify(apiConnectorMock, times(1)).fetchWorkData(eq("hans"), eq("dampf"), any(String[].class));
		verify(service, times(1)).storeWorkMDMSettings(eq(workData.mdm));
	}

	@Test
	@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
	@PrepareForTest({ThreemaApplication.class, Build.VERSION.class})
	public void testFilterNonWorkMdmParametersFromPreferences() throws Exception {
		// arrange
		Whitebox.setInternalState(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.LOLLIPOP_MR1);

		Bundle bundleMock = mockBundle();

		RestrictionsManager restrictionManagerMock = PowerMockito.mock(RestrictionsManager.class);
		when(restrictionManagerMock.getApplicationRestrictions()).thenReturn(bundleMock);

		Context mockContext = PowerMockito.mock(Context.class);
		when(mockContext.getSystemService(Context.RESTRICTIONS_SERVICE)).thenReturn(restrictionManagerMock);
		when(mockContext.getString(anyInt())).thenReturn(
			"th_id_backup",
			"th_id_backup_password",
			"th_safe_password",
			"th_license_username",
			"th_license_password"
		);

		JSONObject parameters = new JSONObject();
		parameters.put("th_id_backup", "ABCD1234");
		parameters.put("th_id_backup_password", "T0p$ecr3t");
		parameters.put("th_safe_password", "T0p$ecr3t");
		parameters.put("th_license_username", "<username>");
		parameters.put("th_license_password", "T0p$ecr3t");
		parameters.put("th_safe_enable", true);
		parameters.put("th_firstname", "John");
		parameters.put("th_lastname", "Doe");
		JSONObject workRestrictionsJson = new JSONObject();
		workRestrictionsJson.put("override", true);
		workRestrictionsJson.put("parameters", parameters);
		PreferenceStoreInterface preferenceStoreMock = mockPreferenceStore(workRestrictionsJson);

		ServiceManager serviceManagerMock = PowerMockito.mock(ServiceManager.class);
		when(serviceManagerMock.getPreferenceStore()).thenReturn(preferenceStoreMock);

		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getAppContext()).thenReturn(mockContext);
		when(ThreemaApplication.getServiceManager()).thenReturn(serviceManagerMock);

		// Act
		AppRestrictionService service = new AppRestrictionService();
		WorkMDMSettings settings = service.getWorkMDMSettings();
		service.reload();
		Bundle appRestrictions = service.getAppRestrictions();

		// Assert
		verify(preferenceStoreMock, times(1)).getJSONObject(eq("wrk_app_restriction"), eq(true));
		verify(mockContext, times(5)).getString(anyInt());

		Assert.assertTrue(settings.override);
		Assert.assertEquals(3, settings.parameters.size());
		Assert.assertEquals(true, settings.parameters.get("th_safe_enable"));
		Assert.assertEquals("John", settings.parameters.get("th_firstname"));
		Assert.assertEquals("Doe", settings.parameters.get("th_lastname"));

		Assert.assertEquals(3, appRestrictions.size());
		Assert.assertTrue(appRestrictions.getBoolean("th_safe_enable"));
		Assert.assertEquals("John", appRestrictions.getString("th_firstname"));
		Assert.assertEquals("Doe", appRestrictions.getString("th_lastname"));
	}

	/**
	 * This tests whether work mdm parameter override application restrictions correctly when
	 * `"override"` is set to `true` in the mdm work restrictions.
	 * Parameters not available in work mdm must not override application restrictions.
	 */
	@Test
	@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
	@PrepareForTest({ThreemaApplication.class, Build.VERSION.class})
	public void testMergeParams_workMdmParametersMustOverrideApplicationRestrictions() throws Exception {
		// Arrange
		Whitebox.setInternalState(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.LOLLIPOP_MR1);

		Bundle applicationRestrictions = mockBundle();
		JSONObject mdmWorkParameters = new JSONObject();

		// Application restrictions that must not be overridden by work mdm parameters:
		applicationRestrictions.putString("th_id_backup", "restriction_id_backup"); // #1
		mdmWorkParameters.put("th_id_backup", "work_mdm_id_backup"); // #1

		applicationRestrictions.putString("th_id_backup_password", "restriction_id_backup_password"); // #2
		mdmWorkParameters.put("th_id_backup_password", "work_mdm_id_backup_password"); // #2

		applicationRestrictions.putString("th_safe_password", "restriction_safe_password"); // #3
		mdmWorkParameters.put("th_safe_password", "work_mdm_safe_password"); // #3

		applicationRestrictions.putString("th_license_password", "restriction_license_username"); // #4
		mdmWorkParameters.put("th_license_username", "work_mdm_license_username"); // #4

		applicationRestrictions.putString("th_license_username", "restriction_license_username"); // #5
		mdmWorkParameters.put("th_license_password", "work_mdm_license_password"); // #5

		// Application restrictions that will be overridden by work mdm parameters:
		applicationRestrictions.putBoolean("th_disable_screenshots", false); // #6
		mdmWorkParameters.put("th_disable_screenshots", true); // #6

		applicationRestrictions.putBoolean("th_disable_calls", true); // #7
		mdmWorkParameters.put("th_disable_calls", false); // #7

		applicationRestrictions.putString("th_nickname", "restriction_nickname"); // #8
		mdmWorkParameters.put("th_nickname", "work_mdm_nickname"); // #8

		applicationRestrictions.putString("th_web_hosts", "restriction_web_hosts"); // #9
		mdmWorkParameters.put("th_web_hosts", "work_mdm_web_hosts"); // #9

		applicationRestrictions.putInt("th_keep_messages_days", 365); // #10
		mdmWorkParameters.put("th_keep_messages_days", 7); // #10

		// Application restrictions that are not touched by work mdm parameters and must therefore not change:
		applicationRestrictions.putBoolean("th_disable_export", true); // #11
		applicationRestrictions.putString("th_safe_password_message", "restriction_safe_password_message"); // #12

		// Work mdm parameters not set by application restrictions:
		mdmWorkParameters.put("th_safe_enable", true);  // #13
		mdmWorkParameters.put("th_firstname", "work_mdm_firstname"); // #14
		mdmWorkParameters.put("th_lastname", "work_mdm_lastname"); // #15
        mdmWorkParameters.put("th_job_title", "work_mdm_job_title"); // #16
        mdmWorkParameters.put("th_department", "work_mdm_department"); // #17

        RestrictionsManager restrictionManagerMock = PowerMockito.mock(RestrictionsManager.class);
		when(restrictionManagerMock.getApplicationRestrictions()).thenReturn(applicationRestrictions);

		JSONObject workRestrictionsJson = new JSONObject();
		workRestrictionsJson.put("override", true);
		workRestrictionsJson.put("parameters", mdmWorkParameters);
		PreferenceStoreInterface preferenceStoreMock = mockPreferenceStore(workRestrictionsJson);

		Context mockContext = PowerMockito.mock(Context.class);
		when(mockContext.getSystemService(Context.RESTRICTIONS_SERVICE)).thenReturn(restrictionManagerMock);
		when(mockContext.getString(R.string.restriction__id_backup)).thenReturn("th_id_backup");
		when(mockContext.getString(R.string.restriction__id_backup_password)).thenReturn("th_id_backup_password");
		when(mockContext.getString(R.string.restriction__safe_password)).thenReturn("th_safe_password");
		when(mockContext.getString(R.string.restriction__license_username)).thenReturn("th_license_username");
		when(mockContext.getString(R.string.restriction__license_password)).thenReturn("th_license_password");

		ServiceManager serviceManagerMock = PowerMockito.mock(ServiceManager.class);
		when(serviceManagerMock.getPreferenceStore()).thenReturn(preferenceStoreMock);
		when(serviceManagerMock.getContext()).thenReturn(mockContext);

		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getAppContext()).thenReturn(mockContext);
		when(ThreemaApplication.getServiceManager()).thenReturn(serviceManagerMock);

		// Act
		AppRestrictionService service = new AppRestrictionService();
		service.reload();
		Bundle appRestrictions = service.getAppRestrictions();

		// Assert
		verify(preferenceStoreMock, times(1)).getJSONObject(eq("wrk_app_restriction"), eq(true));
		verify(mockContext, times(7)).getString(anyInt());

		Assert.assertEquals(17, appRestrictions.size());
		// Application restrictions that must not be overridden by work mdm parameters:
		Assert.assertEquals("restriction_id_backup", appRestrictions.getString("th_id_backup"));
		Assert.assertEquals("restriction_id_backup_password", appRestrictions.getString("th_id_backup_password"));
		Assert.assertEquals("restriction_safe_password", appRestrictions.getString("th_safe_password"));
		Assert.assertEquals("restriction_license_username", appRestrictions.getString("th_license_password"));
		Assert.assertEquals("restriction_license_username", appRestrictions.getString("th_license_username"));

		// Application restrictions that are overridden by work mdm parameters:
		Assert.assertTrue(appRestrictions.getBoolean("th_disable_screenshots"));
		Assert.assertFalse(appRestrictions.getBoolean("th_disable_calls"));
		Assert.assertEquals("work_mdm_nickname", appRestrictions.getString("th_nickname"));
		Assert.assertEquals("work_mdm_web_hosts", appRestrictions.getString("th_web_hosts"));
		Assert.assertEquals(7, appRestrictions.getInt("th_keep_messages_days"));

		// Application restrictions that are not set in work mdm and therefore not overridden:
		Assert.assertTrue(appRestrictions.getBoolean("th_disable_export"));
		Assert.assertEquals("restriction_safe_password_message", appRestrictions.getString("th_safe_password_message"));

		// Work mdm parameters that are not set in application restrictions:
		Assert.assertTrue(appRestrictions.getBoolean("th_safe_enable"));
		Assert.assertEquals("work_mdm_firstname", appRestrictions.getString("th_firstname"));
		Assert.assertEquals("work_mdm_lastname", appRestrictions.getString("th_lastname"));
		Assert.assertEquals("work_mdm_job_title", appRestrictions.getString("th_job_title"));
		Assert.assertEquals("work_mdm_department", appRestrictions.getString("th_department"));
	}

	private PreferenceStoreInterface mockPreferenceStore(@Nullable JSONObject workRestrictionsJson) {
		PreferenceStoreInterface preferenceStoreMock = PowerMockito.mock(PreferenceStoreInterface.class);
		when(preferenceStoreMock.containsKey("wrk_app_restriction", true)).thenReturn(workRestrictionsJson != null);
		when(preferenceStoreMock.getJSONObject(eq("wrk_app_restriction"), eq(true))).thenReturn(workRestrictionsJson);
		return preferenceStoreMock;
	}

	private Bundle mockBundle() {
		Map<String, Object> values = new HashMap<>();
		Bundle bundle = Mockito.mock(Bundle.class);

		when(bundle.containsKey(anyString())).thenAnswer(invocation -> values.containsKey(invocation.getArgument(0, String.class)));
		when(bundle.isEmpty()).thenAnswer(ignored -> values.isEmpty());
		when(bundle.size()).thenAnswer(ignored -> values.size());

		doAnswer(invocation -> {
			String key = invocation.getArgument(0);
			int value = invocation.getArgument(1);
			values.put(key, value);
			return null;
		}).when(bundle).putInt(anyString(), anyInt());
		when(bundle.getInt(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0, String.class)));

		doAnswer(invocation -> {
			String key = invocation.getArgument(0);
			boolean value = invocation.getArgument(1);
			values.put(key, value);
			return null;
		}).when(bundle).putBoolean(anyString(), anyBoolean());
		when(bundle.getBoolean(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0, String.class)));

		doAnswer(invocation -> {
			String key = invocation.getArgument(0);
			String value = invocation.getArgument(1);
			values.put(key, value);
			return null;
		}).when(bundle).putString(anyString(), anyString());
		when(bundle.getString(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0, String.class)));

		doAnswer(invocation -> {
			String key = invocation.getArgument(0);
			long value = invocation.getArgument(1);
			values.put(key, value);
			return null;
		}).when(bundle).putLong(anyString(), anyLong());
		when(bundle.getLong(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0, String.class)));

		doAnswer(invocation -> {
			String key = invocation.getArgument(0);
			double value = invocation.getArgument(1);
			values.put(key, value);
			return null;
		}).when(bundle).putDouble(anyString(), anyDouble());
		when(bundle.getDouble(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0, String.class)));

		return bundle;
	}
}
