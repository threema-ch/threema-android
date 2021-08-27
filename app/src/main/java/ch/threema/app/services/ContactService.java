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

import android.graphics.Bitmap;

import java.io.File;
import java.io.IOException;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.base.ThreemaException;
import ch.threema.base.VerificationLevel;
import ch.threema.client.AbstractMessage;
import ch.threema.client.ContactDeletePhotoMessage;
import ch.threema.client.ContactRequestPhotoMessage;
import ch.threema.client.ContactSetPhotoMessage;
import ch.threema.client.work.WorkContact;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.access.AccessModel;

public interface ContactService extends AvatarService<ContactModel> {

	int ContactVerificationResult_NO_MATCH = 0;
	int ContactVerificationResult_ALREADY_VERIFIED = 1;
	int ContactVerificationResult_VERIFIED = 2;

	String ALL_USERS_PLACEHOLDER_ID = "@@@@@@@@";

	interface ContactProcessor {
		boolean process(ContactModel contactModel);
	}

	interface Filter {

		/**
		 * States filter
		 * @return
		 */
		ContactModel.State[] states();

		/**
		 * @return feature int
		 */
		Integer requiredFeature();

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
	}

	ContactModel getMe();

	List<ContactModel> getAll();
	List<ContactModel> getAll(boolean includeHidden, boolean includeInvalid);

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
	List<ContactModel> getByIdentities(String identities[]);
	List<ContactModel> getByIdentities(List<String> identities);

	List<ContactModel> getIsWork();
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

	void save(ContactModel model);

	/**
	 * save contacts after processing and returning true
	 * @param contactModels
	 * @param contactProcessor
	 */
	int save(List<ContactModel> contactModels, ContactProcessor contactProcessor);

	boolean remove(ContactModel model);
	boolean remove(ContactModel model, boolean removeLink);
	AccessModel getAccess(ContactModel model);

	void setIsTyping(String identity, boolean isTyping) ;
	boolean isTyping(String identity);

	void setActive(String identity);

	int updateContactVerification(String identity, byte[] publicKey);

	Bitmap getAvatar(ContactModel model, boolean highResolution, boolean returnDefaultAvatarIfNone);

	Bitmap getDefaultAvatar(ContactModel contact, boolean highResolution);

	Bitmap getNeutralAvatar(boolean highResolution);

	/**
	 * remove the cached avatar
	 * @param contactModel
	 */
	void clearAvatarCache(ContactModel contactModel);

	/**
	 * Create a contact with the specified identity.
	 *
	 * @param identity The identity string
	 * @param force Force the creation of the contact, even if adding new contacts has been disabled
	 */
	@NonNull
	ContactModel createContactByIdentity(String identity, boolean force) throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException;

	/**
	 * Create a contact with the specified identity.
	 *
	 * @param identity The identity string
	 * @param force Force the creation of the contact, even if adding new contacts has been disabled
	 * @param hiddenDefault Set this to true to hide the contact by default
	 */
	@NonNull
	ContactModel createContactByIdentity(String identity, boolean force, boolean hiddenDefault)	throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException;

	VerificationLevel getInitialVerificationLevel(ContactModel contactModel);

	ContactModel createContactByQRResult(QRCodeService.QRCodeContentResult qrResult) throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException;

	void removeAll();

	ContactMessageReceiver createReceiver(ContactModel contact);

	/**
	 * @param msg latest message with the "newest" public nickname
	 */
	void updatePublicNickName(AbstractMessage msg);

	boolean updateAllContactNamesAndAvatarsFromAndroidContacts();

	void removeAllThreemaContactIds();

	boolean rebuildColors();

	@Deprecated
	int getUniqueId(ContactModel contactModel);
	String getUniqueIdString(ContactModel contactModel);

	String getUniqueIdString(String identity);

	boolean setAvatar(ContactModel contactModel, File temporaryAvatarFile) throws Exception;
	boolean setAvatar(ContactModel contactModel, byte[] avatar) throws Exception;
	boolean removeAvatar(ContactModel contactModel);

	ContactServiceImpl.ContactPhotoUploadResult uploadContactPhoto(Bitmap picture) throws IOException, ThreemaException;
	boolean updateContactPhoto(ContactSetPhotoMessage msg);
	boolean deleteContactPhoto(ContactDeletePhotoMessage msg);
	boolean requestContactPhoto(ContactRequestPhotoMessage msg);

	ContactModel createContactModelByIdentity(String identity) throws InvalidEntryException;

	boolean showBadge(ContactModel contactModel);

	void setName(ContactModel contact, String firstName, String lastName);
	String getAndroidContactLookupUriString(ContactModel contactModel);
	@Nullable ContactModel addWorkContact(@NonNull WorkContact workContact, @Nullable List<ContactModel> existingWorkContacts);
	void createWorkContact(@NonNull String identity);
}
