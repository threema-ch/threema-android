/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.app.threemasafe;

import android.content.Context;

import org.apache.commons.lang3.LocaleUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import ch.threema.app.BuildConfig;
import ch.threema.app.managers.CoreServiceManager;
import ch.threema.app.services.ApiService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.IdListService;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.stores.PreferenceStoreInterface;
import ch.threema.base.utils.JSONUtil;
import ch.threema.base.utils.Utils;
import ch.threema.data.ModelTypeCache;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.storage.DatabaseBackend;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

import static ch.threema.testhelpers.TestHelpersKt.nonSecureRandomArray;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.crypto.*")
public class ThreemaSafeServiceTest {
	@Mock
	private	Context contextMock;
	@Mock
	private	PreferenceService preferenceServiceMock;
	@Mock
	private	UserService userServiceMock;
	@Mock
	private	ContactService contactServiceMock;
	@Mock
	private GroupService groupServiceMock;
	@Mock
	private DistributionListService distributionListServiceMock;
	@Mock
	private	LocaleService localeServiceMock;
	@Mock
	private	FileService fileServiceMock;
	@Mock
	private	DatabaseServiceNew databaseServiceNewMock;
	@Mock
	private	IdentityStore identityStoreMock;
	@Mock
	private ApiService apiService;
	@Mock
	private	APIConnector apiConnectorMock;
	@Mock
	private IdListService profilePicRecipientsServiceMock;
	@Mock
	private IdListService blockedContactsServiceMock;
	@Mock
	private IdListService excludedSyncIdentitiesServiceMock;
	@Mock
	private DeadlineListService hiddenContactsListMock;
	@Mock
	private ServerAddressProvider serverAddressProviderMock;
	@Mock
	private PreferenceStoreInterface preferenceStoreMock;
	private final DatabaseBackend databaseBackendMock = mock(DatabaseBackend.class);
	private final CoreServiceManager coreServiceManagerMock = mock(CoreServiceManager.class);
	private final ContactModelRepository contactModelRepository = new ContactModelRepository(
		new ModelTypeCache<>(), databaseBackendMock, coreServiceManagerMock
	);

    // Test vector: Password "shootdeathstar" and salt "ECHOECHO" should result in this master key
    private static final String MASTER_KEY_HEX = "066384d3695fbbd9f31a7d533900fd0cd8d1373beb6a28678522d2a49980c9c351c3d8d752fb6e1fd3199ead7f0895d6e3893ff691f2a5ee1976ed0897fc2f66";

    // Test data
    private static final byte[] TEST_PRIVATE_KEY_BYTES = {
        1, 2, 3, 4, 5, 6, 7, 8,
        100, 101, 102, 103, 104, 105, 106, 107,
        1, 2, 3, 4, 5, 6, 7, 8,
        100, 101, 102, 103, 104, 105, 106, 107,
    };
    private static final String TEST_PRIVATE_KEY_BASE64 = "AQIDBAUGBwhkZWZnaGlqawECAwQFBgcIZGVmZ2hpams=";
    private Date testDate1, testDate2;
    private long testDate1Timestamp, testDate2Timestamp;

	private ThreemaSafeServiceImpl getServiceImpl() {
		return new ThreemaSafeServiceImpl(
			contextMock, preferenceServiceMock, userServiceMock,
			contactServiceMock, groupServiceMock, distributionListServiceMock,
			localeServiceMock, fileServiceMock,
			blockedContactsServiceMock, excludedSyncIdentitiesServiceMock, profilePicRecipientsServiceMock,
			databaseServiceNewMock, identityStoreMock, apiService, apiConnectorMock,
			hiddenContactsListMock, serverAddressProviderMock, preferenceStoreMock,
			contactModelRepository
		);
	}

    private ThreemaSafeService getService() {
        return getServiceImpl();
    }

    @Before
    public void prepareMocks() {
        Locale.setDefault(LocaleUtils.toLocale("de_CH"));// Private key
        Mockito.when(identityStoreMock.getPrivateKey()).thenReturn(TEST_PRIVATE_KEY_BYTES);

        // Identity lists
        Mockito.when(blockedContactsServiceMock.getAll()).thenReturn(new String[]{});
        Mockito.when(excludedSyncIdentitiesServiceMock.getAll()).thenReturn(new String[]{});
    }

    @Before
    public void prepareTestDates() {
        // For these tests, we assume a timezone of Europe/Zurich (CET/CEST).
        final TimeZone testTimeZone = TimeZone.getTimeZone("Europe/Zurich");
        TimeZone.setDefault(testTimeZone);
        Locale.setDefault(LocaleUtils.toLocale("de_CH"));

        Locale.setDefault(LocaleUtils.toLocale("de_CH"));testDate1 = new Date(2020 - 1900, 11, 1, 13, 14, 15);
        testDate2 = new Date(2021 - 1900, 11, 1, 13, 14, 15);
        testDate1Timestamp = 1606824855000L;
        testDate2Timestamp = 1638360855000L;
    }

    @Test
    public void testDeriveMasterKey() {
        final ThreemaSafeService service = PowerMockito.mock(ThreemaSafeServiceImpl.class);
        doCallRealMethod().when(service).deriveMasterKey(any(String.class), any(String.class));

        // Test case as defined in specification (see confluence)
        final byte[] masterKey = service.deriveMasterKey("shootdeathstar", "ECHOECHO");
        final String masterKeyHex = Utils.byteArrayToHexString(masterKey).toLowerCase();
        Assert.assertEquals(
            MASTER_KEY_HEX,
            masterKeyHex
        );
    }

    @Test
    public void testGetThreemaSafeBackupIdNull() {
        final ThreemaSafeService service = getService();

        when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(null);
        final byte[] backupId1 = service.getThreemaSafeBackupId();
        Assert.assertNull(backupId1);

        when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(new byte[0]);
        final byte[] backupId2 = service.getThreemaSafeBackupId();
        Assert.assertNull(backupId2);
    }

    @Test
    public void testGetThreemaSafeBackupId() {
        final ThreemaSafeService service = getService();
        when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(Utils.hexStringToByteArray(MASTER_KEY_HEX));
        final byte[] backupId = service.getThreemaSafeBackupId();
        final String backupIdHex = Utils.byteArrayToHexString(backupId).toLowerCase();
        Assert.assertEquals(
            "066384d3695fbbd9f31a7d533900fd0cd8d1373beb6a28678522d2a49980c9c3",
            backupIdHex
        );
    }

    @Test
    public void testGetThreemaSafeEncryptionKeyNull() {
        final ThreemaSafeService service = getService();

        when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(null);
        final byte[] encryptionKey1 = service.getThreemaSafeEncryptionKey();
        Assert.assertNull(encryptionKey1);

        when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(new byte[0]);
        final byte[] encryptionKey2 = service.getThreemaSafeEncryptionKey();
        Assert.assertNull(encryptionKey2);
    }

    @Test
    public void testGetThreemaSafeEncryptionKey() {
        final ThreemaSafeService service = getService();
        when(preferenceServiceMock.getThreemaSafeMasterKey()).thenReturn(Utils.hexStringToByteArray(MASTER_KEY_HEX));
        final byte[] encryptionKey = service.getThreemaSafeEncryptionKey();
        final String encryptionKeyHex = Utils.byteArrayToHexString(encryptionKey).toLowerCase();
        Assert.assertEquals(
            "51c3d8d752fb6e1fd3199ead7f0895d6e3893ff691f2a5ee1976ed0897fc2f66",
            encryptionKeyHex
        );
    }

    private JSONObject getParsedSafeJson(ThreemaSafeServiceImpl service) throws JSONException {
        final String jsonString = service.getSafeJson();
        Assert.assertNotNull(jsonString);
        return new JSONObject(jsonString);
    }

    @Test
    public void testSafeJsonInfo() throws Exception {
        final JSONObject parsed = getParsedSafeJson(getServiceImpl());
        final JSONObject info = parsed.getJSONObject("info");
        Assert.assertEquals(
            1,
            info.getInt("version")
        );
        Assert.assertEquals(
            BuildConfig.VERSION_NAME + "A/de_CH",
            info.getString("device")
        );
    }

    @Test
    public void testSafeJsonUser() throws Exception {
        final JSONObject parsed = getParsedSafeJson(getServiceImpl());
        final JSONObject user = parsed.getJSONObject("user");
        Assert.assertEquals(
            TEST_PRIVATE_KEY_BASE64,
            user.getString("privatekey")
        );
        final JSONArray links = user.getJSONArray("links");
        Assert.assertEquals(0, links.length());
    }

    @Test
    public void testSafeJsonContacts() throws Exception {
        // Set up mocks
        when(contactServiceMock.find(null)).thenReturn(Arrays.asList(
            new ContactModel("HELLO123", nonSecureRandomArray(32)).setLastUpdate(null),
            new ContactModel("HELLO234", nonSecureRandomArray(32)).setLastUpdate(testDate1),
            new ContactModel("HELLO345", nonSecureRandomArray(32)).setLastUpdate(testDate2)
        ));

        // Generate and parse JSON
        final JSONObject parsed = getParsedSafeJson(getServiceImpl());
        final JSONArray contacts = parsed.getJSONArray("contacts");
        Assert.assertEquals(
            3,
            contacts.length()
        );

        // Get contacts
        final JSONObject contact1 = contacts.getJSONObject(0);
        final JSONObject contact2 = contacts.getJSONObject(1);
        final JSONObject contact3 = contacts.getJSONObject(2);

        // Validate identity
        Assert.assertEquals("HELLO123", contact1.getString("identity"));
        Assert.assertEquals("HELLO234", contact2.getString("identity"));
        Assert.assertEquals("HELLO345", contact3.getString("identity"));

        // lastUpdate should be set correctly
        Assert.assertFalse(contact1.has("lastUpdate"));
        Assert.assertEquals(testDate1Timestamp, contact2.getLong("lastUpdate"));
        Assert.assertEquals(testDate2Timestamp, contact3.getLong("lastUpdate"));
    }

    @Test
    public void testSafeJsonGroups() throws Exception {
        // Set up mocks
        final GroupModel model1 = new GroupModel().setApiGroupId(new GroupId(1L)).setCreatorIdentity("GROUPER1").setLastUpdate(null);
        final GroupModel model2 = new GroupModel().setApiGroupId(new GroupId(2L)).setCreatorIdentity("GROUPER2").setLastUpdate(testDate1);
        final GroupModel model3 = new GroupModel().setApiGroupId(new GroupId(3L)).setCreatorIdentity("GROUPER3").setLastUpdate(testDate2);
        when(groupServiceMock.getAll(any())).thenReturn(Arrays.asList(model1, model2, model3));
        when(groupServiceMock.getGroupIdentities(model1)).thenReturn(new String[]{"GROUPER1", "MEMBER01"});
        when(groupServiceMock.getGroupIdentities(model2)).thenReturn(new String[]{"GROUPER2"});
        when(groupServiceMock.getGroupIdentities(model3)).thenReturn(new String[]{"GROUPER3", "MEMBER01", "MEMBER02"});

        // Generate and parse JSON
        final JSONObject parsed = getParsedSafeJson(getServiceImpl());
        final JSONArray groups = parsed.getJSONArray("groups");
        Assert.assertEquals(
            3,
            groups.length()
        );

        // Get groups
        final JSONObject group1 = groups.getJSONObject(0);
        final JSONObject group2 = groups.getJSONObject(1);
        final JSONObject group3 = groups.getJSONObject(2);

        // Validate group ID
        Assert.assertEquals("0100000000000000", group1.getString("id"));
        Assert.assertEquals("0200000000000000", group2.getString("id"));
        Assert.assertEquals("0300000000000000", group3.getString("id"));

        // Validate creator
        Assert.assertEquals("GROUPER1", group1.getString("creator"));
        Assert.assertEquals("GROUPER2", group2.getString("creator"));
        Assert.assertEquals("GROUPER3", group3.getString("creator"));

        // lastUpdate should be set correctly
        // Note: Groups always have a "lastUpdate" timestamp! For compatibility reasons,
        // if no date was set, then timestamp 0 is returned.
        Assert.assertEquals(0L, group1.getLong("lastUpdate"));
        Assert.assertEquals(testDate1Timestamp, group2.getLong("lastUpdate"));
        Assert.assertEquals(testDate2Timestamp, group3.getLong("lastUpdate"));
    }

    @Test
    public void testSafeJsonDistributionLists() throws Exception {
        // Set up mocks
        final DistributionListModel model1 = new DistributionListModel().setId(1L).setLastUpdate(null);
        final DistributionListModel model2 = new DistributionListModel().setId(2L).setLastUpdate(testDate1);
        final DistributionListModel model3 = new DistributionListModel().setId(3L).setLastUpdate(testDate2);
        when(distributionListServiceMock.getAll(any())).thenReturn(Arrays.asList(model1, model2, model3));
        when(distributionListServiceMock.getDistributionListIdentities(model1)).thenReturn(new String[]{"MEMBER11"});
        when(distributionListServiceMock.getDistributionListIdentities(model2)).thenReturn(new String[]{"MEMBER21"});
        when(distributionListServiceMock.getDistributionListIdentities(model3)).thenReturn(new String[]{"MEMBER31", "MEMBER32", "MEMBER33"});

        // Generate and parse JSON
        final JSONObject parsed = getParsedSafeJson(getServiceImpl());
        final JSONArray distributionLists = parsed.getJSONArray("distributionlists");
        Assert.assertEquals(
            3,
            distributionLists.length()
        );

        // Get distribution lists
        final JSONObject distributionList1 = distributionLists.getJSONObject(0);
        final JSONObject distributionList2 = distributionLists.getJSONObject(1);
        final JSONObject distributionList3 = distributionLists.getJSONObject(2);

        // Validate distribution list ID
        Assert.assertEquals("0100000000000000", distributionList1.getString("id"));
        Assert.assertEquals("0200000000000000", distributionList2.getString("id"));
        Assert.assertEquals("0300000000000000", distributionList3.getString("id"));

        // Validate members
        Assert.assertArrayEquals(new String[]{"MEMBER11"}, JSONUtil.getStringArray(distributionList1.getJSONArray("members")));
        Assert.assertArrayEquals(new String[]{"MEMBER21"}, JSONUtil.getStringArray(distributionList2.getJSONArray("members")));
        Assert.assertArrayEquals(new String[]{"MEMBER31", "MEMBER32", "MEMBER33"}, JSONUtil.getStringArray(distributionList3.getJSONArray("members")));

        // lastUpdate should be set correctly
        // Note: Distribution lists always have a "lastUpdate" timestamp! For compatibility reasons,
        // if no date was set, then timestamp 0 is returned.
        Assert.assertEquals(0L, distributionList1.getLong("lastUpdate"));
        Assert.assertEquals(testDate1Timestamp, distributionList2.getLong("lastUpdate"));
        Assert.assertEquals(testDate2Timestamp, distributionList3.getLong("lastUpdate"));
    }

    @Test
    public void testSafeJsonSettingBlockedContacts() throws Exception {
        Mockito.when(blockedContactsServiceMock.getAll()).thenReturn(new String[]{"NONONONO", "BLOCKED0"});

        final JSONObject parsed = getParsedSafeJson(getServiceImpl());
        final JSONObject settings = parsed.getJSONObject("settings");
        final JSONArray identities = settings.getJSONArray("blockedContacts");
        Assert.assertArrayEquals(new String[]{"NONONONO", "BLOCKED0"}, JSONUtil.getStringArray(identities));
    }

    @Test
    public void testSafeJsonSettingsSyncExcludedIds() throws Exception {
        Mockito.when(excludedSyncIdentitiesServiceMock.getAll()).thenReturn(new String[]{"ECHOECHO", "OCHEOCHE"});

        final JSONObject parsed = getParsedSafeJson(getServiceImpl());
        final JSONObject settings = parsed.getJSONObject("settings");
        final JSONArray identities = settings.getJSONArray("syncExcludedIds");
        Assert.assertArrayEquals(new String[]{"ECHOECHO", "OCHEOCHE"}, JSONUtil.getStringArray(identities));
    }
}
