/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.ContactsContract;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.listeners.SMSVerificationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.routines.UpdateWorkInfoRoutine;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.SerialCredentials;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.stores.PreferenceStoreInterface;
import ch.threema.app.stores.PreferenceStoreInterfaceDevNullImpl;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DeviceIdUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.APIConnector;
import ch.threema.client.CreateIdentityRequestDataInterface;
import ch.threema.client.IdentityBackupDecoder;
import ch.threema.client.IdentityStoreInterface;
import ch.threema.client.MessageQueue;
import ch.threema.client.ProtocolDefines;
import ch.threema.client.ThreemaFeature;
import ch.threema.client.TypingIndicatorMessage;
import ch.threema.client.Utils;

import static ch.threema.app.ThreemaApplication.PHONE_LINKED_PLACEHOLDER;

/**
 * This service class handle all user actions (db/identity....)
 */
public class UserServiceImpl implements UserService, CreateIdentityRequestDataInterface  {
	private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

	private final Context context;
	private final PreferenceStoreInterface preferenceStore;
	private final IdentityStore identityStore;
	private final APIConnector apiConnector;
	private final LocaleService localeService;
	private final MessageQueue messageQueue;
	private final PreferenceService preferenceService;
	private String policyResponseData;
	private String policySignature;
	private int policyErrorCode;
    private LicenseService.Credentials credentials;
	private Account account;

	public UserServiceImpl(
		Context context,
		PreferenceStoreInterface preferenceStore,
		LocaleService localeService,
		APIConnector apiConnector,
		IdentityStore identityStore,
		MessageQueue messageQueue,
		PreferenceService preferenceService
	) {
		this.context = context;
		this.preferenceStore = preferenceStore;
		this.localeService = localeService;
		this.messageQueue = messageQueue;
		this.identityStore = identityStore;
		this.apiConnector = apiConnector;
		this.preferenceService = preferenceService;
	}

	@Override
	public void createIdentity(byte[] newRandomSeed) throws Exception {
		if (this.hasIdentity()) {
			throw new ThreemaException("please remove your existing identity " + this.getIdentity());
		}

		// no need to send a request if we have no licence.
		// note that CheckLicenseRoutine may not have received an upstream response yet.
		if (policySignature == null && policyResponseData == null && credentials == null && !BuildConfig.DEBUG) {
			throw new ThreemaException(context.getString(R.string.missing_app_licence) + "\n" + context.getString(R.string.app_store_error_code, policyErrorCode));    /* Create identity phase 1 unsuccessful:*/
		}
		else {
			this.apiConnector.createIdentity(
				this.identityStore,
				newRandomSeed,
				this
			);
		}

		this.sendFlags();

		// identity has been successfully created. set push token
		PushUtil.enqueuePushTokenUpdate(context, false, false);
	}

	@Override
	public void removeIdentity() throws Exception {
		if (!this.hasIdentity()) {
			throw new ThreemaException("no identity to remove");
		}
		this.removeAccount();
		this.identityStore.clear();
	}

	@Override
	public Account getAccount() {
		return this.getAccount(false);
	}

	@Override
	public Account getAccount(boolean createIfNotExists) {
		if (this.account == null) {
			AccountManager accountManager = AccountManager.get(this.context);

			try {
				this.account = Functional.select(new HashSet<Account>(Arrays.asList(accountManager.getAccountsByType(context.getPackageName()))), new IPredicateNonNull<Account>() {
					@Override
					public boolean apply(@NonNull Account type) {
						return true;
					}
				});
			} catch (SecurityException e) {
				logger.error("Exception", e);
			}

			//if sync enabled, create one!
			if(this.account == null && (createIfNotExists || this.preferenceService.isSyncContacts())) {
				this.account = new Account(context.getString(R.string.app_name), context.getString(R.string.package_name));
				// TODO: crashes on some phones after update! java.lang.SecurityException: caller uid 10025 is different than the authenticator's uid
				// This method requires the caller to have the same UID as the added account's authenticator.
				try {
					accountManager.addAccountExplicitly(this.account, "", null);
					//auto enable sync
					ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
					if (!ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)) {
						ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
					}
				} catch (SecurityException e) {
					logger.error("Exception", e);
				}
			}

		}
		return this.account;
	}

	@Override
	public boolean checkAccount() {
		AccountManager accountManager = AccountManager.get(this.context);
		return Functional.select(new HashSet<Account>(Arrays.asList(accountManager.getAccountsByType(context.getPackageName()))), new IPredicateNonNull<Account>() {
			@Override
			public boolean apply(@NonNull Account type) {
				return true;
			}
		}) != null;
	}

	@Override
	public boolean enableAccountAutoSync(boolean enable) {
		Account account = this.getAccount();
		if(account != null) {
			if(enable != ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)) {
				ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, enable);
			}
			return true;
		}
		return false;
	}

	@Override
	public void removeAccount() {
		this.removeAccount(null);
	}

	@Override
	public boolean removeAccount(AccountManagerCallback<Boolean> callback) {
		Account a = this.getAccount(false);
		if(a != null) {
			AccountManager accountManager = AccountManager.get(this.context);
			accountManager.removeAccount(a, callback, null);
			this.account = null;
			return true;
		}
		else {
			return false;
		}
	}

	@Override
	public boolean hasIdentity() {
		return this.getIdentity() != null;
	}

	@Override
	public String getIdentity() {
		return this.identityStore.getIdentity();
	}

	@Override
	public boolean isMe(@Nullable String identity) {
		return identity != null && identity.equals(this.getIdentity());
	}

	@Override
	public byte[] getPublicKey() {
		return this.identityStore.getPublicKey();
	}

	@Override
	public byte[] getPrivateKey() {
		return this.identityStore.getPrivateKey();
	}

	@Override
	public String getLinkedEmail() {
		String email = this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_EMAIL);
		return email!=null?email:"";
	}

	@Override
	public void linkWithEmail(String email) throws Exception {
		boolean pending = this.apiConnector.linkEmail(
				email,
				this.getLanguage(),
				this.identityStore);

		this.preferenceStore.save(PreferenceStore.PREFS_LINKED_EMAIL, email);
		this.preferenceStore.save(PreferenceStore.PREFS_LINKED_EMAIL_PENDING, pending);
	}

	@Override
	public void unlinkEmail() throws Exception {
		String email = this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_EMAIL);
		if (email == null) {
			throw new ThreemaException("no email linked");
		}

		this.apiConnector.linkEmail("", this.getLanguage(), this.identityStore);
		this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_EMAIL);
		this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_EMAIL_PENDING);
	}

	@Override
	public int getEmailLinkingState() {
		if(this.preferenceStore.getBoolean(PreferenceStore.PREFS_LINKED_EMAIL_PENDING)) {
			return LinkingState_PENDING;
		}
		else if(this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_EMAIL) != null) {
			return LinkingState_LINKED;
		}
		else {
			return LinkingState_NONE;
		}
	}

	@Override
	public void checkEmailLinkState() {
		if(this.getEmailLinkingState() == LinkingState_PENDING) {
			try {
				if(this.apiConnector.linkEmailCheckStatus(this.getLinkedEmail(), this.identityStore)) {
					this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_EMAIL_PENDING);
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	public Date linkWithMobileNumber(String number) throws Exception {
		Date linkWithMobileTime = new Date();
		String normalizedMobileNo = this.localeService.getNormalizedPhoneNumber(number);

		if(normalizedMobileNo != null && normalizedMobileNo.length() > 0 && normalizedMobileNo.startsWith("+")) {
			normalizedMobileNo = normalizedMobileNo.substring(1);
		}

		String verificationId = this.apiConnector.linkMobileNo(
				normalizedMobileNo,
				this.getLanguage(),
				this.identityStore,
				(BuildFlavor.getLicenseType() == BuildFlavor.LicenseType.GOOGLE_WORK ||
					BuildFlavor.getLicenseType() == BuildFlavor.LicenseType.HMS_WORK)
					? "threemawork" : null
		);

		this.preferenceStore.save(PreferenceStore.PREFS_LINKED_MOBILE, number);

		if (verificationId == null) {
			throw new ThreemaException(this.context.getResources().getString(R.string.mobile_already_linked));
		}

		this.preferenceStore.save(PreferenceStore.PREFS_LINKED_MOBILE_PENDING, System.currentTimeMillis());
		this.preferenceStore.save(PreferenceStore.PREFS_MOBILE_VERIFICATION_ID, verificationId);

		ListenerManager.smsVerificationListeners.handle(new ListenerManager.HandleListener<SMSVerificationListener>() {
			@Override
			public void handle(SMSVerificationListener listener) {
				listener.onVerificationStarted();
			}
		});

		return linkWithMobileTime;
	}

	@Override
	public void makeMobileLinkCall() throws Exception {
		if(this.getMobileLinkingState() != LinkingState_PENDING) {
			throw new ThreemaException("no verification in progress");
		}

		this.apiConnector.linkMobileNoCall(getCurrentMobileNumberVerificationId());
	}

	private String getCurrentMobileNumberVerificationId() {
		return this.preferenceStore.getString(PreferenceStore.PREFS_MOBILE_VERIFICATION_ID);
	}

	private String getCurrentMobileNumber() {
		return this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_MOBILE);
	}

	@Override
	public void unlinkMobileNumber() throws Exception {
		String mobileNumber = this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_MOBILE);
		if (mobileNumber == null) {

			String currentMobileNumber = getCurrentMobileNumber();
			if(currentMobileNumber == null || currentMobileNumber.length() == 0) {
				throw new ThreemaException("no mobile number linked");
			}
		}

		this.apiConnector.linkMobileNo("", this.getLanguage(), this.identityStore);
		this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_MOBILE);
		this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_MOBILE_PENDING);
		this.preferenceStore.remove(PreferenceStore.PREFS_MOBILE_VERIFICATION_ID);

		ListenerManager.smsVerificationListeners.handle(new ListenerManager.HandleListener<SMSVerificationListener>() {
			@Override
			public void handle(SMSVerificationListener listener) {
				listener.onVerified();
			}
		});
	}

	@Override
	public boolean verifyMobileNumber(String code) throws Exception {
		if (this.getMobileLinkingState() == LinkingState_PENDING) {
			this.apiConnector.linkMobileNoVerify(getCurrentMobileNumberVerificationId(), code);

			//verification ok, save phone number
			this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_MOBILE_PENDING);
			this.preferenceStore.remove(PreferenceStore.PREFS_MOBILE_VERIFICATION_ID);

			ListenerManager.smsVerificationListeners.handle(new ListenerManager.HandleListener<SMSVerificationListener>() {
				@Override
				public void handle(SMSVerificationListener listener) {
					listener.onVerified();
				}
			});
			return true;
		}

		return false;
	}

	@Override
	public String getLinkedMobileE164() {
		return this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_MOBILE);
	}

	@Override
	public String getLinkedMobile() {
		String linkedMobile = getLinkedMobileE164();

		if (PHONE_LINKED_PLACEHOLDER.equals(linkedMobile)) {
			return linkedMobile;
		}

		if (TestUtil.empty(linkedMobile)) {
			return null;
		}
		return "+" + linkedMobile;
	}

	@Override
	public String getLinkedMobile(boolean returnPendingNumber) {
		String currentMobileNumber = getCurrentMobileNumber();
		if(currentMobileNumber != null && currentMobileNumber.length() > 0) {
			return currentMobileNumber;
		}

		return this.getLinkedMobile();
	}

	@Override
	public int getMobileLinkingState() {
		if(this.preferenceStore.getLong(PreferenceStore.PREFS_LINKED_MOBILE_PENDING) > 0) {
			return LinkingState_PENDING;
		}
		else if(this.getLinkedMobile() != null) {
			return LinkingState_LINKED;
		}
		else {
			return LinkingState_NONE;
		}
	}

	@Override
	public long getMobileLinkingTime() {
		return this.preferenceStore.getLong(PreferenceStore.PREFS_LINKED_MOBILE_PENDING);
	}

	@Override
	public String getPublicNickname() {
		return this.identityStore.getPublicNickname();
	}

	@Override
	public String setPublicNickname(String publicNickname) {
		//truncate string into a 32 byte length string
		//fix #ANDR-530
		String truncated = Utils.truncateUTF8String(publicNickname, ProtocolDefines.PUSH_FROM_LEN);
		this.identityStore.setPublicNickname(truncated);
		//run update work info (only if the app is the work version)
		if(ConfigUtils.isWorkBuild()) {
			UpdateWorkInfoRoutine.start();
		}
		return truncated;
	}

	private String getLanguage() {
		return LocaleUtil.getLanguage();
	}

	@Override
	public boolean isTyping(String toIdentity, boolean isTyping) {
		if (!preferenceService.isTypingIndicator()) {
			return false;
		}

		final TypingIndicatorMessage msg = new TypingIndicatorMessage();
		msg.setTyping(isTyping);
		msg.setFromIdentity(this.getIdentity());
		msg.setToIdentity(toIdentity);

		try {
			return this.messageQueue.enqueue(msg) != null;
		} catch (ThreemaException e) {
			logger.error("Exception", e);
		}
		return false;
	}

	@Override
	public boolean restoreIdentity(final String backupString, final String password) throws Exception {
		final IdentityBackupDecoder identityBackupDecoder = new IdentityBackupDecoder(backupString);
		if(!identityBackupDecoder.decode(password)) {
			return false;
		}

		return restoreIdentity(identityBackupDecoder.getIdentity(), identityBackupDecoder.getPrivateKey(), identityBackupDecoder.getPublicKey());
	}

	@Override
	public boolean restoreIdentity(String identity, byte[] privateKey, byte[] publicKey) throws Exception {
		IdentityStoreInterface temporaryIdentityStore = new IdentityStore(new PreferenceStoreInterfaceDevNullImpl());
		//store identity without server group
		temporaryIdentityStore.storeIdentity(
				identity,
				"",
				publicKey,
				privateKey
		);
		//fetching identity group
		APIConnector.FetchIdentityPrivateResult result = this.apiConnector.fetchIdentityPrivate(temporaryIdentityStore);

		if(result == null) {
			throw new ThreemaException("fetching private result failed");
		}

		this.removeAccount();

		//store to the REAL identity store!
		this.identityStore.storeIdentity(
				identity,
				result.serverGroup,
				publicKey,
				privateKey
		);

		this.sendFlags();

		if(result.email != null && result.email.length() > 0) {
			this.preferenceStore.save(PreferenceStore.PREFS_LINKED_EMAIL, result.email);
		}
		if(result.mobileNo!= null && result.mobileNo.length() > 0) {
			this.preferenceStore.save(PreferenceStore.PREFS_LINKED_MOBILE, result.mobileNo);
		}

		// identity has been successfully restored. set push token
		PushUtil.enqueuePushTokenUpdate(context, false, false);

		return true;
	}

	@Override
	public void setPolicyResponse(String responseData, String signature, int policyErrorCode) {
		this.policyResponseData = responseData;
		this.policySignature = signature;
		this.policyErrorCode = policyErrorCode;
	}


	@Override
    public void setCredentials(LicenseService.Credentials credentials) {
        this.credentials = credentials;
    }


	@Override
	public boolean sendFlags() {
		boolean success = false;
		try {
			ThreemaFeature.Builder builder = (new ThreemaFeature.Builder())
					.audio(true)
					.group(true)
					.ballot(true)
					.file(true)
					.voip(true)
					.videocalls(true);
			if(this.preferenceService.getTransmittedFeatureLevel() != builder.build()) {
				this.apiConnector.setFeatureMask(builder, this.identityStore);
				this.preferenceService.setTransmittedFeatureLevel(builder.build());
			}
			success = true;
		} catch (Exception e) {
			logger.error("Exception", e);
		}


		return success;
	}

	@Override
	public boolean setRevocationKey(String revocationKey) {
		APIConnector.SetRevocationKeyResult result = null;
		try {
			result = this.apiConnector.setRevocationKey(this.identityStore, revocationKey);
			if (!result.success) {
				logger.error("set revocation key failed: " + result.error);
				return false;
			}
			else {
				//update
				this.checkRevocationKey(true);
			}

			return true;
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return false;
	}

	@Override
	public Date getLastRevocationKeySet() {
		return this.preferenceStore.getDate(PreferenceStore.PREFS_LAST_REVOCATION_KEY_SET);
	}

	@Override
	public void checkRevocationKey(boolean force) {
		logger.debug("RevocationKey", "check (force = " + force + ")");
		Date lastSet = null;
		try {
			//check if force = true or PREFS_REVOCATION_KEY_CHECKED is false or not set
			boolean check = force
				||!this.preferenceStore.getBoolean(PreferenceStore.PREFS_REVOCATION_KEY_CHECKED);


			logger.debug("RevocationKey", "check = " + check);
			if(check) {
				APIConnector.CheckRevocationKeyResult result = this.apiConnector.checkRevocationKey(this.identityStore);
				if (result != null) {
					if (result.isSet) {
						lastSet = result.lastChanged;
					}

					logger.debug("RevocationKey", "result = " + result.isSet);
					//update new state
					this.preferenceStore.save(PreferenceStore.PREFS_LAST_REVOCATION_KEY_SET, lastSet);

					//update checked state
					this.preferenceStore.save(PreferenceStore.PREFS_REVOCATION_KEY_CHECKED, true);
				}
				else {
					logger.debug("RevocationKey", "result is null");
				}
			}
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public JSONObject createIdentityRequestDataJSON() throws JSONException {
		JSONObject baseObject = new JSONObject();

		BuildFlavor.LicenseType licenseType = BuildFlavor.getLicenseType();
		String deviceId = DeviceIdUtil.getDeviceId(this.context);

		if (deviceId != null) {
			baseObject.put("deviceId", deviceId);
		}

		if (licenseType == BuildFlavor.LicenseType.GOOGLE) {
			baseObject.put("lvlResponseData", policyResponseData);
			baseObject.put("lvlSignature", policySignature);
		}
		else if (licenseType == BuildFlavor.LicenseType.HMS) {
			baseObject.put("hmsResponseData", policyResponseData);
			baseObject.put("hmsSignature", policySignature);
		}
		else {
			String licenseKey = null;
			String licenseUsername = null;
			String licensePassword = null;

			if(this.credentials != null) {
				if(this.credentials instanceof SerialCredentials) {
					licenseKey = ((SerialCredentials)this.credentials).licenseKey;
				}
				else if(this.credentials instanceof UserCredentials) {
					licenseUsername = ((UserCredentials)this.credentials).username;
					licensePassword = ((UserCredentials)this.credentials).password;
				}
			}
			if (licenseKey != null) {
				baseObject.put("licenseKey", licenseKey);
			}

			if (licenseUsername != null) {
				baseObject.put("licenseUsername", licenseUsername);
			}

			if (licensePassword != null) {
				baseObject.put("licensePassword", licensePassword);
			}
		}

		return baseObject;
	}
}
