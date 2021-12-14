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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.format.DateUtils;

import com.neilalexander.jnacl.NaCl;

import net.sqlcipher.Cursor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.listeners.ProfileListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.routines.UpdateBusinessAvatarRoutine;
import ch.threema.app.routines.UpdateFeatureLevelRoutine;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.stores.DatabaseContactStore;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base32;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.api.work.WorkContact;
import ch.threema.domain.protocol.blob.BlobLoader;
import ch.threema.domain.protocol.blob.BlobUploader;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.ContactDeletePhotoMessage;
import ch.threema.domain.protocol.csp.messages.ContactRequestPhotoMessage;
import ch.threema.domain.protocol.csp.messages.ContactSetPhotoMessage;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ValidationMessage;
import ch.threema.storage.models.access.AccessModel;

public class ContactServiceImpl implements ContactService {
	private static final Logger logger = LoggerFactory.getLogger(ContactServiceImpl.class);

	private static final int TYPING_RECEIVE_TIMEOUT = (int) DateUtils.MINUTE_IN_MILLIS;

	private final Context context;
	private final AvatarCacheService avatarCacheService;
	private final DatabaseContactStore contactStore;
	private final DatabaseServiceNew databaseServiceNew;
	private final DeviceService deviceService;
	private final UserService userService;
	private final MessageQueue messageQueue;
	private final IdentityStore identityStore;
	private final PreferenceService preferenceService;
	private final Map<String, ContactModel> contactModelCache;
	private final IdListService blackListIdentityService, profilePicRecipientsService;
	private final DeadlineListService mutedChatsListService;
	private final DeadlineListService hiddenChatsListService;
	private final RingtoneService ringtoneService;
	private final FileService fileService;
	private final ApiService apiService;
	private final WallpaperService wallpaperService;
	private final LicenseService licenseService;
	private final APIConnector apiConnector;
	private final Timer typingTimer;
	private final Map<String,TimerTask> typingTimerTasks;
	private final VectorDrawableCompat contactDefaultAvatar;
	private final int avatarSizeSmall;

	private final List<String> typingIdentities = new ArrayList<>();

	private ContactModel me;

	// These are public keys of identities that will be immediately trusted (three green dots)
	private final static byte[][] TRUSTED_PUBLIC_KEYS = {
		new byte[] { // *THREEMA
			58, 56, 101, 12, 104, 20, 53, -67, 31, -72, 73, -114, 33, 58, 41, 25,
			-80, -109, -120, -11, -128, 58, -92, 70, 64, -32, -9, 6, 50, 106, -122, 92,
		},
		new byte[] { // *SUPPORT
			15, -108, 77, 24, 50, 75, 33, 50, -58, 29, -114, 64, -81, -50, 96, -96,
			-21, -41, 1, -69, 17, -24, -101, -23, 73, 114, -44, 34, -98, -108, 114, 42,
		},
		new byte[]{ // *MY3DATA
			59, 1, -123, 79, 36, 115, 110, 45, 13, 45, -61, -121, -22, -14, -64, 39,
			60, 80, 73, 5, 33, 71, 19, 35, 105, -65, 57, 96, -48, -96, -65, 2
		}
	};

	static class ContactPhotoUploadResult {
		public byte[] bitmapArray;
		public byte[] blobId;
		public byte[] encryptionKey;
		public int size;
	}

	public ContactServiceImpl(
			Context context,
			DatabaseContactStore contactStore,
			AvatarCacheService avatarCacheService,
			DatabaseServiceNew databaseServiceNew,
			DeviceService deviceService,
			UserService userService,
			MessageQueue messageQueue,
			IdentityStore identityStore,
			PreferenceService preferenceService,
			IdListService blackListIdentityService,
			IdListService profilePicRecipientsService,
			RingtoneService ringtoneService,
			DeadlineListService mutedChatsListService,
			DeadlineListService hiddenChatsListService,
			FileService fileService,
			CacheService cacheService,
			ApiService apiService,
			WallpaperService wallpaperService,
			LicenseService licenseService,
			APIConnector apiConnector) {

		this.context = context;
		this.avatarCacheService = avatarCacheService;
		this.contactStore = contactStore;
		this.databaseServiceNew = databaseServiceNew;
		this.deviceService = deviceService;
		this.userService = userService;
		this.messageQueue = messageQueue;
		this.identityStore = identityStore;
		this.preferenceService = preferenceService;
		this.blackListIdentityService = blackListIdentityService;
		this.profilePicRecipientsService = profilePicRecipientsService;
		this.ringtoneService = ringtoneService;
		this.mutedChatsListService = mutedChatsListService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.fileService = fileService;
		this.apiService = apiService;
		this.wallpaperService = wallpaperService;
		this.licenseService = licenseService;
		this.apiConnector = apiConnector;
		this.typingTimer = new Timer();
		this.typingTimerTasks = new HashMap<>();
		this.contactModelCache = cacheService.getContactModelCache();
		this.contactDefaultAvatar = VectorDrawableCompat.create(context.getResources(), R.drawable.ic_contact, null);
		this.avatarSizeSmall = context.getResources().getDimensionPixelSize(R.dimen.avatar_size_small);
	}

	@Override
	public ContactModel getMe() {
		if(this.me == null && this.userService.getIdentity() != null) {
			this.me = new ContactModel(
					this.userService.getIdentity(),
					this.userService.getPublicKey()
			);
			this.me.setState(ContactModel.State.ACTIVE);
			this.me.setFirstName(context.getString(R.string.me_myself_and_i));
			this.me.setVerificationLevel(VerificationLevel.FULLY_VERIFIED);
			this.me.setFeatureMask(-1);
		}

		return this.me;
	}

	@Override
	public List<ContactModel> getAll() {
		return getAll(false, true);
	}

	@Override
	public List<ContactModel> getAll(final boolean includeHiddenContacts, final boolean includeInvalid) {
		return this.find(new Filter() {
			@Override
			public ContactModel.State[] states() {
				if (preferenceService.showInactiveContacts()) {
					if (includeInvalid) {
						return null;
					} else {
						// do not show contacts with INVALID state
						return new ContactModel.State[]{ContactModel.State.ACTIVE, ContactModel.State.INACTIVE};
					}
				} else {
					return new ContactModel.State[]{ContactModel.State.ACTIVE};
				}
			}

			@Override
			public Integer requiredFeature() {
				return null;
			}

			@Override
			public Boolean fetchMissingFeatureLevel() {
				return null;
			}

			@Override
			public Boolean includeMyself() {
				return false;
			}

			@Override
			public Boolean includeHidden() {
				return includeHiddenContacts;
			}

			@Override
			public Boolean onlyWithReceiptSettings() {
				return false;
			}
		});
	}

	@Override
	public List<ContactModel> find(Filter filter) {
		ContactModelFactory contactModelFactory = this.databaseServiceNew.getContactModelFactory();
		//TODO: move this to database factory!
		QueryBuilder queryBuilder = new QueryBuilder();
		List<String> placeholders = new ArrayList<>();

		List<ContactModel> result;
		if(filter != null) {
			ContactModel.State[] filterStates = filter.states();
			if(filterStates != null && filterStates.length > 0) {

				//dirty, add placeholder should be added to makePlaceholders
				queryBuilder.appendWhere(ContactModel.COLUMN_STATE + " IN (" + DatabaseUtil.makePlaceholders(filterStates.length) + ")");
				for(ContactModel.State s: filterStates) {
					placeholders.add(s.toString());
				}
			}

			if (!filter.includeHidden()) {
				queryBuilder.appendWhere(ContactModel.COLUMN_IS_HIDDEN + "=0");
			}

			if (!filter.includeMyself() && getMe() != null) {
				queryBuilder.appendWhere(ContactModel.COLUMN_IDENTITY + "!=?");
				placeholders.add(getMe().getIdentity());
			}

			if (filter.onlyWithReceiptSettings()) {
				queryBuilder.appendWhere(ContactModel.COLUMN_TYPING_INDICATORS + " !=0 OR " + ContactModel.COLUMN_READ_RECEIPTS + " !=0");
			}

			result = contactModelFactory.convert
					(
							queryBuilder,
							placeholders.toArray(new String[placeholders.size()]),
							null
					);
		}
		else {
			result = contactModelFactory.convert
					(
							queryBuilder,
							placeholders.toArray(new String[placeholders.size()]),
							null
					);
		}

		// sort
		final boolean sortOrderFirstName = preferenceService.isContactListSortingFirstName();
		final Collator collator = Collator.getInstance();
		collator.setStrength(Collator.PRIMARY);

		Collections.sort(result, new Comparator<ContactModel>() {
			@Override
			public int compare(ContactModel contactModel1, ContactModel contactModel2) {
				return collator.compare(getSortKey(contactModel1, sortOrderFirstName), getSortKey(contactModel2, sortOrderFirstName));
			}
		});

		if(filter != null) {

			final Integer feature = filter.requiredFeature();

			//update feature level routine call
			if(feature != null) {
				if (filter.fetchMissingFeatureLevel()) {
					//do not filtering with sql
					UpdateFeatureLevelRoutine routine = new UpdateFeatureLevelRoutine(this,
							this.apiConnector,
							Functional.filter(result, new IPredicateNonNull<ContactModel>() {
								@Override
								public boolean apply(@NonNull ContactModel contactModel) {
									return !ThreemaFeature.hasFeature(contactModel.getFeatureMask(), feature);
								}
							}));
					routine.run();
				}

				// Now filter
				result = Functional.filter(result, new IPredicateNonNull<ContactModel>() {
					@Override
					public boolean apply(@NonNull ContactModel contactModel) {
						return ThreemaFeature.hasFeature(contactModel.getFeatureMask(), feature);
					}
				});
			}

		}

		for(int n = 0; n < result.size(); n++) {
			synchronized (this.contactModelCache) {
				String identity = result.get(n).getIdentity();
				if(this.contactModelCache.containsKey(identity)) {
					//replace selected model with the cached one
					//but do not cache the result
					result.set(n, this.contactModelCache.get(identity));
				}
			}
		}
		return result;
	}

	private String getSortKey(ContactModel contactModel, boolean sortOrderFirstName) {
		String key = ContactUtil.getSafeNameString(contactModel, sortOrderFirstName);

		if (contactModel.getIdentity().startsWith("*")) {
			key = "\uFFFF" + key;
		}
		return key;
	}

	@Override
	@Nullable
	public ContactModel getByLookupKey(String lookupKey) {
		if(lookupKey == null) {
			return null;
		}

		return this.contactStore.getContactModelForLookupKey(lookupKey);
	}

	@Override
	@Nullable
	public ContactModel getByIdentity(@Nullable String identity) {
		if(identity == null) {
			return null;
		}

		//return me object
		if(this.getMe() != null && this.getMe().getIdentity().equals(identity)) {
			return this.me;
		}

		synchronized (this.contactModelCache) {
			if(this.contactModelCache.containsKey(identity)) {
				return this.contactModelCache.get(identity);
			}
		}
		return this.cache(this.contactStore.getContactModelForIdentity(identity));
	}

	/**
	 * If a contact for the specified identity exists, return the contactmodel.
	 * Otherwise, create a new contact and return the contactmodel.
	 */
	@Override
	@NonNull
	public ContactModel getOrCreateByIdentity(@NonNull String identity, boolean force)
			throws EntryAlreadyExistsException, InvalidEntryException, PolicyViolationException {
		ContactModel contactModel = this.getByIdentity(identity);
		if (contactModel == null) {
			contactModel = this.createContactByIdentity(identity, force);
		}
		return contactModel;
	}

	private ContactModel cache(ContactModel contactModel) {
		if(contactModel != null) {
			this.contactModelCache.put(contactModel.getIdentity(), contactModel);
		}
		return contactModel;
	}

	@Override
	public List<ContactModel> getByIdentities(String[] identities) {
		List<ContactModel> models = new ArrayList<>();
		for(String s : identities) {
			ContactModel model = this.getByIdentity(s);
			if(model != null) {
				models.add(model);
			}
		}

		return models;
	}

	@Override
	public List<ContactModel> getByIdentities(List<String> identities) {
		List<ContactModel> models = new ArrayList<>();
		for(String s : identities) {
			ContactModel model = this.getByIdentity(s);
			if(model != null) {
				models.add(model);
			}
		}

		return models;
	}

	@Override
	public List<ContactModel> getIsWork() {
		return Functional.filter(this.find(null), new IPredicateNonNull<ContactModel>() {
			@Override
			public boolean apply(@NonNull ContactModel type) {
				return type.isWork();
			}
		});
	}

	@Override
	public int countIsWork() {
		int count = 0;
		Cursor c = this.databaseServiceNew.getReadableDatabase().rawQuery(
			"SELECT COUNT(*) FROM contacts " +
			"WHERE " + ContactModel.COLUMN_IS_WORK + " = 1 " +
			"AND " + ContactModel.COLUMN_IS_HIDDEN + " = 0", null);

		if (c != null) {
			if(c.moveToFirst()) {
				count = c.getInt(0);
			}
			c.close();
		}
		return count;
	}

	@Override
	public List<ContactModel> getCanReceiveProfilePics() {
		return Functional.filter(this.find(new Filter() {
			@Override
			public ContactModel.State[] states() {
				if (preferenceService.showInactiveContacts()) {
					return null;
				}
				return new ContactModel.State[]{ContactModel.State.ACTIVE};
			}

			@Override
			public Integer requiredFeature() {
				return null;
			}

			@Override
			public Boolean fetchMissingFeatureLevel() {
				return null;
			}

			@Override
			public Boolean includeMyself() {
				return false;
			}

			@Override
			public Boolean includeHidden() {
				return false;
			}

			@Override
			public Boolean onlyWithReceiptSettings() {
				return false;
			}
		}), new IPredicateNonNull<ContactModel>() {

			@Override
			public boolean apply(@NonNull ContactModel type) {
				return ContactUtil.canReceiveProfilePics(type);
			}
		});
	}

	@Override
	@Nullable
	public List<String> getSynchronizedIdentities() {
		Cursor c = this.databaseServiceNew.getReadableDatabase().rawQuery("" +
				"SELECT identity FROM contacts " +
				"WHERE androidContactId IS NOT NULL AND androidContactId != ?",
				new String[]{""});

		if(c != null) {
			List<String> identities = new ArrayList<>();
			while(c.moveToNext()) {
				identities.add(c.getString(0));
			}
			c.close();
			return identities;
		}

		return null;
	}

	@Override
	@Nullable
	public List<String> getIdentitiesByVerificationLevel(VerificationLevel verificationLevel) {
		Cursor c = this.databaseServiceNew.getReadableDatabase().rawQuery("" +
				"SELECT identity FROM contacts " +
				"WHERE verificationLevel = ?",
			new String[]{String.valueOf(verificationLevel.getCode())});

		if(c != null) {
			List<String> identities = new ArrayList<>();
			while(c.moveToNext()) {
				identities.add(c.getString(0));
			}
			c.close();
			return identities;
		}

		return null;
	}

	@Override
	@Nullable
	public ContactModel getByPublicKey(byte[] publicKey) {
		return this.contactStore.getContactModelForPublicKey(publicKey);
	}

	@Override
	public void setIsTyping(final String identity, final boolean isTyping) {
		// cancel old timer task
		synchronized (typingTimerTasks) {
			TimerTask oldTimerTask = typingTimerTasks.get(identity);
			if (oldTimerTask != null) {
				oldTimerTask.cancel();
				typingTimerTasks.remove(identity);
			}
		}

		//get the cached model
		final ContactModel contact = this.getByIdentity(identity);
		synchronized (this.typingIdentities) {
			boolean contains = this.typingIdentities.contains(identity);
			if(isTyping) {
				if (!contains) {
					this.typingIdentities.add(identity);
				}
			}
			else {
				if (contains) {
					this.typingIdentities.remove(identity);
				}
			}
		}

		ListenerManager.contactTypingListeners.handle(new ListenerManager.HandleListener<ContactTypingListener>() {
			@Override
			public void handle(ContactTypingListener listener) {
				listener.onContactIsTyping(contact, isTyping);
			}
		});

		// schedule a new timer task to reset typing state after timeout if necessary
		if (isTyping) {
			synchronized (typingTimerTasks) {
				TimerTask newTimerTask = new TimerTask() {
					@Override
					public void run() {
						synchronized (typingIdentities) {
							if(typingIdentities.contains(identity)) {
								typingIdentities.remove(identity);
							}
						}

						ListenerManager.contactTypingListeners.handle(new ListenerManager.HandleListener<ContactTypingListener>() {
							@Override
							public void handle(ContactTypingListener listener) {
								listener.onContactIsTyping(contact, false);
							}
						});

						synchronized (typingTimerTasks) {
							typingTimerTasks.remove(identity);
						}
					}
				};

				typingTimerTasks.put(identity, newTimerTask);
				typingTimer.schedule(newTimerTask, TYPING_RECEIVE_TIMEOUT);
			}
		}
	}

	@Override
	public boolean isTyping(String identity) {
		synchronized (this.typingIdentities) {
			return this.typingIdentities.contains(identity);
		}
	}

	@Override
	public void setActive(String identity) {
		final ContactModel contact = this.getByIdentity(identity);

		if (contact != null && contact.getState() == ContactModel.State.INACTIVE) {
			contact.setState(ContactModel.State.ACTIVE);
		}
	}

	/**
	 * Change hidden status of contact
	 * @param identity
	 * @param hide true if we want to hide the contact, false to unhide
	 */
	@Override
	public void setIsHidden(String identity, boolean hide) {
		final ContactModel contact = this.getByIdentity(identity);

		if (contact != null && contact.isHidden() != hide) {
			//remove from cache
			synchronized (this.contactModelCache) {
				this.contactModelCache.remove(identity);
			}
			this.contactStore.hideContact(contact, hide);
		}
	}

	/**
	 * Get hidden status of contact
	 * @param identity
	 * @return true if contact is hidden from contact list, false otherwise
	 */
	@Override
	public boolean getIsHidden(String identity) {
		final ContactModel contact = this.getByIdentity(identity);
		return (contact != null && contact.isHidden());
	}

	@Override
	public void setIsArchived(String identity, boolean archived) {
		final ContactModel contact = this.getByIdentity(identity);

		if (contact != null && contact.isArchived() != archived) {
			contact.setArchived(archived);
			save(contact);
			// listeners will be fired by save()
		}
	}

	@Override
	public void save(ContactModel contactModel) {
		this.contactStore.addContact(contactModel);
	}

	@Override
	public int save(List<ContactModel> contactModels, ContactProcessor contactProcessor) {
		int savedModels = 0;
		if(TestUtil.required(contactModels, contactProcessor)) {
			for(ContactModel contactModel: contactModels) {
				if (contactProcessor.process(contactModel)) {
					this.save(contactModel);
					savedModels++;
				}
			}
		}
		return savedModels;
	}

	@Override
	public boolean remove(ContactModel model) {
		return this.remove(model, true);
	}

	@Override
	public boolean remove(@NonNull ContactModel model, boolean removeLink) {
		String uniqueIdString = getUniqueIdString(model);

		clearAvatarCache(model);

		AccessModel access = this.getAccess(model);
		if (access.canDelete()) {
			// remove
			this.contactStore.removeContact(model);

			//remove from cache
			synchronized (this.contactModelCache) {
				this.contactModelCache.remove(model.getIdentity());
			}

			this.ringtoneService.removeCustomRingtone(uniqueIdString);
			this.mutedChatsListService.remove(uniqueIdString);
			this.hiddenChatsListService.remove(uniqueIdString);
			this.profilePicRecipientsService.remove(model.getIdentity());
			this.wallpaperService.removeWallpaper(uniqueIdString);
			this.fileService.removeAndroidContactAvatar(model);
		} else {
			// hide contact
			setIsHidden(model.getIdentity(),true);
			// also remove conversation of this contact
			try {
				ConversationService conversationService = ThreemaApplication.getServiceManager().getConversationService();
				conversationService.removed(model);
			} catch (Exception e) {
				logger.error("Exception", e);
			}

		}

		if (removeLink) {
			AndroidContactUtil.getInstance().deleteThreemaRawContact(model);
		}

		return true;
	}

	@NonNull
	@Override
	public AccessModel getAccess(ContactModel model) {
		if(model == null) {
			return new AccessModel() {
				@Override
				public boolean canDelete() {
					return false;
				}

				@Override
				public ValidationMessage[] canNotDeleteReasons() {
					return new ValidationMessage[]{
							new ValidationMessage(
									context.getString(R.string.can_not_delete_contact),
									context.getString(R.string.can_not_delete_not_valid)
							)
					};
				}
			};
		}
		else {
			boolean isInGroup = false;
			Cursor c = this.databaseServiceNew.getReadableDatabase().rawQuery("" +
					"SELECT COUNT(*) FROM m_group g " +
					"INNER JOIN group_member m " +
					"	ON m.groupId = g.id " +
					"WHERE m.identity = ? AND deleted = 0", new String[]{
					model.getIdentity()
			});

			if(c != null) {
				if(c.moveToFirst()) {
					isInGroup = c.getInt(0) > 0;
				}
				c.close();
			}

			if(isInGroup) {
				return new AccessModel() {
					@Override
					public boolean canDelete() {
						return false;
					}

					@Override
					public ValidationMessage[] canNotDeleteReasons() {
						return new ValidationMessage[]{
								new ValidationMessage(
										context.getString(R.string.can_not_delete_contact),
										context.getString(R.string.can_not_delete_contact_until_in_group)
								)
						};
					}
				};
			}
		}

		return new AccessModel() {
			@Override
			public boolean canDelete() {
				return true;
			}

			@Override
			public ValidationMessage[] canNotDeleteReasons() {
				return new ValidationMessage[0];
			}
		};
	}

	@Override
	public int updateContactVerification(String identity, byte[] publicKey) {
		ContactModel c = this.getByIdentity(identity);

		if (c != null) {
			if (Arrays.equals(c.getPublicKey(), publicKey)) {
				if (c.getVerificationLevel() != VerificationLevel.FULLY_VERIFIED) {
					c.setVerificationLevel(VerificationLevel.FULLY_VERIFIED);
					this.save(c);
					return ContactVerificationResult_VERIFIED;
				} else {
					return ContactVerificationResult_ALREADY_VERIFIED;
				}
			}
		}

		return ContactVerificationResult_NO_MATCH;
	}

	@Override
	@Nullable
	public Bitmap getCachedAvatar(ContactModel model) {
		if(model == null) {
			return null;
		}
		return this.avatarCacheService.getContactAvatarLowFromCache(model);
	}

	@Override
	@Nullable
	public Bitmap getAvatar(ContactModel model, boolean highResolution) {
		return getAvatar(model, highResolution, true);
	}

	@Override
	public Bitmap getAvatar(ContactModel contact, boolean highResolution, boolean returnDefaultAvatarIfNone) {
		Bitmap b = null;

		if(contact != null) {

			if (highResolution) {
				b = this.avatarCacheService.getContactAvatarHigh(contact);
			} else {
				b = this.avatarCacheService.getContactAvatarLow(contact);
			}

			//check if a business avatar update is necessary
			if (ContactUtil.isChannelContact(contact) && ContactUtil.isAvatarExpired(contact)) {
				//simple start
				UpdateBusinessAvatarRoutine.startUpdate(contact, this.fileService, this, apiService);
			}
		}

		// return default avatar pic as a last resort
		if (b == null && returnDefaultAvatarIfNone) {
			return getDefaultAvatar(contact, highResolution);
		}
		return b;
	}

	@Override
	public Bitmap getDefaultAvatar(ContactModel contact, boolean highResolution) {
		@ColorInt int color = ColorUtil.getInstance().getCurrentThemeGray(this.context);
		if (avatarCacheService.getDefaultAvatarColored() && (contact != null && contact.getIdentity() != null && !contact.getIdentity().equals(identityStore.getIdentity()))) {
			color = contact.getColor();
		}

		if (highResolution) {
			return this.avatarCacheService.buildHiresDefaultAvatar(color, AvatarCacheService.CONTACT_AVATAR);
		} else {
			return AvatarConverterUtil.getAvatarBitmap(contactDefaultAvatar, color, avatarSizeSmall);
		}
	}

	@Override
	public Bitmap getNeutralAvatar(boolean highResolution) {
		return getDefaultAvatar(null, highResolution);
	}

	@Override
	public void clearAvatarCache(ContactModel contactModel) {
		if(this.avatarCacheService != null) {
			this.avatarCacheService.reset(contactModel);
		}
	}

	@Override
	public @NonNull ContactModel createContactByIdentity(String identity, boolean force) throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException {
		return createContactByIdentity(identity, force, false);
	}

	@Override
	public @NonNull ContactModel createContactByIdentity(String identity, boolean force, boolean hideContactByDefault) throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException {
		if (!force && AppRestrictionUtil.isAddContactDisabled(ThreemaApplication.getAppContext())) {
			throw new PolicyViolationException();
		}

		if (identity.equals(getMe().getIdentity())) {
			throw new InvalidEntryException(R.string.identity_already_exists);
		}

		ContactModel newContact = this.getByIdentity(identity);
		if (newContact == null) {
			// create a new contact
			newContact = this.createContactModelByIdentity(identity);
		} else if (!newContact.isHidden() || hideContactByDefault) {
			throw new EntryAlreadyExistsException(R.string.identity_already_exists);
		}

		// set default hidden status
		newContact.setIsHidden(hideContactByDefault);

		// Set initial verification level
		newContact.setVerificationLevel(getInitialVerificationLevel(newContact));

		this.save(newContact);

		return newContact;
	}

	public VerificationLevel getInitialVerificationLevel(ContactModel contactModel) {
		// Determine whether this is a trusted public key (e.g. for *SUPPORT)
		final byte[] pubKey = contactModel.getPublicKey();
		boolean isTrusted = false;
		for (byte[] trustedKey : TRUSTED_PUBLIC_KEYS) {
			if (Arrays.equals(trustedKey, pubKey)) {
				isTrusted = true;
				break;
			}
		}
		return isTrusted ? VerificationLevel.FULLY_VERIFIED : VerificationLevel.UNVERIFIED;
	}

	@Override
	public ContactModel createContactByQRResult(QRCodeService.QRCodeContentResult qrResult) throws InvalidEntryException, EntryAlreadyExistsException, PolicyViolationException {
		ContactModel newContact = this.createContactByIdentity(qrResult.getIdentity(), false);

		if (newContact == null || !Arrays.equals(newContact.getPublicKey(), qrResult.getPublicKey())) {
			//remove CONTACT!
			this.remove(newContact);
			throw new InvalidEntryException(R.string.invalid_threema_qr_code);
		}

		newContact.setVerificationLevel(VerificationLevel.FULLY_VERIFIED);

		this.save(newContact);

		return newContact;
	}

	@Override
	public void removeAll() {
		for(ContactModel model: this.find(null)) {
			this.remove(model, false);
		}
	}

	@Override
	public ContactMessageReceiver createReceiver(ContactModel contact) {
		return new ContactMessageReceiver(contact,
				this,
				this.databaseServiceNew,
				this.messageQueue,
				this.identityStore,
				this.blackListIdentityService,
				this.apiService);
	}

	private ContactModel getContact(AbstractMessage msg) {
		return this.getByIdentity(msg.getFromIdentity());
	}

	@Override
	public void updatePublicNickName(AbstractMessage msg) {
		if(msg == null) {
			return;
		}

		ContactModel contact = getContact(msg);

		if (contact == null) return;

		if(msg.getPushFromName() != null && msg.getPushFromName().length() > 0 &&
			!msg.getPushFromName().equals(contact.getIdentity()) &&
			!msg.getPushFromName().equals(contact.getPublicNickName())) {
			contact.setPublicNickName(msg.getPushFromName());
			this.save(contact);
		}
	}

	@Override
	@WorkerThread
	public boolean updateAllContactNamesFromAndroidContacts() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
			ContextCompat.checkSelfPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
			return false;
		}

		List<ContactModel> contactModels = this.getAll(true, true);
		if(contactModels != null) {
			for(ContactModel contactModel: contactModels) {
				if(!TestUtil.empty(contactModel.getAndroidContactLookupKey())) {
					try {
						AndroidContactUtil.getInstance().updateNameByAndroidContact(contactModel);
					} catch (ThreemaException e) {
						contactModel.setAndroidContactLookupKey(null);
						logger.error("Unable to update contact name", e);
					}
					this.save(contactModel);
				}
			}
		}
		return true;
	}

	@Override
	public void removeAllSystemContactLinks() {
		for(ContactModel c: this.find(null)) {
			if (c.getAndroidContactLookupKey() != null) {
				c.setAndroidContactLookupKey(null);
				this.save(c);
			}
		}
	}

	@Override
	public boolean rebuildColors() {
		List<ContactModel> models = this.getAll(true, true);
		if(models != null) {
			int[] colors = ColorUtil.getInstance().generateGoogleColorPalette(models.size());
			for(int n = 0; n < colors.length; n++) {
				ContactModel c = models.get(n);
				c.setColor(colors[n]);
				this.save(c);
			}
			return true;
		}
		return false;
	}

	@Override
	@Deprecated
	public int getUniqueId(ContactModel contactModel) {
		if (contactModel != null) {
			return ("c-" + contactModel.getIdentity()).hashCode();
		} else {
			return 0;
		}
	}

	@Override
	public String getUniqueIdString(ContactModel contactModel) {
		if (contactModel != null) {
			return getUniqueIdString(contactModel.getIdentity());
		}
		return "";
	}

	@Override
	public String getUniqueIdString(String identity) {
		if (identity != null) {
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
				messageDigest.update(("c-" + identity).getBytes());
				return Base32.encode(messageDigest.digest());
			} catch (NoSuchAlgorithmException e) {
				//
			}
		}
		return "";
	}

	@Override
	public boolean setAvatar(final ContactModel contactModel, File temporaryAvatarFile) throws Exception {
		if (contactModel != null && temporaryAvatarFile != null) {
			if (this.fileService.writeContactAvatar(contactModel, temporaryAvatarFile)) {
				return this.onAvatarSet(contactModel);
			}
		}
		return false;
	}

	@Override
	public boolean setAvatar(final ContactModel contactModel, byte[] avatar) throws Exception {
		if (contactModel != null && avatar != null) {
			if (this.fileService.writeContactAvatar(contactModel, avatar)) {
				return this.onAvatarSet(contactModel);
			}
		}
		return false;
	}

	private boolean onAvatarSet(final ContactModel contactModel) {
		this.clearAvatarCache(contactModel);

		if (this.userService.isMe(contactModel.getIdentity())) {
			// Update last profile picture change date
			this.preferenceService.setProfilePicLastUpdate(new Date());

			// Notify listeners
			ListenerManager.profileListeners.handle(ProfileListener::onAvatarChanged);
		} else {
			ListenerManager.contactListeners.handle(listener -> listener.onAvatarChanged(contactModel));
		}

		return true;
	}

	@Override
	public boolean removeAvatar(final ContactModel contactModel) {
		if(contactModel != null) {
			if(this.fileService.removeContactAvatar(contactModel)) {
				this.clearAvatarCache(contactModel);

				// Notify listeners
				if (this.userService.isMe(contactModel.getIdentity())) {
					// Update last profile picture change date
					this.preferenceService.setProfilePicLastUpdate(new Date());

					ListenerManager.profileListeners.handle(ProfileListener::onAvatarRemoved);
				}
				ListenerManager.contactListeners.handle(listener -> listener.onAvatarChanged(contactModel));

				return true;
			}
		}
		return false;
	}

	@Override
	public ContactPhotoUploadResult uploadContactPhoto(Bitmap picture) throws IOException, ThreemaException {
		/* only upload blob every 7 days */
		Date uploadDeadline = new Date(preferenceService.getProfilePicUploadDate() + DateUtils.WEEK_IN_MILLIS);
		Date now = new Date();

		ContactPhotoUploadResult result = new ContactPhotoUploadResult();

		if (now.after(uploadDeadline)) {
			logger.info("Uploading profile picture blob");

			SecureRandom rnd = new SecureRandom();
			result.encryptionKey = new byte[NaCl.SYMMKEYBYTES];
			rnd.nextBytes(result.encryptionKey);

			result.bitmapArray = BitmapUtil.bitmapToJpegByteArray(picture);
			byte[] imageData = NaCl.symmetricEncryptData(result.bitmapArray, result.encryptionKey, ProtocolDefines.CONTACT_PHOTO_NONCE);
			BlobUploader blobUploader = this.apiService.createUploader(imageData);
			result.blobId = blobUploader.upload();
			result.size = imageData.length;

			preferenceService.setProfilePicUploadDate(now);
			preferenceService.setProfilePicUploadData(result);
		} else {
			result = preferenceService.getProfilePicUploadData(result);
		}
		return result;
	}


	@Override
	public boolean updateContactPhoto(ContactSetPhotoMessage msg) {
		final ContactModel contactModel = this.getContact(msg);

		if (contactModel != null) {
			BlobLoader blobLoader = this.apiService.createLoader(msg.getBlobId());
			try {
				byte[] encryptedBlob = blobLoader.load(false);

				if (encryptedBlob != null) {
					NaCl.symmetricDecryptDataInplace(encryptedBlob, msg.getEncryptionKey(), ProtocolDefines.CONTACT_PHOTO_NONCE);

					this.fileService.writeContactPhoto(contactModel, encryptedBlob);

					this.avatarCacheService.reset(contactModel);

					ListenerManager.contactListeners.handle(listener -> listener.onAvatarChanged(contactModel));

					return true;
				}
			} catch (Exception e) {
				logger.error("Exception", e);

				if (e instanceof FileNotFoundException) {
					// do not bother trying download again
					return true;
				}
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean deleteContactPhoto(ContactDeletePhotoMessage msg) {
		final ContactModel contactModel = this.getContact(msg);

		if (contactModel != null) {
			fileService.removeContactPhoto(contactModel);

			this.avatarCacheService.reset(contactModel);

			ListenerManager.contactListeners.handle(listener -> listener.onAvatarChanged(contactModel));
		}
		return true;
	}

	@Override
	public boolean requestContactPhoto(ContactRequestPhotoMessage msg) {
		final ContactModel contactModel = this.getContact(msg);

		if (contactModel != null) {
			logger.info("Received request to re-send profile pic by {}", msg.getFromIdentity());

			contactModel.setProfilePicSentDate(new Date(0));
			save(contactModel);
		}
		return true;
	}

	@Override
	public ContactModel createContactModelByIdentity(String identity) throws InvalidEntryException {
		if (identity == null || identity.length() != ProtocolDefines.IDENTITY_LEN) {
			throw new InvalidEntryException(R.string.invalid_threema_id);
		}

		//auto UPPERCASE identity
		identity = identity.toUpperCase();

		//check for existing
		if (this.getByIdentity(identity) != null) {
			throw new InvalidEntryException(R.string.contact_already_exists);
		}

		if (identity.equals(userService.getIdentity())) {
			throw new InvalidEntryException(R.string.contact_already_exists);
		}

		if(!this.deviceService.isOnline()) {
			throw new InvalidEntryException(R.string.connection_error);
		}

		//try to fetch
		byte[] publicKey;
		ContactModel newContact;

		try {
			publicKey = this.contactStore.fetchPublicKeyForIdentity(identity);

			if (publicKey == null) {
				throw new InvalidEntryException(R.string.connection_error);
			}
		} catch (FileNotFoundException e) {
			throw new InvalidEntryException(
				ConfigUtils.isOnPremBuild() && !identity.startsWith(BuildConfig.ONPREM_ID_PREFIX) ?
				R.string.invalid_onprem_id : R.string.invalid_threema_id);
		} catch (ThreemaException e) {
			// contact already exists - shouldn't happen
		}

		newContact = this.getByIdentity(identity);

		if (newContact == null) {
			throw new InvalidEntryException(R.string.invalid_threema_id);
		}
		return newContact;
	}

	@Override
	public boolean showBadge(ContactModel contactModel) {
		if (contactModel != null) {
			if (ConfigUtils.isWorkBuild()) {
				if (userService.isMe(contactModel.getIdentity())) {
					return false;
				}
				return contactModel.getIdentityType() == IdentityType.NORMAL && ContactUtil.canReceiveProfilePics(contactModel);
			} else {
				return contactModel.getIdentityType() == IdentityType.WORK;
			}
		}
		return false;
	}

	@Override
	public void setName(ContactModel contact, String firstName, String lastName) {
		contact.setFirstName(firstName);
		contact.setLastName(lastName);

		synchronized (this.contactModelCache) {
			this.contactModelCache.remove(contact.getIdentity());
		}

		save(contact);
	}

	/**
	 * Get Android contact lookup key Uri in String representation to be used for Notification.Builder.addPerson()
	 * @param contactModel ContactModel to get Uri for
	 * @return Uri of Android contact as a string or null if there's no linked contact or permission to access contacts has not been granted
	 */
	@Nullable
	public String getAndroidContactLookupUriString(ContactModel contactModel) {
		String contactLookupUri = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (ContextCompat.checkSelfPermission(ThreemaApplication.getAppContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
				if (contactModel != null && contactModel.getAndroidContactLookupKey() != null) {
					Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, contactModel.getAndroidContactLookupKey());
					if (lookupUri != null) {
						contactLookupUri = lookupUri.toString();
					}
				}
			}
		}
		return contactLookupUri;
	}

	/**
	 * Create a ContactModel for the provided Work contact. If a ContactModel already exists, it will be updated with the data from the Work API,
	 * namely. name, verification level, work status. If the contact was hidden (i.e. added by a group), it will be visible after this operation
	 * @param workContact WorkContact object for the contact to add
	 * @param existingWorkContacts An optional list of ContactModels. If a ContactModel already exists for workContact, the ContactModel will be removed from this list
	 * @return ContactModel of created or updated contact or null if public key of provided WorkContact was invalid
	 */
	@Override
	@Nullable
	public ContactModel addWorkContact(@NonNull WorkContact workContact, @Nullable List<ContactModel> existingWorkContacts) {
		if (!ConfigUtils.isWorkBuild()) {
			return null;
		}

		if (workContact.publicKey == null || workContact.publicKey.length != NaCl.PUBLICKEYBYTES) {
			// ignore work contact with invalid public key
			return null;
		}

		if (workContact.threemaId != null && workContact.threemaId.equals(getMe().getIdentity())) {
			// do not add our own ID as a contact
			return null;
		}

		ContactModel contactModel = getByIdentity(workContact.threemaId);

		if (contactModel == null) {
			contactModel = new ContactModel(workContact.threemaId, workContact.publicKey);
		} else if (existingWorkContacts != null) {
			// try to remove from list of existing work contacts
			for (int x = 0; x < existingWorkContacts.size(); x++) {
				if (existingWorkContacts.get(x).getIdentity().equals(workContact.threemaId)) {
					existingWorkContacts.remove(x);
					break;
				}
			}
		}

		if (!ContactUtil.isLinked(contactModel)
			&& (workContact.firstName != null
			|| workContact.lastName != null)) {
			contactModel.setFirstName(workContact.firstName);
			contactModel.setLastName(workContact.lastName);
		}
		contactModel.setIsWork(true);
		contactModel.setIsHidden(false);
		if (contactModel.getVerificationLevel() != VerificationLevel.FULLY_VERIFIED) {
			contactModel.setVerificationLevel(VerificationLevel.SERVER_VERIFIED);
		}
		this.save(contactModel);

		return contactModel;
	}

	/**
	 * Check if a contact for the provided identity exists, if not, try to fetch a contact from work api and add it to the contact database
	 * @param identity Identity
	 */
	@Override
	public void createWorkContact(@NonNull String identity) {
		if (!ConfigUtils.isWorkBuild()) {
			return;
		}

		if (contactStore.getPublicKeyForIdentity(identity, false) == null) {
			LicenseService.Credentials credentials = this.licenseService.loadCredentials();
			if ((credentials instanceof UserCredentials)) {
				try {
					List<WorkContact> workContacts = apiConnector.fetchWorkContacts(((UserCredentials) credentials).username, ((UserCredentials) credentials).password, new String[]{identity});
					if (workContacts.size() > 0) {
						WorkContact workContact = workContacts.get(0);
						addWorkContact(workContact, null);
					}
				} catch (Exception e) {
					logger.error("Error fetching work contact", e);
				}
			}
		}
	}

	@Override
	@WorkerThread
	public boolean resetReceiptsSettings() {
		List<ContactModel> contactModels = find(new Filter() {
			@Override
			public ContactModel.State[] states() {
				return new ContactModel.State[]{ContactModel.State.ACTIVE, ContactModel.State.INACTIVE};
			}

			@Override
			public Integer requiredFeature() {
				return null;
			}

			@Override
			public Boolean fetchMissingFeatureLevel() {
				return null;
			}

			@Override
			public Boolean includeMyself() {
				return false;
			}

			@Override
			public Boolean includeHidden() {
				return true;
			}

			@Override
			public Boolean onlyWithReceiptSettings() {
				return true;
			}
		});

		if (contactModels.size() > 0) {
			for (ContactModel contactModel : contactModels) {
				contactModel.setTypingIndicators(ContactModel.DEFAULT);
				contactModel.setReadReceipts(ContactModel.DEFAULT);
				save(contactModel);
			}
			return true;
		}
		return false;
	}
}
