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

package ch.threema.storage.models;

import android.content.Context;
import android.text.format.DateUtils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;

public class ContactModel extends Contact implements ReceiverModel {

	private final static Logger logger = LoggingUtil.getThreemaLogger("ContactModel");

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
	@Deprecated public static final String COLUMN_COLOR = "color";
	public static final String COLUMN_ID_COLOR_INDEX = "idColorIndex";
	public static final String COLUMN_AVATAR_EXPIRES = "avatarExpires";
	public static final String COLUMN_IS_WORK = "isWork";
	public static final String COLUMN_TYPE = "type";
	public static final String COLUMN_PROFILE_PIC_BLOB_ID = "profilePicBlobID"; /* the blob ID of the profile pic that was last sent to this contact */
	public static final String COLUMN_DATE_CREATED = "dateCreated"; /* date when this contact was created locally */
	public static final String COLUMN_IS_HIDDEN = "isHidden"; /* whether this contact is visible in the contact list */
	public static final String COLUMN_IS_RESTORED = "isRestored"; /* whether this contact has been restored from a backup and not yet been contacted */
	public static final String COLUMN_IS_ARCHIVED = "isArchived"; /* whether this contact has been archived by user */
	public static final String COLUMN_READ_RECEIPTS = "readReceipts"; /* whether read receipts should be sent to this contact */
	public static final String COLUMN_TYPING_INDICATORS = "typingIndicators"; /* whether typing indicators should be sent to this contact */
	public static final String COLUMN_FORWARD_SECURITY_STATE = "forwardSecurityState"; /* current state of forward security with this contact */

	public static final byte[] NO_PROFILE_PICTURE_BLOB_ID = new byte[0];

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

	/**
	 * Forward Security state constants. Note that these values are only maintained for contacts
	 * with a DH session of version 1.0.
	 */
	public static final int FS_OFF = 0; // last message from this contact did not have FS enabled
	public static final int FS_ON = 1; // last message from this contact was received with FS

	@Retention(RetentionPolicy.SOURCE)
	@IntDef({FS_OFF, FS_ON})
	public @interface ForwardSecurityState {}

	// Timeout for avatars of linked contacts
	public static long DEFAULT_ANDROID_CONTACT_AVATAR_EXPIRY = DateUtils.DAY_IN_MILLIS * 14;

	private String publicNickName;
	private State state;
	private String androidContactId;
	private String threemaAndroidContactId;
	private boolean isSynchronized;
	private int featureMask;
	private int colorIndex = -1;
	private boolean isWork, isHidden, isRestored, isArchived;
	private Date avatarExpires, dateCreated;
	private byte[] profilePicBlobID;
	private @IdentityType.Type int type;
	private @OverridePolicy int readReceipts, typingIndicators;
	// TODO(ANDR-2452): Remove the forward security state when most of clients support 1.1 anyway
	private int forwardSecurityState;

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

	public int getThemedColor(@NonNull Context context) {
		if (ConfigUtils.isTheDarkSide(context)) {
			return getColorDark();
		} else {
			return getColorLight();
		}
	}

	/**
	 * Call this to set id color index. If this has not been called or the value is negative and the
	 * color is being accessed, the hash will be recomputed to get the color index.
	 *
	 * @param colorIndex the id color index
	 * @return this contact model
	 */
	public ContactModel setIdColorIndex(int colorIndex) {
		this.colorIndex = colorIndex;
		return this;
	}

	/**
	 * Get the id color index. If the value is not initialized, this object will be updated and the
	 * computed value will be returned.
	 *
	 * @return the id color index of the contact
	 */
	public int getIdColorIndex() {
		if (this.colorIndex < 0) {
			initializeIdColor();
		}
		return colorIndex;
	}

	/**
	 * Compute the sha 256 hash of this identity and set the color index accordingly.
	 */
	public void initializeIdColor() {
		int firstByte = computeIdColorFirstByte();
		if (firstByte < 0) {
			colorIndex = -1;
		} else {
			colorIndex = ColorUtil.getInstance().getIDColorIndex((byte) firstByte);
		}
	}

	/**
	 * Get the first byte of the id color hash. If there was an error computing the hash, a negative value is returned.
	 *
	 * @return the first byte of the id color hash
	 */
	private int computeIdColorFirstByte() {
		try {
			return ((int) MessageDigest.getInstance("SHA-256").digest(getIdentity().getBytes(StandardCharsets.UTF_8))[0]) & 0xFF;
		} catch (NoSuchAlgorithmException e) {
			logger.error("Could not find hashing algorithm for id color", e);
			return -1;
		}
	}

	/**
	 * Get the light variant of the id color.
	 *
	 * @return the light id color
	 */
	public int getColorLight() {
		if (colorIndex < 0) {
			initializeIdColor();
		}
		return ColorUtil.getInstance().getIDColorLight(colorIndex);
	}

	/**
	 * Get the dark variant of the id color.
	 *
	 * @return the dark id color
	 */
	public int getColorDark() {
		if (colorIndex < 0) {
			initializeIdColor();
		}
		return ColorUtil.getInstance().getIDColorDark(colorIndex);
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

	/**
	 * Get the BlobId of the latest profile picture that was sent to this contact.
	 *
	 * @return The blobId of the latest profile-picture sent to this contact, {@code null} if no
	 *      profile-picture has been sent or {@code new byte[0]} if a delete-profile-picture message has been sent
	 */
	@Nullable
	public byte[] getProfilePicBlobID() {
		return profilePicBlobID;
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

	/**
	 * Set the BlobId of the latest profile picture that was sent to this contact.
	 *
	 * @param profilePicBlobID The blobId of the latest profile-picture sent to this contact, {@code null} if no
	 *      profile-picture has been sent or {@code new byte[0]} if a delete-profile-picture message has been sent
	 */
	public ContactModel setProfilePicBlobID(@Nullable byte[] profilePicBlobID) {
		this.profilePicBlobID = profilePicBlobID;
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

	/**
	 * Get the forward security state of this contact. Note that these states are only maintained
	 * for contacts with a DH session of version 1.0.
	 * TODO(ANDR-2452): Remove the forward security state when most of clients support 1.1 anyway
	 *
	 * @return the forward security state
	 */
	@ForwardSecurityState
	public int getForwardSecurityState() {
		return forwardSecurityState;
	}

	/**
	 * Set the forward security state of this contact. Note that these states are only maintained
	 * for contacts with a DH session of version 1.0.
	 * TODO(ANDR-2452): Remove the forward security state when most of clients support 1.1 anyway
	 *
	 * @param forwardSecurityState the forward security state
	 * @return this contact model
	 */
	public ContactModel setForwardSecurityState(@ForwardSecurityState int forwardSecurityState) {
		this.forwardSecurityState = forwardSecurityState;
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
			this.colorIndex,
			this.state,
			this.featureMask,
			this.avatarExpires,
			this.isWork,
			this.profilePicBlobID,
			this.type,
			this.dateCreated,
			this.isHidden,
			this.isRestored,
			this.isArchived,
			this.readReceipts,
			this.typingIndicators,
			this.forwardSecurityState
		};
	}

	@Override
	public String toString() {
		return "contact " + this.getIdentity() + ", " + this.getFirstName() + " " + this.getLastName();
	}
}


