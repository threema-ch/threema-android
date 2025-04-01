/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

import java.io.File;
import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.license.LicenseService;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ContactModel;

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
     *
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
    @Nullable
    String setPublicNickname(String publicNickname, @NonNull TriggerSource triggerSource);

    /**
     * Get the user profile picture. If no profile picture is set, then null is returned.
     */
    @Nullable
    byte[] getUserProfilePicture();

    /**
     * Set the user profile picture. Note that this will trigger a user profile sync if multi device
     * is active.
     */
    boolean setUserProfilePicture(@NonNull File userProfilePicture, @NonNull TriggerSource triggerSource);

    /**
     * Set the user profile picture. Note that this will trigger a user profile sync if multi device
     * is active.
     */
    boolean setUserProfilePicture(@NonNull byte[] userProfilePicture, @NonNull TriggerSource triggerSource);

    /**
     * Remove the user profile picture. Note that this will trigger a user profile sync if multi
     * device is active.
     */
    void removeUserProfilePicture(@NonNull TriggerSource triggerSource);

    /**
     * Upload the current profile picture if it hasn't been uploaded recently and get the most
     * recent contact profile picture upload data.
     *
     * @return the most recent profile picture upload data. If the upload failed or the last stored
     * data could not be read, the returned data contains null as blob ID. If there is no profile
     * picture set, the blob ID is {@link ContactModel#NO_PROFILE_PICTURE_BLOB_ID}.
     */
    @NonNull
    @WorkerThread
    ContactService.ProfilePictureUploadData uploadUserProfilePictureOrGetPreviousUploadData();


    boolean restoreIdentity(String backupString, String password) throws Exception;

    boolean restoreIdentity(String identity, byte[] privateKey, byte[] publicKey) throws Exception;

    void setPolicyResponse(String responseData, String signature, int policyErrorCode);

    void setCredentials(LicenseService.Credentials credentials);

    /**
     * Sends the feature mask to the server if it has changed or the last time the feature mask has
     * been sent is more than 24h ago.
     *
     * @return true if the feature mask has been updated (or no update is necessary) and false if
     * an exception has occurred and the feature mask wasn't updated.
     */
    boolean sendFeatureMask();

    /**
     * Set whether the forward security flag should be set in the feature
     * mask. Note that if `ConfigUtils.isForwardSecurityEnabled() == false`,
     * the flag will never be set and the value set via this method is ignored.
     * <br>
     * This will not send the flags to the server. Use {@link #sendFeatureMask()} to
     * update the mask on the server
     * <p>
     * TODO(ANDR-2519): Remove method when fs is allowed with md enabled
     */
    void setForwardSecurityEnabled(boolean isFsEnabled);

    boolean setRevocationKey(String revocationKey);

    Date getLastRevocationKeySet();

    /**
     * Check revocation key
     *
     * @param force
     */
    void checkRevocationKey(boolean force);
}
