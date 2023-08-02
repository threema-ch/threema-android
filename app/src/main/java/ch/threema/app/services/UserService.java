/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import android.accounts.Account;
import android.accounts.AccountManagerCallback;

import java.util.Date;

import androidx.annotation.Nullable;
import ch.threema.app.services.license.LicenseService;

/**
 * Method and actions for the current Threema-User!
 */
public interface UserService {

	int LinkingState_NONE = 0;
	int LinkingState_PENDING = 1;
	int LinkingState_LINKED = 2;

	void createIdentity(byte[] newRandomSeed) throws Exception;

	void removeIdentity() throws Exception;

	Account getAccount();
	Account getAccount(boolean createIfNotExists);
	boolean checkAccount();

	boolean enableAccountAutoSync(boolean enable);

	/**
	 * Remove the Account for the Sync Adapter (all Android-Threema Contacts will be deleted)
	 */
	void removeAccount();

	/**
	 * Remove the Account for the Sync Adapter, see {@removeAccount}
	 * @param callback Callback after adding
	 */
	boolean removeAccount(AccountManagerCallback<Boolean> callback);

	boolean hasIdentity();

	String getIdentity();

	boolean isMe(String identity);

	byte[] getPublicKey();

	byte[] getPrivateKey();

	String getLinkedEmail();

	String getLinkedMobileE164();

	String getLinkedMobile();
	String getLinkedMobile(boolean returnPendingNumber);

	void linkWithEmail(String email) throws Exception;

	void unlinkEmail() throws Exception;

	int getEmailLinkingState();

	void checkEmailLinkState();

	Date linkWithMobileNumber(String number) throws Exception;

	void makeMobileLinkCall() throws Exception;

	void unlinkMobileNumber() throws Exception;

	boolean verifyMobileNumber(String code) throws Exception;

	int getMobileLinkingState();

	long getMobileLinkingTime();

	String getPublicNickname();

	/**
	 * Set the own public nickname and return the converted (size limit) string.
	 *
	 * @return converted and truncated string or null if an error happens.
	 */
	@Nullable String setPublicNickname(String publicNickname);

	boolean restoreIdentity(String backupString, String password) throws Exception;

	boolean restoreIdentity(String identity, byte[] privateKey, byte[] publicKey) throws Exception;

	void setPolicyResponse(String responseData, String signature, int policyErrorCode);

	void setCredentials(LicenseService.Credentials credentials);

	boolean sendFlags();

	boolean setRevocationKey(String revocationKey);

	Date getLastRevocationKeySet();

	/**
	 * Check revocation key
	 *
	 * @param force
	 */
	void checkRevocationKey(boolean force);
}
