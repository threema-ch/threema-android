/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2020 Threema GmbH
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

package ch.threema.app.service;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.Iterator;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.AppRestrictionService;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.stores.PreferenceStoreInterface;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.client.APIConnector;
import ch.threema.client.work.WorkData;
import ch.threema.client.work.WorkMDMSettings;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
public class AppRestrictionServiceTest {

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
		doCallRealMethod().when(service).convert(any(JSONObject.class));

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

		WorkMDMSettings result = service.convert(json);
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
		doCallRealMethod().when(service).convert(any(WorkMDMSettings.class));

		WorkMDMSettings s = new WorkMDMSettings();
		s.override = true;
		s.parameters.put("param1", "param1-value");
		s.parameters.put("param2", "param2-value");

		JSONObject r = service.convert(s);

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

		ServiceManager serviceManagerMock = PowerMockito.mock(ServiceManager.class);
		PreferenceStoreInterface preferenceStoreMock = PowerMockito.mock(PreferenceStoreInterface.class);
		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getServiceManager()).thenReturn(serviceManagerMock);
		when(serviceManagerMock.getPreferenceStore()).thenReturn(preferenceStoreMock);


		JSONObject jsonObject = new JSONObject();
		WorkMDMSettings s = new WorkMDMSettings();
		s.override = true;
		s.parameters.put("param1", "param1-value");
		s.parameters.put("param2", "param2-value");

		when(service.convert(eq(s))).thenReturn(jsonObject);
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

		ServiceManager serviceManagerMock = PowerMockito.mock(ServiceManager.class);
		PreferenceStoreInterface preferenceStoreMock = PowerMockito.mock(PreferenceStoreInterface.class);
		PowerMockito.mockStatic(ThreemaApplication.class);
		when(ThreemaApplication.getServiceManager()).thenReturn(serviceManagerMock);
		when(serviceManagerMock.getPreferenceStore()).thenReturn(preferenceStoreMock);

		JSONObject jsonObject = new JSONObject();
		when(preferenceStoreMock.getJSONObject(eq("wrk_app_restriction"), eq(true)))
				.thenReturn(jsonObject);

		WorkMDMSettings s = new WorkMDMSettings();

		when(service.convert(eq(jsonObject))).thenReturn(s);
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
}
