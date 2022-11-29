/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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

import android.accounts.Account;
import android.accounts.AccountManagerCallback;
import android.net.Uri;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;

import androidx.annotation.Nullable;
import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.group.GroupInviteService;
import ch.threema.app.services.group.GroupInviteServiceImpl;
import ch.threema.app.services.license.LicenseService;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteData;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.protobuf.url_payloads.GroupInvite;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.group.GroupInviteModel;

public class GroupInviteServiceTest {

	private GroupService groupService;
	private GroupInviteService groupInviteService;

	static final String TEST_GROUP_NAME = "A nice little group";
	static final String TEST_INVITE_NAME = "New unnamed link";
	static String TEST_IDENTITY = "ECHOECHO";
	static final GroupInvite.ConfirmationMode TEST_CONFIRMATION_MODE_AUTOMATIC = GroupInvite.ConfirmationMode.AUTOMATIC;
	static GroupInviteToken TEST_TOKEN_VALID;
	static GroupInviteModel TEST_INVITE_MODEL;
	static String TEST_ENCODED_INVITE = "RUNIT0VDSE86MDAwMTAyMDMwNDA1MDYwNzA4MDkwYTBiMGMwZDBlMGY6QSBuaWNlIGxpdHRsZSBncm91cDow";

	static {
		try {
			TEST_TOKEN_VALID = new GroupInviteToken(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
			TEST_INVITE_MODEL = new GroupInviteModel.Builder()
				.withGroupName(TEST_GROUP_NAME)
				.withInviteName(TEST_INVITE_NAME)
				.withToken(TEST_TOKEN_VALID)
				.withManualConfirmation(false)
				.build();
		} catch (GroupInviteToken.InvalidGroupInviteTokenException | GroupInviteModel.MissingRequiredArgumentsException e) {
			e.printStackTrace();
		}
	}

	private final GroupInviteData TEST_INVITE_DATA = new GroupInviteData(
		TEST_IDENTITY,
		TEST_TOKEN_VALID,
		TEST_GROUP_NAME,
		TEST_CONFIRMATION_MODE_AUTOMATIC
	);

	@Before
	public void setUp() {
		// create new implementation while only implementing getIdentity with the TEST_IDENTITY because Powermock cannot be used in androidTest scope
		UserService userService = new UserService() {
			@Override
			public void createIdentity(byte[] newRandomSeed) throws Exception {

			}

			@Override
			public void removeIdentity() throws Exception {

			}

			@Override
			public Account getAccount() {
				return null;
			}

			@Override
			public Account getAccount(boolean createIfNotExists) {
				return null;
			}

			@Override
			public boolean checkAccount() {
				return false;
			}

			@Override
			public boolean enableAccountAutoSync(boolean enable) {
				return false;
			}

			@Override
			public void removeAccount() {

			}

			@Override
			public boolean removeAccount(AccountManagerCallback<Boolean> callback) {
				return false;
			}

			@Override
			public boolean hasIdentity() {
				return false;
			}

			@Override
			public String getIdentity() {
				return TEST_IDENTITY;
			}

			@Override
			public boolean isMe(String identity) {
				return false;
			}

			@Override
			public byte[] getPublicKey() {
				return new byte[0];
			}

			@Override
			public byte[] getPrivateKey() {
				return new byte[0];
			}

			@Override
			public String getLinkedEmail() {
				return null;
			}

			@Override
			public String getLinkedMobileE164() {
				return null;
			}

			@Override
			public String getLinkedMobile() {
				return null;
			}

			@Override
			public String getLinkedMobile(boolean returnPendingNumber) {
				return null;
			}

			@Override
			public void linkWithEmail(String email) throws Exception {

			}

			@Override
			public void unlinkEmail() throws Exception {

			}

			@Override
			public int getEmailLinkingState() {
				return 0;
			}

			@Override
			public void checkEmailLinkState() {

			}

			@Override
			public Date linkWithMobileNumber(String number) throws Exception {
				return null;
			}

			@Override
			public void makeMobileLinkCall() throws Exception {

			}

			@Override
			public void unlinkMobileNumber() throws Exception {

			}

			@Override
			public boolean verifyMobileNumber(String code) throws Exception {
				return false;
			}

			@Override
			public int getMobileLinkingState() {
				return 0;
			}

			@Override
			public long getMobileLinkingTime() {
				return 0;
			}

			@Override
			public String getPublicNickname() {
				return null;
			}

			@Nullable
			@Override
			public String setPublicNickname(String publicNickname) {
				return null;
			}

			@Override
			public boolean isTyping(String toIdentity, boolean isTyping) {
				return false;
			}

			@Override
			public boolean restoreIdentity(String backupString, String password) throws Exception {
				return false;
			}

			@Override
			public boolean restoreIdentity(String identity, byte[] privateKey, byte[] publicKey) throws Exception {
				return false;
			}

			@Override
			public void setPolicyResponse(String responseData, String signature, int policyErrorCode) {

			}

			@Override
			public void setCredentials(LicenseService.Credentials credentials) {

			}

			@Override
			public boolean sendFlags() {
				return false;
			}

			@Override
			public boolean setRevocationKey(String revocationKey) {
				return false;
			}

			@Override
			public Date getLastRevocationKeySet() {
				return null;
			}

			@Override
			public void checkRevocationKey(boolean force) {

			}
		};
		try {
			this.groupService = ThreemaApplication.getServiceManager().getGroupService();
		} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
			e.printStackTrace();
		}
		DatabaseServiceNew databaseServiceNew = ThreemaApplication.getServiceManager().getDatabaseServiceNew();
		this.groupInviteService = new GroupInviteServiceImpl(userService, this.groupService, databaseServiceNew);
	}

	@Test
	public void testEncodeDecodeGroupInvite() {
		Uri encodedGroupInvite = groupInviteService.encodeGroupInviteLink(TEST_INVITE_MODEL);

		Assert.assertEquals("https", encodedGroupInvite.getScheme());
		Assert.assertEquals(BuildConfig.groupLinkActionUrl, encodedGroupInvite.getAuthority());
		Assert.assertEquals("/join", encodedGroupInvite.getPath());
		Assert.assertEquals(TEST_ENCODED_INVITE, encodedGroupInvite.getEncodedFragment());
	}

	@Test
	public void testDecodeGroupInvite() throws IOException, GroupInviteToken.InvalidGroupInviteTokenException {
		GroupInviteData inviteDataFromDecodedUri = groupInviteService.decodeGroupInviteLink(TEST_ENCODED_INVITE);

		Assert.assertEquals(TEST_INVITE_DATA.getAdminIdentity(),  inviteDataFromDecodedUri.getAdminIdentity());
		Assert.assertEquals(TEST_INVITE_DATA.getToken(), inviteDataFromDecodedUri.getToken());
		Assert.assertEquals(TEST_INVITE_DATA.getGroupName(), inviteDataFromDecodedUri.getGroupName());
		Assert.assertEquals(TEST_INVITE_DATA.getConfirmationMode(), inviteDataFromDecodedUri.getConfirmationMode());
	}
}
