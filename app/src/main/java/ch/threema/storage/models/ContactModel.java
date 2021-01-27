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

package ch.threema.storage.models;

import android.text.format.DateUtils;

import java.util.Date;

import androidx.annotation.Nullable;
import ch.threema.base.Contact;

public class ContactModel extends Contact implements ReceiverModel {

	public static final String TABLE = "contacts";
	public static final String COLUMN_IDENTITY = "identity";
	public static final String COLUMN_PUBLIC_KEY = "publicKey";
	public static final String COLUMN_FIRST_NAME = "firstName";
	public static final String COLUMN_LAST_NAME = "lastName";
	public static final String COLUMN_PUBLIC_NICK_NAME = "publicNickName";
	public static final String COLUMN_VERIFICATION_LEVEL = "verificationLevel";
	public static final String COLUMN_ANDROID_CONTACT_ID= "androidContactId";
	public static final String COLUMN_THREEMA_ANDROID_CONTACT_ID = "threemaAndroidContactId";
	public static final String COLUMN_IS_SYNCHRONIZED= "isSynchronized";
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

	public enum State {
		/**
		 * Deprecated.
		 *
		 * Can probably be removed, but a DB migration must be created to ensure
		 * that there are no more contacts with TEMPORARY state in the database.
		 * */
		TEMPORARY,
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
	private int type;

	public ContactModel(String identity, byte[] publicKey) {
		super(identity, publicKey);
	}


	public String getAndroidContactId() {
		return androidContactId;
	}

	public ContactModel setAndroidContactId(String androidContactId) {
		this.androidContactId = androidContactId;
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

	public String getThreemaAndroidContactId() {
		return this.threemaAndroidContactId;
	}

	public ContactModel setThreemaAndroidContactId(String id) {
		this.threemaAndroidContactId = id;
		return this;
	}

	public boolean isSynchronized() {
		return this.isSynchronized;
	}

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

	public ContactModel setIsWork(boolean isWork) {
		this.isWork = isWork;
		return this;
	}

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

	public ContactModel setType(int type) {
		this.type = type;
		return this;
	}

	public int getType() {
		return this.type;
	}

	public boolean isArchived() {
		return isArchived;
	}

	public ContactModel setArchived(boolean archived) {
		isArchived = archived;
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
			this.isArchived
		};
	}

	@Override
	public String toString() {
		return "contact " + this.getIdentity() + ", " + this.getFirstName() + " " + this.getLastName();
	}
}


