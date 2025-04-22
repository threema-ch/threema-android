/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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
import android.text.format.DateUtils;

import com.neilalexander.jnacl.NaCl;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.listeners.ProfileListener;
import ch.threema.app.listeners.SMSVerificationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.routines.UpdateWorkInfoRoutine;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.SerialCredentials;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.stores.PreferenceStoreInterface;
import ch.threema.app.stores.PreferenceStoreInterfaceDevNullImpl;
import ch.threema.app.tasks.ReflectUserProfileNicknameSyncTask;
import ch.threema.app.tasks.TaskCreator;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.DeviceIdUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.identitybackup.IdentityBackupDecoder;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.api.CreateIdentityRequestDataInterface;
import ch.threema.domain.protocol.blob.BlobScope;
import ch.threema.domain.protocol.blob.BlobUploader;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ContactModel;

import static ch.threema.app.ThreemaApplication.PHONE_LINKED_PLACEHOLDER;
import static ch.threema.app.utils.StreamUtilKt.toByteArray;

/**
 * This service class handle all user actions (db/identity....)
 */
public class UserServiceImpl implements UserService, CreateIdentityRequestDataInterface {
    private static final Logger logger = LoggingUtil.getThreemaLogger("UserServiceImpl");

    @NonNull
    private final Context context;
    @NonNull
    private final PreferenceStoreInterface preferenceStore;
    @NonNull
    private final IdentityStore identityStore;
    @NonNull
    private final APIConnector apiConnector;
    @NonNull
    private final ApiService apiService;
    @NonNull
    private final FileService fileService;
    @NonNull
    private final LocaleService localeService;
    @NonNull
    private final PreferenceService preferenceService;
    @NonNull
    private final TaskManager taskManager;
    @NonNull
    private final TaskCreator taskCreator;
    @NonNull
    private final MultiDeviceManager multiDeviceManager;
    private String policyResponseData;
    private String policySignature;
    private int policyErrorCode;
    private LicenseService.Credentials credentials;
    private Account account;

    // TODO(ANDR-2519): Remove when md allows fs
    private boolean isFsEnabled = true;

    public UserServiceImpl(
        @NonNull Context context,
        @NonNull PreferenceStoreInterface preferenceStore,
        @NonNull LocaleService localeService,
        @NonNull APIConnector apiConnector,
        @NonNull ApiService apiService,
        @NonNull FileService fileService,
        @NonNull IdentityStore identityStore,
        @NonNull PreferenceService preferenceService,
        @NonNull TaskManager taskManager,
        @NonNull TaskCreator taskCreator,
        @NonNull MultiDeviceManager multiDeviceManager
    ) {
        this.context = context;
        this.preferenceStore = preferenceStore;
        this.localeService = localeService;
        this.identityStore = identityStore;
        this.apiConnector = apiConnector;
        this.apiService = apiService;
        this.fileService = fileService;
        this.preferenceService = preferenceService;
        this.taskCreator = taskCreator;
        this.taskManager = taskManager;
        this.multiDeviceManager = multiDeviceManager;
    }

    @Override
    public void createIdentity(byte[] newRandomSeed) throws Exception {
        if (this.hasIdentity()) {
            throw new ThreemaException("please remove your existing identity " + this.getIdentity());
        }

        // no need to send a request if we have no licence
        // note that CheckLicenseRoutine may not have received an upstream response yet.
        if (policySignature == null && policyResponseData == null && credentials == null
            && !(BuildFlavor.getCurrent().getLicenseType().equals(BuildFlavor.LicenseType.NONE))
        ) {
            throw new ThreemaException(context.getString(R.string.missing_app_licence) + "\n" + context.getString(R.string.app_store_error_code, policyErrorCode));    /* Create identity phase 1 unsuccessful:*/
        } else {
            this.apiConnector.createIdentity(
                this.identityStore,
                newRandomSeed,
                this
            );
        }

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
                this.account = Functional.select(new HashSet<>(Arrays.asList(accountManager.getAccountsByType(context.getPackageName()))), type -> true);
            } catch (SecurityException e) {
                logger.error("Could not get account", e);
            }

            //if sync enabled, create one!
            if (this.account == null && (createIfNotExists || this.preferenceService.isSyncContacts())) {
                this.account = new Account(context.getString(R.string.app_name), context.getString(R.string.package_name));
                // This method requires the caller to have the same UID as the added account's authenticator.
                try {
                    accountManager.addAccountExplicitly(this.account, "", null);
                    //auto enable sync
                    ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
                    if (!ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)) {
                        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
                    }
                } catch (SecurityException e) {
                    logger.error("Could not add account", e);
                }
            }

        }
        return this.account;
    }

    @Override
    public boolean checkAccount() {
        AccountManager accountManager = AccountManager.get(this.context);
        return Functional.select(new HashSet<>(Arrays.asList(accountManager.getAccountsByType(context.getPackageName()))), type -> true) != null;
    }

    @Override
    public boolean enableAccountAutoSync(boolean enable) {
        Account account = this.getAccount();
        if (account != null) {
            if (enable != ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)) {
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
        if (a != null) {
            AccountManager accountManager = AccountManager.get(this.context);
            try {
                accountManager.removeAccount(a, callback, null);
            } catch (Exception e) {
                logger.error("Unable to remove account", e);
                return false;
            }
            this.account = null;
            return true;
        } else {
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
        return email != null ? email : "";
    }

    @Override
    public void linkWithEmail(String email, @NonNull TriggerSource triggerSource) throws Exception {
        boolean pending = this.apiConnector.linkEmail(
            email,
            this.getLanguage(),
            this.identityStore);

        this.preferenceStore.save(PreferenceStore.PREFS_LINKED_EMAIL, email);
        this.preferenceStore.save(PreferenceStore.PREFS_LINKED_EMAIL_PENDING, pending);

        // Note that we sync the identity links regardless from the pending-flag to be safe. In both
        // cases there is a scenario where we want to reflect the change:
        //
        // 1. pending == false: The email address is verified immediately and therefore already
        // returns false here. This is very unlikely, but it might happen in theory.
        //
        // 2. pending == true: Prior to the current execution of this method we may have had an
        // email address that has been successfully linked and verified. The new email address won't
        // be reflected yet by the reflection task if it is not yet verified, but the old email
        // address will be removed already (as it technically has been unlinked).
        reflectUserProfileIdentityLinksIfApplicable(triggerSource);
    }

    @Override
    public void unlinkEmail(@NonNull TriggerSource triggerSource) throws Exception {
        String email = this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_EMAIL);
        if (email == null) {
            throw new ThreemaException("no email linked");
        }

        this.apiConnector.linkEmail("", this.getLanguage(), this.identityStore);
        this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_EMAIL);
        this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_EMAIL_PENDING);

        // Reflect an update
        reflectUserProfileIdentityLinksIfApplicable(triggerSource);
    }

    @Override
    public int getEmailLinkingState() {
        if (this.preferenceStore.getBoolean(PreferenceStore.PREFS_LINKED_EMAIL_PENDING)) {
            return LinkingState_PENDING;
        } else if (this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_EMAIL) != null) {
            return LinkingState_LINKED;
        } else {
            return LinkingState_NONE;
        }
    }

    @Override
    public void checkEmailLinkState(@NonNull TriggerSource triggerSource) {
        if (this.getEmailLinkingState() == LinkingState_PENDING) {
            try {
                if (this.apiConnector.linkEmailCheckStatus(this.getLinkedEmail(), this.identityStore)) {
                    this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_EMAIL_PENDING);
                    reflectUserProfileIdentityLinksIfApplicable(triggerSource);
                }
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public Date linkWithMobileNumber(String number, @NonNull TriggerSource triggerSource) throws Exception {
        boolean aPhoneNumberHasBeenLinked = getMobileLinkingState() == LinkingState_LINKED;

        Date linkWithMobileTime = new Date();
        String normalizedMobileNo = this.localeService.getNormalizedPhoneNumber(number);

        if (normalizedMobileNo != null && normalizedMobileNo.startsWith("+")) {
            normalizedMobileNo = normalizedMobileNo.substring(1);
        }

        String verificationId = this.apiConnector.linkMobileNo(
            normalizedMobileNo,
            this.getLanguage(),
            this.identityStore,
            (BuildFlavor.getCurrent().getLicenseType() == BuildFlavor.LicenseType.GOOGLE_WORK ||
                BuildFlavor.getCurrent().getLicenseType() == BuildFlavor.LicenseType.HMS_WORK)
                ? "threemawork" : null
        );

        this.preferenceStore.save(PreferenceStore.PREFS_LINKED_MOBILE, number);

        if (verificationId == null) {
            throw new ThreemaException(this.context.getResources().getString(R.string.mobile_already_linked));
        }

        this.preferenceStore.save(PreferenceStore.PREFS_LINKED_MOBILE_PENDING, System.currentTimeMillis());
        this.preferenceStore.save(PreferenceStore.PREFS_MOBILE_VERIFICATION_ID, verificationId);

        // If a phone number is currently successfully linked, then we trigger an update already now
        // as it is removed immediately and the new phone number may never be verified
        if (aPhoneNumberHasBeenLinked) {
            reflectUserProfileIdentityLinksIfApplicable(triggerSource);
        }

        ListenerManager.smsVerificationListeners.handle(SMSVerificationListener::onVerificationStarted);

        return linkWithMobileTime;
    }

    @Override
    public void makeMobileLinkCall() throws Exception {
        if (this.getMobileLinkingState() != LinkingState_PENDING) {
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
    public void unlinkMobileNumber(@NonNull TriggerSource triggerSource) throws Exception {
        String mobileNumber = this.preferenceStore.getString(PreferenceStore.PREFS_LINKED_MOBILE);
        if (mobileNumber == null) {

            String currentMobileNumber = getCurrentMobileNumber();
            if (currentMobileNumber == null || currentMobileNumber.length() == 0) {
                throw new ThreemaException("no mobile number linked");
            }
        }

        this.apiConnector.linkMobileNo("", this.getLanguage(), this.identityStore);
        this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_MOBILE);
        this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_MOBILE_PENDING);
        this.preferenceStore.remove(PreferenceStore.PREFS_MOBILE_VERIFICATION_ID);

        reflectUserProfileIdentityLinksIfApplicable(triggerSource);

        ListenerManager.smsVerificationListeners.handle(SMSVerificationListener::onVerified);
    }

    @Override
    public boolean verifyMobileNumber(String code, @NonNull TriggerSource triggerSource) throws Exception {
        if (this.getMobileLinkingState() == LinkingState_PENDING) {
            this.apiConnector.linkMobileNoVerify(getCurrentMobileNumberVerificationId(), code);

            //verification ok, save phone number
            this.preferenceStore.remove(PreferenceStore.PREFS_LINKED_MOBILE_PENDING);
            this.preferenceStore.remove(PreferenceStore.PREFS_MOBILE_VERIFICATION_ID);

            reflectUserProfileIdentityLinksIfApplicable(triggerSource);

            ListenerManager.smsVerificationListeners.handle(SMSVerificationListener::onVerified);
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

        if (TestUtil.isEmptyOrNull(linkedMobile)) {
            return null;
        }
        return "+" + linkedMobile;
    }

    @Override
    public String getLinkedMobile(boolean returnPendingNumber) {
        String currentMobileNumber = getCurrentMobileNumber();
        if (currentMobileNumber != null && currentMobileNumber.length() > 0) {
            return currentMobileNumber;
        }

        return this.getLinkedMobile();
    }

    @Override
    public int getMobileLinkingState() {
        if (this.preferenceStore.getLong(PreferenceStore.PREFS_LINKED_MOBILE_PENDING) > 0) {
            return LinkingState_PENDING;
        } else if (this.getLinkedMobile() != null) {
            return LinkingState_LINKED;
        } else {
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
    public String setPublicNickname(String publicNickname, @NonNull TriggerSource triggerSource) {
        final @NonNull String oldNickname = this.identityStore.getPublicNickname();
        // truncate string into a 32 byte length string
        // fix #ANDR-530
        final @Nullable String publicNicknameTruncated = Utils.truncateUTF8String(
            publicNickname,
            ProtocolDefines.PUSH_FROM_LEN
        );
        this.identityStore.persistPublicNickname(publicNicknameTruncated);
        // run update work info (only if the app is the work version)
        if (ConfigUtils.isWorkBuild()) {
            UpdateWorkInfoRoutine.start();
        }
        if (publicNicknameTruncated != null && !publicNicknameTruncated.equals(oldNickname)
            && multiDeviceManager.isMultiDeviceActive()
            && triggerSource != TriggerSource.SYNC) {
            taskManager.schedule(
                new ReflectUserProfileNicknameSyncTask(
                    publicNicknameTruncated,
                    ThreemaApplication.requireServiceManager()
                )
            );
        }
        return publicNicknameTruncated;
    }

    @Override
    @Nullable
    public byte[] getUserProfilePicture() {
        try {
            return toByteArray(fileService.getUserDefinedProfilePictureStream(getIdentity()));
        } catch (Exception e) {
            logger.error("Could not get user profile picture");
            return null;
        }
    }

    @Override
    public boolean setUserProfilePicture(@NonNull File userProfilePicture, @NonNull TriggerSource triggerSource) {
        try {
            fileService.writeUserDefinedProfilePicture(getIdentity(), userProfilePicture);
            onUserProfilePictureChanged();
            if (multiDeviceManager.isMultiDeviceActive() && triggerSource != TriggerSource.SYNC) {
                taskCreator.scheduleReflectUserProfilePictureTask();
            }
            return true;
        } catch (Exception e) {
            logger.error("Could not set user profile picture", e);
            return false;
        }
    }

    @Override
    public boolean setUserProfilePicture(@NonNull byte[] userProfilePicture, @NonNull TriggerSource triggerSource) {
        try {
            fileService.writeUserDefinedProfilePicture(getIdentity(), userProfilePicture);
            onUserProfilePictureChanged();
            if (multiDeviceManager.isMultiDeviceActive() && triggerSource != TriggerSource.SYNC) {
                taskCreator.scheduleReflectUserProfilePictureTask();
            }
            return true;
        } catch (Exception e) {
            logger.error("Could not set user profile picture", e);
            return false;
        }
    }

    @Override
    public void removeUserProfilePicture(@NonNull TriggerSource triggerSource) {
        fileService.removeUserDefinedProfilePicture(getIdentity());
        onUserProfilePictureChanged();
        if (multiDeviceManager.isMultiDeviceActive() && triggerSource != TriggerSource.SYNC) {
            taskCreator.scheduleReflectUserProfilePictureTask();
        }
    }

    @Override
    @WorkerThread
    @NonNull
    public ContactService.ProfilePictureUploadData uploadUserProfilePictureOrGetPreviousUploadData() {
        byte[] profilePicture = getUserProfilePicture();
        if (profilePicture == null) {
            // If there is no profile picture set, then return empty upload data with an empty byte
            // array as blob ID.
            ContactService.ProfilePictureUploadData data = new ContactService.ProfilePictureUploadData();
            data.blobId = ContactModel.NO_PROFILE_PICTURE_BLOB_ID;
            return data;
        }

        // Only upload blob every 7 days
        long uploadedAt = preferenceService.getProfilePicUploadDate();
        Date uploadDeadline = new Date(uploadedAt + ContactUtil.PROFILE_PICTURE_BLOB_CACHE_DURATION);
        Date now = new Date();

        if (now.after(uploadDeadline)) {
            logger.info("Uploading profile picture blob");

            ContactService.ProfilePictureUploadData data = uploadContactPhoto(profilePicture);

            if (data == null) {
                return new ContactService.ProfilePictureUploadData();
            }

            data.uploadedAt = now.getTime();

            preferenceService.setProfilePicUploadDate(now);
            preferenceService.setProfilePicUploadData(data);
            return data;
        } else {
            ContactService.ProfilePictureUploadData data = preferenceService.getProfilePicUploadData();
            if (data != null) {
                data.uploadedAt = uploadedAt;
                data.bitmapArray = profilePicture;
                return data;
            } else {
                return new ContactService.ProfilePictureUploadData();
            }
        }
    }

    @Nullable
    private ContactService.ProfilePictureUploadData uploadContactPhoto(@NonNull byte[] contactPhoto) {
        ContactService.ProfilePictureUploadData data = new ContactService.ProfilePictureUploadData();

        SecureRandom rnd = new SecureRandom();
        data.encryptionKey = new byte[NaCl.SYMMKEYBYTES];
        rnd.nextBytes(data.encryptionKey);

        data.bitmapArray = contactPhoto;
        byte[] imageData = NaCl.symmetricEncryptData(data.bitmapArray, data.encryptionKey, ProtocolDefines.CONTACT_PHOTO_NONCE);
        try {
            BlobUploader blobUploader = this.apiService.createUploader(
                imageData,
                true,
                BlobScope.Public.INSTANCE
            );
            data.blobId = blobUploader.upload();
        } catch (ThreemaException | IOException e) {
            logger.error("Could not upload contact photo", e);

            if (e instanceof FileNotFoundException && ConfigUtils.isOnPremBuild()) {
                logger.info("Invalidating auth token");
                apiService.invalidateAuthToken();
            }

            return null;
        }
        data.size = imageData.length;
        return data;
    }

    private void onUserProfilePictureChanged() {
        // Reset the last profile picture upload date
        this.preferenceService.setProfilePicUploadDate(new Date(0));
        this.preferenceService.setProfilePicUploadData(null);

        // Notify listeners
        ListenerManager.profileListeners.handle(ProfileListener::onAvatarChanged);
    }

    private String getLanguage() {
        return LocaleUtil.getLanguage();
    }

    @Override
    public boolean restoreIdentity(final String backupString, final String password) throws Exception {
        final IdentityBackupDecoder identityBackupDecoder = new IdentityBackupDecoder(backupString);
        if (!identityBackupDecoder.decode(password)) {
            return false;
        }

        return restoreIdentity(identityBackupDecoder.getIdentity(), identityBackupDecoder.getPrivateKey(), identityBackupDecoder.getPublicKey());
    }

    @Override
    public boolean restoreIdentity(@NonNull String identity, @NonNull byte[] privateKey, @NonNull byte[] publicKey) throws Exception {
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

        if (result == null) {
            throw new ThreemaException("fetching private identity data failed");
        }

        this.removeAccount();

        //store to the REAL identity store!
        this.identityStore.storeIdentity(
            identity,
            result.serverGroup,
            publicKey,
            privateKey
        );

        if (result.email != null && result.email.length() > 0) {
            this.preferenceStore.save(PreferenceStore.PREFS_LINKED_EMAIL, result.email);
        }
        if (result.mobileNo != null && result.mobileNo.length() > 0) {
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
    @WorkerThread
    public boolean sendFeatureMask() {
        boolean success = false;
        try {
            long featureMask = getMyFeatureMask();
            if (!shouldUpdateFeatureMask(featureMask)) {
                logger.info("No feature mask update necessary ({})", featureMask);
                return true;
            }

            logger.info("Sending feature mask {}", featureMask);
            this.apiConnector.setFeatureMask(featureMask, this.identityStore);
            this.preferenceService.setTransmittedFeatureMask(featureMask);
            this.preferenceService.setLastFeatureMaskTransmission(new Date().getTime());
            logger.info("Successfully sent feature mask");
            success = true;
        } catch (Exception e) {
            logger.error("Could not send feature mask", e);
        }

        return success;
    }

    private long getMyFeatureMask() {
        ThreemaFeature.Builder builder = (new ThreemaFeature.Builder())
            .audio(true)
            .group(true)
            .ballot(true)
            .file(true)
            .voip(true)
            .videocalls(true)
            .forwardSecurity(isFsEnabled)
            .groupCalls(true)
            .editMessages(true)
            .deleteMessages(true)
            .emojiReactions(true);

        return builder.build();
    }

    private boolean shouldUpdateFeatureMask(long actualFeatureMask) {
        long transmittedFeatureMask = preferenceService.getTransmittedFeatureMask();
        if (transmittedFeatureMask != actualFeatureMask) {
            logger.info("Feature mask update necessary: {} -> {}", transmittedFeatureMask, actualFeatureMask);
            return true;
        }

        long lastFeatureMaskTransmission = preferenceService.getLastFeatureMaskTransmission();
        long timeThreshold = new Date().getTime() - DateUtils.DAY_IN_MILLIS;
        return lastFeatureMaskTransmission < timeThreshold;
    }

    @Override
    public void setForwardSecurityEnabled(boolean isFsEnabled) {
        this.isFsEnabled = isFsEnabled;
    }

    @Override
    public boolean setRevocationKey(String revocationKey) {
        APIConnector.SetRevocationKeyResult result;
        try {
            result = this.apiConnector.setRevocationKey(this.identityStore, revocationKey);
            if (!result.success) {
                logger.error("set revocation key failed: {}", result.error);
                return false;
            } else {
                //update
                this.checkRevocationKey(true);
            }

            return true;
        } catch (Exception e) {
            logger.error("Could not set revocation key", e);
        }
        return false;
    }

    @Override
    public Date getLastRevocationKeySet() {
        return this.preferenceStore.getDate(PreferenceStore.PREFS_LAST_REVOCATION_KEY_SET);
    }

    @Override
    public void checkRevocationKey(boolean force) {
        logger.debug("checkRevocationKey (force={})", force);
        Date lastSet = null;
        try {
            //check if force = true or PREFS_REVOCATION_KEY_CHECKED is false or not set
            boolean check = force
                || !this.preferenceStore.getBoolean(PreferenceStore.PREFS_REVOCATION_KEY_CHECKED);


            logger.debug("checkRevocationKey (check={})", check);
            if (check) {
                APIConnector.CheckRevocationKeyResult result = this.apiConnector.checkRevocationKey(this.identityStore);
                if (result != null) {
                    if (result.isSet) {
                        lastSet = result.lastChanged;
                    }

                    logger.debug("checkRevocationKey (result={})", result.isSet);
                    //update new state
                    this.preferenceStore.save(PreferenceStore.PREFS_LAST_REVOCATION_KEY_SET, lastSet);

                    //update checked state
                    this.preferenceStore.save(PreferenceStore.PREFS_REVOCATION_KEY_CHECKED, true);
                } else {
                    logger.debug("checkRevocationKey (result is null)");
                }
            }
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @Override
    public JSONObject createIdentityRequestDataJSON() throws JSONException {
        JSONObject baseObject = new JSONObject();

        BuildFlavor.LicenseType licenseType = BuildFlavor.getCurrent().getLicenseType();
        String deviceId = DeviceIdUtil.getDeviceId(this.context);

        baseObject.put("deviceId", deviceId);

        if (licenseType == BuildFlavor.LicenseType.GOOGLE) {
            baseObject.put("lvlResponseData", policyResponseData);
            baseObject.put("lvlSignature", policySignature);
        } else if (licenseType == BuildFlavor.LicenseType.HMS) {
            baseObject.put("hmsResponseData", policyResponseData);
            baseObject.put("hmsSignature", policySignature);
        } else {
            String licenseKey = null;
            String licenseUsername = null;
            String licensePassword = null;

            if (this.credentials != null) {
                if (this.credentials instanceof SerialCredentials) {
                    licenseKey = ((SerialCredentials) this.credentials).licenseKey;
                } else if (this.credentials instanceof UserCredentials) {
                    licenseUsername = ((UserCredentials) this.credentials).username;
                    licensePassword = ((UserCredentials) this.credentials).password;
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

    private void reflectUserProfileIdentityLinksIfApplicable(@NonNull TriggerSource triggerSource) {
        if (triggerSource != TriggerSource.SYNC && multiDeviceManager.isMultiDeviceActive()) {
            taskCreator.scheduleReflectUserProfileIdentityLinksTask();
        }
    }
}
