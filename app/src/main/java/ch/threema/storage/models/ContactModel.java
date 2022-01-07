/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.storage.models;

import android.text.format.DateUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;

public class ContactModel extends Contact implements ReceiverModel {

	public static final String TABLE = "contacts";
	public static final String COLUMN_IDENTITY = "identity";
	public static final String COLUMN_PUBLIC_KEY = "publicKey";
	public static final String COLUMN_FIRST_NAME = "firstName";
	public static final String COLUMN_LAST_NAME = "lastName";
	public static final String COLUMN_PUBLIC_NICK_NAME = "publicNickName";
	public static final String COLUMN_VERIFICATION_LEVEL = "verificationLevel";
	public static final String COLUMN_ANDROID_CONTACT_LOOKUP_KEY = "androidContactId"; /* The complete lookup key (consisting of key and ID) of the android contact this contact is linked with */
	@Deprecated	public static final String COLUMN_THREEMA_ANDROID_CONTACT_ID = "threemaAndroidContactId";
	@Deprecated public static final String COLUMN_IS_SYNCHRONIZED = "isSynchronized";
	public static final String COLUMN_FEATURE_LEVEL = "featureLevel";
	public static final String COLUMN_STATE = "state";
	public static final String COLUMN_COLOR = "color";
	public static final String COLUMN_AVATAR_EXPIRES = "avatarExpires";
	public static final String COLUMN_IS_WORK = "isWork";
	public static final String COLUMN_TYPE = "type";
	public static final String COLUMN_PROFILE_PIC_SENT_DATE = "profilePicSent"; /* date when profile pic was last sent to this contact */
	public static final String COLUMN_DATE_CREATED = "dateCreated"; /* date when this contact was created locally */
	public static final String COLUMN_IS_HIDDEN = "isHidden"; /* whether this contact is visible in the contact list */
	public static final String COLUMN_IS_RESTORED = "isRestored"; /* whether this contact has been restored from a backup and not yet been contacted */
	public static final String COLUMN_IS_ARCHIVED = "isArchived"; /* whether this contact has been archived by user */
	public static final String COLUMN_READ_RECEIPTS = "readReceipts"; /* whether read receipts should be sent to this contact */
	public static final String COLUMN_TYPING_INDICATORS = "typingIndicators"; /* whether typing indicators should be sent to this contact */

	public enum State {
		/**
		 * Contact is active.
		 */
		ACTIVE,
		/**
		 * Contact is inactive.
		 */
		INACTIVE,
		/**
		 * Contact does not have a valid Threema-ID, or the ID was revoked.
		 */
		INVALID
	}

	/**
	 * Policy for sending read receipts or typing indicators
	 */
	public static final int DEFAULT = 0; // use the global setting
	public static final int SEND = 1; // always send, regardless of global setting
	public static final int DONT_SEND = 2; // never send

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({DEFAULT, SEND, DONT_SEND})
	public @interface OverridePolicy {}

	// Timeout for avatars of linked contacts
	public static long DEFAULT_ANDROID_CONTACT_AVATAR_EXPIRY = DateUtils.DAY_IN_MILLIS * 14;

	private String publicNickName;
	private State state;
	private String androidContactId;
	private String threemaAndroidContactId;
	private boolean isSynchronized;
	private int featureMask;
	private int color;
	private boolean isWork, isHidden, isRestored, isArchived;
	private Date avatarExpires, profilePicSent, dateCreated;
	private @IdentityType.Type int type;
	private @OverridePolicy int readReceipts, typingIndicators;

	public ContactModel(String identity, byte[] publicKey) {
		super(identity, publicKey);
	}

	public ContactModel(@NonNull Contact contact) {
		super(contact.getIdentity(), contact.getPublicKey());
		this.setFirstName(contact.getFirstName());
		this.setLastName(contact.getLastName());
		this.setVerificationLevel(contact.getVerificationLevel());
	}

	/**
	 * Get the complete lookup key (consisting of key with appended ID) of the android contact this contact is linked with
	 * @return lookup key to be used for a contact lookup
	 */
	@Nullable
	public String getAndroidContactLookupKey() {
		return androidContactId;
	}

	public ContactModel setAndroidContactLookupKey(String androidContactId) {
		this.androidContactId = androidContactId;
		// degrade verification level as this contact is no longer connected to an address book contact
		if (androidContactId == null && getVerificationLevel() == VerificationLevel.SERVER_VERIFIED) {
			setVerificationLevel(VerificationLevel.UNVERIFIED);
		}
		return this;
	}

	public ContactModel setName(String firstName, String lastName) {
		this.setLastName(lastName);
		this.setFirstName(firstName);
		return this;
	}

	public String getPublicNickName() {
		return this.publicNickName;
	}

	public ContactModel setPublicNickName(String publicNickName) {
		this.publicNickName = publicNickName;
		return this;
	}

	/**
	 * Deprecated. Do not use. Used to store the ID or lookup key of the raw contact created by Threema.
	 * @return
	 */
	@Deprecated
	public String getThreemaAndroidContactId() {
		return this.threemaAndroidContactId;
	}

	/**
	 * Deprecated. Do not use. Used to store the ID or lookup key of the raw contact created by Threema.
	 * @return
	 */
	@Deprecated
	public ContactModel setThreemaAndroidContactId(String id) {
		this.threemaAndroidContactId = id;
		return this;
	}

	/**
	 * Whether the contact is synchronized with the address book, i.e. the information came from an automatic sync
	 * or it was manually linked
	 * @return true if the contact in androidContactId was automatically synced, false if it was manually linked or not linked at all.
	 *
	 * @Deprecated Use getAndroidContactLookupKey() != null to determine if the contact is synchronized with the address book
	 */
	@Deprecated
	public boolean isSynchronized() {
		return this.isSynchronized;
	}

	/**
	 * Sets whether this contact is synchronized with the address book, either from an automatic sync or manually
	 * @param isSynchronized
	 * @return ContactModel for a builder
	 *
	 * @Deprecated Use setAndroidContactLookupKey()
	 */
	@Deprecated
	public ContactModel setIsSynchronized(boolean isSynchronized) {
		this.isSynchronized = isSynchronized;
		return this;
	}

	public int getColor() {
		return color;
	}

	public ContactModel setColor(int color) {
		this.color = color;
		return this;
	}

	public int getFeatureMask() {
		return featureMask;
	}

	public ContactModel setFeatureMask(int featureMask) {
		this.featureMask = featureMask;
		return this;
	}

	public State getState() {
		return this.state;
	}

	public ContactModel setState(State state) {
		this.state = state;
		return this;
	}

	public Date getAvatarExpires() {
		return avatarExpires;
	}

	public ContactModel setAvatarExpires(Date avatarExpires) {
		this.avatarExpires = avatarExpires;
		return this;
	}

	/**
	 * Set this flag, if the contact is "work verified", i.e. has been added to the contact list in the management cockpit
	 * "work verified" contacts are symbolized by a blue verification level
	 * @param isWork true if the contact is "work verified", false otherwise
	 * @return ContactModel for chaining calls
	 */
	public ContactModel setIsWork(boolean isWork) {
		this.isWork = isWork;
		return this;
	}

	/**
	 * Check whether the contact is "work verified", i.e. has been added to the contact list in the management cockpit
	 * @return true if the contact is "work verified", false otherwise
	 */
	public boolean isWork() {
		return this.isWork;
	}

	public Date getProfilePicSentDate() {
		return profilePicSent;
	}

	public @Nullable Date getDateCreated() {
		return dateCreated;
	}

	public ContactModel setIsHidden(boolean isHidden) {
		this.isHidden = isHidden;
		return this;
	}

	public boolean isHidden() {
		return this.isHidden;
	}

	public ContactModel setIsRestored(boolean isRestored) {
		this.isRestored = isRestored;
		return this;
	}

	public boolean isRestored() {
		return this.isRestored;
	}

	public ContactModel setProfilePicSentDate(Date profilePicSent) {
		this.profilePicSent = profilePicSent;
		return this;
	}

	public ContactModel setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
		return this;
	}

	/**
	 * Set the {@link IdentityType} (regular or work).
	 */
	public ContactModel setIdentityType(@IdentityType.Type int type) {
		this.type = type;
		return this;
	}

	/**
	 * Return the {@link IdentityType} (regular or work).
	 */
	public @IdentityType.Type int getIdentityType() {
		return this.type;
	}

	public boolean isArchived() {
		return isArchived;
	}

	public ContactModel setArchived(boolean archived) {
		isArchived = archived;
		return this;
	}

	public @OverridePolicy int getReadReceipts() {
		return readReceipts;
	}

	public ContactModel setReadReceipts(@OverridePolicy int readReceipts) {
		this.readReceipts = readReceipts;
		return this;
	}

	public @OverridePolicy int getTypingIndicators() {
		return typingIndicators;
	}

	public ContactModel setTypingIndicators(@OverridePolicy int typingIndicators) {
		this.typingIndicators = typingIndicators;
		return this;
	}

	public Object[] getModifiedValueCandidates() {
		return new Object[] {
			this.getPublicKey(),
			this.getFirstName(),
			this.getLastName(),
			this.publicNickName,
			this.getVerificationLevel(),
			this.androidContactId,
			this.threemaAndroidContactId,
			this.isSynchronized,
			this.color,
			this.state,
			this.featureMask,
			this.avatarExpires,
			this.isWork,
			this.profilePicSent,
			this.type,
			this.dateCreated,
			this.isHidden,
			this.isRestored,
			this.isArchived,
			this.readReceipts,
			this.typingIndicators
		};
	}

	@Override
	public String toString() {
		return "contact " + this.getIdentity() + ", " + this.getFirstName() + " " + this.getLastName();
	}
}


