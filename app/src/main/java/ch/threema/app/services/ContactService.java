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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.List;

import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.data.models.ContactModelData;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.api.work.WorkContact;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.taskmanager.ActiveTaskCodec;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.access.AccessModel;
import java8.util.function.Consumer;

public interface ContactService extends AvatarService<ContactModel> {

	int ContactVerificationResult_NO_MATCH = 0;
	int ContactVerificationResult_ALREADY_VERIFIED = 1;
	int ContactVerificationResult_VERIFIED = 2;

	String ALL_USERS_PLACEHOLDER_ID = "@@@@@@@@";

    interface ContactProcessor {
		boolean process(ContactModel contactModel);
	}

	class ProfilePictureUploadData {
		public byte[] bitmapArray;
		public byte[] blobId;
		public byte[] encryptionKey;
		public int size;
		public long uploadedAt;
	}

	class ProfilePictureSharePolicy {
		public enum Policy { NOBODY, EVERYONE, SOME }

		@NonNull
		private final Policy policy;
		@NonNull
		private final List<String> allowedIdentities;

		ProfilePictureSharePolicy(@NonNull Policy policy, @NonNull List<String> allowedIdentities) {
			this.policy = policy;
			this.allowedIdentities = allowedIdentities;
		}

		@NonNull
		public Policy getPolicy() {
			return policy;
		}

		@NonNull
		public List<String> getAllowedIdentities() {
			return allowedIdentities;
		}
	}

	class ForwardSecuritySessionState {
		private enum ForwardSecurityState {
			NO_SESSION,
			UNSUPPORTED_BY_REMOTE,
			L20,
			R20,
			R24,
			RL44,
		}

		private final ForwardSecurityState forwardSecurityState;
		private final DHSession.DHVersions currentDHVersions;

		private ForwardSecuritySessionState(@NonNull ForwardSecurityState forwardSecurityState) {
			this(forwardSecurityState, null);
		}

		private ForwardSecuritySessionState(
			@NonNull ForwardSecurityState forwardSecurityState,
			@Nullable DHSession.DHVersions currentDHVersions
		) {
			this.forwardSecurityState = forwardSecurityState;
			this.currentDHVersions = currentDHVersions;
		}

		protected static ForwardSecuritySessionState noSession() {
			return new ForwardSecuritySessionState(ForwardSecurityState.NO_SESSION);
		}

		protected static ForwardSecuritySessionState unsupportedByRemote() {
			return new ForwardSecuritySessionState(ForwardSecurityState.UNSUPPORTED_BY_REMOTE);
		}

		protected static ForwardSecuritySessionState fromDHState(
			@NonNull DHSession.State state,
			@Nullable DHSession.DHVersions dhVersions
		) {
			switch (state) {
				case L20:
					return new ForwardSecuritySessionState(ForwardSecurityState.L20, dhVersions);
				case R20:
					return new ForwardSecuritySessionState(ForwardSecurityState.R20, dhVersions);
				case R24:
					return new ForwardSecuritySessionState(ForwardSecurityState.R24, dhVersions);
				case RL44:
					return new ForwardSecuritySessionState(ForwardSecurityState.RL44, dhVersions);
				default:
					throw new IllegalStateException("No such dh state: " + state);
			}
		}

		@NonNull
		@Override
		public String toString() {
			switch (forwardSecurityState) {
				case NO_SESSION:
					return "No session";
				case L20:
					return "L20 " + currentDHVersions;
				case R20:
					return "R20 " + currentDHVersions;
				case R24:
					return "R24 " + currentDHVersions;
				case RL44:
					return "RL44 " + currentDHVersions;
				case UNSUPPORTED_BY_REMOTE:
					return "Unsupported by remote";
				default:
					return "Unknown state";
			}
		}
	}

	interface Filter {

		/**
		 * States filter
		 */
		ContactModel.State[] states();

		/**
		 * @return feature int
		 */
		Long requiredFeature();

		/**
		 * Update feature Level of Contacts.
		 *
		 * NOTE: The feature level will only be fetched if `requiredFeature()` is set
		 * and for contacts that don't have that feature.
		 */
		Boolean fetchMissingFeatureLevel();

		/**
		 * Include own identity
		 */
		Boolean includeMyself();

		/**
		 * include hidden contacts
		 */
		Boolean includeHidden();

		/*
		 * Limit to contacts with individual settings fro read receipts and typing indicators
		 */
		Boolean onlyWithReceiptSettings();
	}

	ContactModel getMe();

	/**
	 * The contact selection to include or exclude invalid contacts.
	 */
	enum ContactSelection {
		/**
		 * Includes invalid contacts. Note that in the methods {@link #getAllDisplayed(ContactSelection)}
		 * and {@link #getAllDisplayedWork(ContactSelection)}, this may be overridden by the
		 * preferences.
		 */
		INCLUDE_INVALID,
		/**
		 * Don't include invalid contacts.
		 */
		EXCLUDE_INVALID,
	}

	/**
	 * Get all contacts (including work contacts) depending on the preference to display inactive
	 * contacts. If inactive contacts are hidden by the preference, then invalid contacts are not
	 * included in this list neither. If inactive contacts should be shown according to the
	 * preference, then depending on the argument, inactive and invalid contacts may be included.
	 *
	 * Note that the result does not include hidden contacts.
	 *
	 * This list also includes work contacts.
	 *
	 * If a list of contacts is sought without depending on the inactive contacts preference, the
	 * method {@link #find(Filter)} can be used.
	 *
	 * @param contactSelection the option to include or exclude invalid contacts
	 * @return a list of the contact models
	 */
	@NonNull
	List<ContactModel> getAllDisplayed(@NonNull ContactSelection contactSelection);

	/**
	 * Get all contacts. This does not depend on any user preferences. Invalid and hidden contacts
	 * are included as well.
	 *
	 * @return a list of all contact models
	 */
	@NonNull
	List<ContactModel> getAll();

	/**
	 * Get a list of contact models based on the given filter.
	 *
	 * @param filter the filter that is applied to the result
	 * @return a list of contact models
	 * @see #getAllDisplayed
	 * @see #getAllDisplayedWork
	 */
	@NonNull
	List<ContactModel> find(Filter filter);

	@Nullable
	ContactModel getByLookupKey(String lookupKey);

	@Nullable
	ContactModel getByIdentity(@Nullable String identity);

	/**
	 * Return the contact model for the specified identity. If the contact does not exist yet, create it.
	 *
	 * @param identity The identity string
	 * @param force Force the creation of the contact, even if adding new contacts has been disabled
	 */
	@NonNull
	ContactModel getOrCreateByIdentity(String identity, boolean force) throws EntryAlreadyExistsException, InvalidEntryException, PolicyViolationException;
	List<ContactModel> getByIdentities(String[] identities);
	List<ContactModel> getByIdentities(List<String> identities);

	/**
	 * Get all work contacts depending on the preference to display inactive contacts. If inactive
	 * contacts are hidden by the preference, then invalid contacts are not included in this list
	 * neither. If inactive contacts should be shown according to the preference, then depending on
	 * the contact selection argument, invalid contacts may be included.
	 *
	 * Note that this does not include hidden contacts.
	 *
	 * If a list of contacts is sought without depending on the inactive contacts preference, the
	 * method {@link #find(Filter)} can be used.
	 *
	 * @param contactSelection the states that should be included (if enabled in preferences)
	 * @return a list of the contact models
	 */
	@NonNull
	List<ContactModel> getAllDisplayedWork(ContactSelection contactSelection);

	/**
	 * Get all work contacts. This does not depend on any user preferences. Invalid and hidden
	 * contacts are also included.
	 *
	 * @return a list of the work contact models
	 */
	@NonNull
	List<ContactModel> getAllWork();

	int countIsWork();

	List<ContactModel> getCanReceiveProfilePics();
	List<String> getSynchronizedIdentities();

	@Nullable
	List<String> getIdentitiesByVerificationLevel(VerificationLevel verificationLevel);

	@Nullable
	ContactModel getByPublicKey(byte[] publicKey);

	void setIsHidden(String identity, boolean hidden);
	boolean getIsHidden(String identity);

	void setIsArchived(String identity, boolean archived);

	/**
	 * Set the `lastUpdate` field of the specified contact to the current date.
	 *
	 * This will also save the model and notify listeners.
	 */
	void bumpLastUpdate(@NonNull String identity);

	/**
	 * Clear the `lastUpdate` field of the specified contact.
	 *
	 * This will result in the conversation being removed from the conversation list.
	 *
	 * Save the model and notify listeners.
	 */
	void clearLastUpdate(@NonNull String identity);

	void save(@NonNull ContactModel model);

	/**
	 * save contacts after processing and returning true
	 */
	int save(List<ContactModel> contactModels, ContactProcessor contactProcessor);

	boolean remove(ContactModel model);
	boolean remove(ContactModel model, boolean removeLink);
	AccessModel getAccess(ContactModel model);

	void setIsTyping(String identity, boolean isTyping) ;
	boolean isTyping(String identity);

	/**
	 * Send a typing indicator if the preferences do not prevent that.
	 *
	 * @param toIdentity the identity that should receive the typing indicator
	 * @param isTyping   whether the user is typing or not
	 */
	void sendTypingIndicator(String toIdentity, boolean isTyping);

	void setActive(@Nullable String identity);

	int updateContactVerification(String identity, byte[] publicKey);

	/**
	 * Create a contact with the specified identity.
	 *
	 * @param identity The identity string
	 * @param force Force the creation of the contact, even if adding new contacts has been disabled
	 */
	@NonNull
	ContactModel createContactByIdentity(@NonNull String identity, boolean force) throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException;

	/**
	 * Create a contact with the specified identity.
	 *
	 * @param identity The identity string
	 * @param force Force the creation of the contact, even if adding new contacts has been disabled
	 * @param acquaintanceLevel The acquaintance level of the new contact
	 */
	@NonNull ContactModel createContactByIdentity(@NonNull String identity, boolean force, @NonNull ContactModel.AcquaintanceLevel acquaintanceLevel) throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException;

	/**
	 * Create (optionally hidden) contacts for all provided identities. Note that contacts are also created when block
	 * unknown is active or th_disable_add_contact is set to true.
	 * Contacts are added with acquaintance level 'group'.
	 * There is no contact created for an identity if
	 *  - it is the user's identity
	 *  - there is already a contact for that identity
	 *  - the identity public key cannot be fetched (404)
	 */
	void createGroupContactsByIdentities(@NonNull List<String> identities);

	VerificationLevel getInitialVerificationLevel(ContactModel contactModel);

	void removeAll();

	/**
	 * Create a new message receiver for the specified contact model.
	 */
	@NonNull ContactMessageReceiver createReceiver(ContactModel contact);

	/**
	 * @param msg latest message with the "newest" public nickname
	 */
	void updatePublicNickName(@NonNull AbstractMessage msg);

	boolean updateAllContactNamesFromAndroidContacts();

	void removeAllSystemContactLinks();

	@Deprecated
	int getUniqueId(@Nullable ContactModel contactModel);
	@Deprecated
	int getUniqueId(@NonNull String identity);
	String getUniqueIdString(ContactModel contactModel);

	String getUniqueIdString(String identity);

	boolean setAvatar(@Nullable ContactModel contactModel, @Nullable File temporaryAvatarFile) throws Exception;
	boolean setAvatar(@NonNull String identity, @Nullable File temporaryAvatarFile) throws Exception;
	boolean setAvatar(ContactModel contactModel, byte[] avatar) throws Exception;
	boolean removeAvatar(ContactModel contactModel);
	void clearAvatarCache(@NonNull String identity);

	@NonNull
	ProfilePictureSharePolicy getProfilePictureSharePolicy();

	/**
	 * Check whether the app settings allow the profile picture to be sent to the contact. Note that
	 * this method does <b>not</b> check whether the contact is a gateway ID or ECHOECHO.
	 *
	 * @return {@code true} if the profile picture could be sent, {@code false} otherwise
	 */
	boolean isContactAllowedToReceiveProfilePicture(@NonNull ContactModel contactModel);

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
	ProfilePictureUploadData getUpdatedProfilePictureUploadData();

	/**
	 * Reset the date of the last profile picture distribution date of the given contact.
	 */
	void resetContactPhotoSentState(@NonNull ContactModel contactModel);

	ContactModel createContactModelByIdentity(String identity) throws InvalidEntryException;

	boolean showBadge(@Nullable ContactModel contactModel);
	boolean showBadge(@NonNull ContactModelData contactModelData);

	String getAndroidContactLookupUriString(ContactModel contactModel);
	@Nullable ContactModel addWorkContact(@NonNull WorkContact workContact, @Nullable List<ContactModel> existingWorkContacts);

	/**
	 * Remove the specified contact from the contact cache.
	 */
	void removeFromCache(@NonNull String identity);

	/**
	 * Fetch contact if not available locally. There are different steps executed to get the public
	 * key of the identity. As soon as the public key has been fetched, the steps are aborted and
	 * the contact is either saved or cached.
	 * <ul>
	 *     <li>Check if the contact is a special contact.</li>
	 *     <li>Check if the contact is cached locally.</li>
	 *     <li>Check if the contact is stored locally.</li>
	 *     <li>On work builds, check if the identity is available in the work package. If it is, a
	 *         work contact is created.</li>
	 *     <li>Contact synchronization is executed (if enabled) for the given contact. Afterwards
	 *         local contacts are checked again.</li>
	 *     <li>The identity is fetched from the server and then cached. Note that this does not
	 *         store the contact permanently.</li>
	 * </ul>
	 * The contact will not be saved locally if it does not exist yet (except for work contacts). It
	 * will only be cached in the contact store, so that for example the message coder can access
	 * the public key to decrypt messages.
	 *
	 * @throws ch.threema.domain.protocol.api.APIConnector.HttpConnectionException when there is a problem with the http connection
	 * @throws ch.threema.domain.protocol.api.APIConnector.NetworkException        when there is a problem with the network
	 * @throws MissingPublicKeyException                                           when there is no public key for this identity
	 */
	@WorkerThread
	void fetchAndCacheContact(@NonNull String identity) throws APIConnector.HttpConnectionException, APIConnector.NetworkException, MissingPublicKeyException;

	@WorkerThread
	boolean resetReceiptsSettings();
	void reportSpam(@NonNull ContactModel spammerContactModel, @Nullable Consumer<Void> onSuccess, @Nullable Consumer<String> onFailure);

	/**
	 * Get the forward security state of a given contact.
	 *
	 * @return the forward security state of a given contact
	 */
	@Nullable
	ForwardSecuritySessionState getForwardSecurityState(
		@NonNull ContactModel contactModel,
		@NonNull ActiveTaskCodec handle
	);

}
