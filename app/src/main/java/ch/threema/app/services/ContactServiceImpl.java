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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.RequestManager;
import com.neilalexander.jnacl.NaCl;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.listeners.ProfileListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.routines.UpdateBusinessAvatarRoutine;
import ch.threema.app.routines.UpdateFeatureLevelRoutine;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.stores.DatabaseContactStore;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ColorUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Base32;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.api.work.WorkContact;
import ch.threema.domain.protocol.blob.BlobLoader;
import ch.threema.domain.protocol.blob.BlobUploader;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.ContactDeleteProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.ContactSetProfilePictureMessage;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.stores.DHSessionStoreException;
import ch.threema.domain.stores.DHSessionStoreInterface;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.DatabaseUtil;
import ch.threema.storage.QueryBuilder;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ValidationMessage;
import ch.threema.storage.models.access.AccessModel;
import java8.util.function.Consumer;
import java8.util.stream.Collectors;
import java8.util.stream.StreamSupport;

import static ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy.CUSTOM_AVATAR;

public class ContactServiceImpl implements ContactService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ContactServiceImpl");

	private static final int TYPING_RECEIVE_TIMEOUT = (int) DateUtils.MINUTE_IN_MILLIS;
	private static final String CONTACT_UID_PREFIX = "c-";

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
	private final ForwardSecurityMessageProcessor fsmp;
	private final Timer typingTimer;
	private final Map<String,TimerTask> typingTimerTasks;

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
		APIConnector apiConnector,
		ForwardSecurityMessageProcessor fsmp) {

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
		this.fsmp = fsmp;
		this.typingTimer = new Timer();
		this.typingTimerTasks = new HashMap<>();
		this.contactModelCache = cacheService.getContactModelCache();
	}

	@Override
	public ContactModel getMe() {
		if(this.me == null && this.userService.getIdentity() != null) {
			this.me = new ContactModel(
					this.userService.getIdentity(),
					this.userService.getPublicKey()
			);
			this.me.setPublicNickName(this.userService.getPublicNickname());
			this.me.setState(ContactModel.State.ACTIVE);
			this.me.setFirstName(context.getString(R.string.me_myself_and_i));
			this.me.setVerificationLevel(VerificationLevel.FULLY_VERIFIED);
			this.me.setFeatureMask(-1);
		}

		return this.me;
	}

	@Override
	@NonNull
	public List<ContactModel> getAllDisplayed(@NonNull ContactSelection contactSelection) {
		return this.find(new Filter() {
			@Override
			public ContactModel.State[] states() {
				if (preferenceService.showInactiveContacts()) {
					switch (contactSelection) {
						case EXCLUDE_INVALID:
							return new ContactModel.State[]{
								ContactModel.State.ACTIVE,
								ContactModel.State.INACTIVE,
							};
						case INCLUDE_INVALID:
						default:
							return null;
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
				return false;
			}

			@Override
			public Boolean onlyWithReceiptSettings() {
				return false;
			}
		});
	}

	@Override
	@NonNull
	public List<ContactModel> getAll() {
		return find(null);
	}

	@Override
	@NonNull
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

		Collections.sort(result, ContactUtil.getContactComparator(sortOrderFirstName));

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
		return this.cache(this.contactStore.getContactForIdentity(identity));
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
	@NonNull
	public List<ContactModel> getAllDisplayedWork(@NonNull ContactSelection selection) {
		return Functional.filter(this.getAllDisplayed(selection), (IPredicateNonNull<ContactModel>) ContactModel::isWork);
	}

	@Override
	@NonNull
	public List<ContactModel> getAllWork() {
		return Functional.filter(this.getAll(), (IPredicateNonNull<ContactModel>) ContactModel::isWork);
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
				return !ContactUtil.isEchoEchoOrChannelContact(type);
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
							typingIdentities.remove(identity);
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
	public void sendTypingIndicator(String toIdentity, boolean isTyping) {
		ContactModel contactModel = getByIdentity(toIdentity);
		if (contactModel == null) {
			logger.error("Cannot send typing indicator");
			return;
		}

		boolean sendTypingIndicator;
		switch (contactModel.getTypingIndicators()) {
			case ContactModel.SEND:
				sendTypingIndicator = true;
				break;
			case ContactModel.DONT_SEND:
				sendTypingIndicator = false;
				break;
			default:
				sendTypingIndicator = preferenceService.isTypingIndicator();
				break;
		}

		if (!sendTypingIndicator) {
			return;
		}

		try {
			createReceiver(contactModel).sendTypingIndicatorMessage(isTyping);
		} catch (ThreemaException e) {
			logger.error("Could not send typing indicator", e);
		}
	}

	@Override
	public void setActive(@Nullable String identity) {
		final ContactModel contact = this.getByIdentity(identity);

		if (contact != null && contact.getState() == ContactModel.State.INACTIVE) {
			contact.setState(ContactModel.State.ACTIVE);
			this.save(contact);
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
	public void save(@NonNull ContactModel contactModel) {
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

		// Remove draft of this contact
		ContactMessageReceiver receiver = createReceiver(model);
		ThreemaApplication.putMessageDraft(receiver.getUniqueIdString(), null, null);

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
			ShortcutUtil.deleteShareTargetShortcut(uniqueIdString);
			ShortcutUtil.deletePinnedShortcut(uniqueIdString);

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

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		String identity = userService.getIdentity();
		if (serviceManager != null && identity != null && model.getIdentity() != null) {
			try {
				DHSessionStoreInterface dhSessionStore = serviceManager.getDHSessionStore();
				dhSessionStore.deleteAllDHSessions(identity, model.getIdentity());
			} catch (DHSessionStoreException e) {
				logger.error("Could not delete all DH sessions");
			}
		} else {
			logger.warn("Could not delete DH sessions because the service manager or identity is null");
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

	@AnyThread
	@Override
	public Bitmap getAvatar(@Nullable ContactModel contact, @NonNull AvatarOptions options) {
		// If the custom avatar is requested without default fallback and there is no avatar for
		// this contact, we can return null directly. Important: This is necessary to prevent glide
		// from logging an unnecessary error stack trace.
		if (options.defaultAvatarPolicy == CUSTOM_AVATAR && !hasAvatarOrContactPhoto(contact)) {
			return null;
		}

		Bitmap b = this.avatarCacheService.getContactAvatar(contact, options);

		//check if a business avatar update is necessary
		if (ContactUtil.isChannelContact(contact) && ContactUtil.isAvatarExpired(contact)) {
			//simple start
			UpdateBusinessAvatarRoutine.startUpdate(contact, this.fileService, this, apiService);
		}

		return b;
	}

	private boolean hasAvatarOrContactPhoto(@Nullable ContactModel contact) {
		if (contact == null) {
			return false;
		}

		return fileService.hasContactAvatarFile(contact) || fileService.hasContactPhotoFile(contact);
	}

	@Override
	public @ColorInt int getAvatarColor(@Nullable ContactModel contact) {
		if ((this.preferenceService == null || this.preferenceService.isDefaultContactPictureColored()) && contact != null) {
			return contact.getThemedColor(context);
		}
		return ColorUtil.getInstance().getCurrentThemeGray(this.context);
	}

	@AnyThread
	@Override
	public void loadAvatarIntoImage(
		@NonNull ContactModel model,
		@NonNull ImageView imageView,
		@NonNull AvatarOptions options,
		@NonNull RequestManager requestManager
	) {
		avatarCacheService.loadContactAvatarIntoImage(model, imageView, options, requestManager);
	}

	@AnyThread
	@Override
	public void clearAvatarCache(@NonNull ContactModel contactModel) {
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

	@Override
	public void createContactsByIdentities(@NonNull List<String> identities) {
		List<String> newIdentities = StreamSupport.stream(identities)
				.filter(identity -> {
					if (identity == null) {
						return false;
					}
					if (identity.equals(getMe().getIdentity())) {
						logger.warn("Ignore own identity");
						return false;
					}
					if (getByIdentity(identity) != null) {
						logger.warn("Ignore ID that is already in contact list");
						return false;
					}
					return true;
				}).collect(Collectors.toList());

		if (newIdentities.isEmpty()) {
			return;
		}

		try {
			for (APIConnector.FetchIdentityResult result : apiConnector.fetchIdentities(newIdentities)) {
				ContactModel contactModel = createContactByFetchIdentityResult(result);
				if (contactModel != null) {
					contactStore.addContact(contactModel);
				}
			}
		} catch (Exception e) {
			logger.error("Error while bulk creating contacts", e);
		}
	}

	@Override
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
		return new ContactMessageReceiver(
			contact,
			this,
			this.databaseServiceNew,
			this.messageQueue,
			this.identityStore,
			this.blackListIdentityService,
			this.fsmp
		);
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

		List<ContactModel> contactModels = this.getAll();
		for (ContactModel contactModel: contactModels) {
			if (!TestUtil.empty(contactModel.getAndroidContactLookupKey())) {
				try {
					AndroidContactUtil.getInstance().updateNameByAndroidContact(contactModel);
				} catch (ThreemaException e) {
					contactModel.setAndroidContactLookupKey(null);
					logger.error("Unable to update contact name", e);
				}
				this.save(contactModel);
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
	@Deprecated
	public int getUniqueId(ContactModel contactModel) {
		if (contactModel != null) {
			return (CONTACT_UID_PREFIX + contactModel.getIdentity()).hashCode();
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
				messageDigest.update((CONTACT_UID_PREFIX + identity).getBytes());
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
			// Update last profile picture upload date
			this.preferenceService.setProfilePicUploadDate(new Date(0));
			this.preferenceService.setProfilePicUploadData(null);

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
					ListenerManager.profileListeners.handle(ProfileListener::onAvatarRemoved);
				}
				ListenerManager.contactListeners.handle(listener -> listener.onAvatarChanged(contactModel));

				return true;
			}
		}
		return false;
	}

	@Override
	@WorkerThread
	@NonNull
	public ProfilePictureUploadData getUpdatedProfilePictureUploadData() {
		Bitmap contactPhoto;
		try {
			contactPhoto = getMyProfilePicture();
		} catch (ThreemaException e) {
			logger.error("Could not get my profile picture", e);
			// Returning empty profile picture upload data means no set or delete profile picture
			// message will be sent.
			return new ProfilePictureUploadData();
		}
		if (contactPhoto == null) {
			// If there is no profile picture set, then return empty upload data with an empty byte
			// array as blob ID. This means, that a delete-profile-picture message will be sent.
			ProfilePictureUploadData data = new ProfilePictureUploadData();
			data.blobId = ContactModel.NO_PROFILE_PICTURE_BLOB_ID;
			return data;
		}

		/* only upload blob every 7 days */
		Date uploadDeadline = new Date(preferenceService.getProfilePicUploadDate() + ContactUtil.PROFILE_PICTURE_BLOB_CACHE_DURATION);
		Date now = new Date();

		if (now.after(uploadDeadline)) {
			logger.info("Uploading profile picture blob");

			ProfilePictureUploadData data = uploadContactPhoto(contactPhoto);

			if (data == null) {
				return new ProfilePictureUploadData();
			}

			preferenceService.setProfilePicUploadDate(now);
			preferenceService.setProfilePicUploadData(data);

			return data;
		} else {
			ProfilePictureUploadData data = preferenceService.getProfilePicUploadData();
			if (data != null) {
				return data;
			} else {
				return new ProfilePictureUploadData();
			}
		}
	}

	@WorkerThread
	@Nullable
	private Bitmap getMyProfilePicture() throws ThreemaException {
		ContactModel myContactModel = getMe();
		Bitmap myProfilePicture = getAvatar(myContactModel, true, false);
		if (myProfilePicture == null && fileService.hasContactAvatarFile(myContactModel)) {
			throw new ThreemaException("Could not load profile picture despite having set one");
		}
		return myProfilePicture;
	}

	@Nullable
	private ProfilePictureUploadData uploadContactPhoto(@NonNull Bitmap contactPhoto) {
		ProfilePictureUploadData data = new ProfilePictureUploadData();

		SecureRandom rnd = new SecureRandom();
		data.encryptionKey = new byte[NaCl.SYMMKEYBYTES];
		rnd.nextBytes(data.encryptionKey);

		data.bitmapArray = BitmapUtil.bitmapToJpegByteArray(contactPhoto);
		byte[] imageData = NaCl.symmetricEncryptData(data.bitmapArray, data.encryptionKey, ProtocolDefines.CONTACT_PHOTO_NONCE);
		try {
			BlobUploader blobUploader = this.apiService.createUploader(imageData);
			data.blobId = blobUploader.upload();
		} catch (ThreemaException | IOException e) {
			logger.error("Could not upload contact photo", e);
			return null;
		}
		data.size = imageData.length;
		return data;
	}


	@Override
	public boolean updateProfilePicture(ContactSetProfilePictureMessage msg) {
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
	public boolean deleteProfilePicture(ContactDeleteProfilePictureMessage msg) {
		final ContactModel contactModel = this.getContact(msg);

		if (contactModel != null) {
			fileService.removeContactPhoto(contactModel);

			this.avatarCacheService.reset(contactModel);

			ListenerManager.contactListeners.handle(listener -> listener.onAvatarChanged(contactModel));
		}
		return true;
	}

	@Override
	public boolean requestProfilePicture(ContactRequestProfilePictureMessage msg) {
		final ContactModel contactModel = this.getContact(msg);

		if (contactModel != null) {
			logger.info("Received request to re-send profile pic by {}", msg.getFromIdentity());

			resetContactPhotoSentState(contactModel);
		}
		return true;
	}

	private void resetContactPhotoSentState(@NonNull ContactModel contactModel) {
		// Note that setting the blob id to null also triggers a delete-profile-picture message to
		// be sent again in case there is no profile picture set.
		contactModel.setProfilePicBlobID(null);
		save(contactModel);
	}

	@Override
	public boolean isContactAllowedToReceiveProfilePicture(@NonNull ContactModel contactModel) {
		int profilePicRelease = preferenceService.getProfilePicRelease();
		return profilePicRelease == PreferenceService.PROFILEPIC_RELEASE_EVERYONE ||
			(profilePicRelease == PreferenceService.PROFILEPIC_RELEASE_SOME && profilePicRecipientsService.has(contactModel.getIdentity()));
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

		ContactModel contact;
		try {
			contact = this.fetchPublicKeyForIdentity(identity);
		} catch (APIConnector.HttpConnectionException e) {
			logger.error("Could not fetch public key", e);
			if (e.getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
				throw new InvalidEntryException(R.string.invalid_threema_id);
			} else {
				throw new InvalidEntryException(R.string.connection_error);
			}
		} catch (APIConnector.NetworkException e) {
			throw new InvalidEntryException(R.string.connection_error);
		}

		if (contact == null) {
			throw new InvalidEntryException(R.string.invalid_threema_id);
		}

		if (contact.getPublicKey() == null) {
			throw new InvalidEntryException(R.string.connection_error);
		}

		save(contact);

		return contact;
	}

	@Override
	public boolean showBadge(@Nullable ContactModel contactModel) {
		if (contactModel != null) {
			if (ConfigUtils.isWorkBuild()) {
				if (userService.isMe(contactModel.getIdentity())) {
					return false;
				}
				return contactModel.getIdentityType() == IdentityType.NORMAL && !ContactUtil.isEchoEchoOrChannelContact(contactModel);
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

		// delete share target shortcut as name is different
		ShortcutUtil.deleteShareTargetShortcut(getUniqueIdString(contact));
	}

	/**
	 * Get Android contact lookup key Uri in String representation to be used for Notification.Builder.addPerson()
	 * @param contactModel ContactModel to get Uri for
	 * @return Uri of Android contact as a string or null if there's no linked contact or permission to access contacts has not been granted
	 */
	@Override
	public @Nullable String getAndroidContactLookupUriString(ContactModel contactModel) {
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

	@Override
	@WorkerThread
	public void fetchAndCacheContact(@NonNull String identity) throws APIConnector.HttpConnectionException, APIConnector.NetworkException, MissingPublicKeyException {
		// Check if the contact is available locally
		if (contactStore.getContactForIdentity(identity) != null) {
			return;
		}

		// Check if the contact is cached locally
		if (contactStore.getContactForIdentityIncludingCache(identity) != null) {
			return;
		}

		// Check if the identity is a work contact that should be known
		if (ConfigUtils.isWorkBuild()) {
			fetchAndCreateWorkContact(identity);
			if (contactStore.getContactForIdentity(identity) != null) {
				return;
			}
		}

		// Check if contact is known after contact synchronization (if enabled)
		if (preferenceService.isSyncContacts()) {
			// Synchronize contact
			SynchronizeContactsUtil.startDirectly(identity);

			// Check again locally
			if (contactStore.getContactForIdentity(identity) != null) {
				return;
			}
		}

		try {
			// Otherwise try to fetch the identity
			Contact contact = fetchPublicKeyForIdentity(identity);
			if (contact != null) {
				contactStore.addCachedContact(contact);
			}
		} catch (APIConnector.HttpConnectionException e) {
			if (e.getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
				logger.warn("Identity fetch for identity '{}' returned 404", identity);
				throw new MissingPublicKeyException("No public key found");
			} else {
				throw e;
			}
		}
	}

	/**
	 * Try to fetch a contact from work api and add it to the contact database. Note that this
	 * method does not throw any exceptions when the connection to the server could not be
	 * established.
	 *
	 * @param identity the identity of the contact that might be a work contact
	 */
	private void fetchAndCreateWorkContact(@NonNull String identity) {
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

	/**
	 * Create a contact by an identity fetch result.
	 *
	 * @param result the result of the identity fetch
	 * @return the contact model if the fetch was successful, null otherwise
	 */
	private @Nullable ContactModel createContactByFetchIdentityResult(
		@Nullable APIConnector.FetchIdentityResult result
	) {
		if (result == null || result.publicKey == null) {
			return null;
		}

		byte[] b = result.publicKey;

		ContactModel contact = new ContactModel(result.identity, b);
		contact.setFeatureMask(result.featureMask);
		contact.setVerificationLevel(VerificationLevel.UNVERIFIED);
		contact.setDateCreated(new Date());
		contact.setIdentityType(result.type);
		switch (result.state) {
			case IdentityState.ACTIVE:
				contact.setState(ContactModel.State.ACTIVE);
				break;
			case IdentityState.INACTIVE:
				contact.setState(ContactModel.State.INACTIVE);
				break;
			case IdentityState.INVALID:
				contact.setState(ContactModel.State.INVALID);
				break;
		}

		return contact;
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

	@Override
	@UiThread
	public void reportSpam(@NonNull final ContactModel spammerContactModel, @Nullable Consumer<Void> onSuccess, @Nullable Consumer<String> onFailure) {
		new Thread(() -> {
			try {
				apiConnector.reportJunk(identityStore, spammerContactModel.getIdentity(), spammerContactModel.getPublicNickName());

				spammerContactModel.setIsHidden(true);
				save(spammerContactModel);

				if (onSuccess != null) {
					RuntimeUtil.runOnUiThread(() -> onSuccess.accept(null));
				}
			} catch (Exception e) {
				logger.error("Error reporting spam", e);
				if (onFailure != null) {
					RuntimeUtil.runOnUiThread(() -> onFailure.accept(e.getMessage()));
				}
			}
		}).start();
	}

	@Nullable
	@Override
	public ForwardSecuritySessionState getForwardSecurityState(@NonNull ContactModel contactModel) {
		if (!ThreemaFeature.canForwardSecurity(contactModel.getFeatureMask())) {
			return ForwardSecuritySessionState.unsupportedByRemote();
		}
		try {
			DHSession session = ThreemaApplication.requireServiceManager().getDHSessionStore()
				.getBestDHSession(userService.getIdentity(), contactModel.getIdentity());
			if (session == null) {
				return ForwardSecuritySessionState.noSession();
			}
			DHSession.State dhState = session.getState();
			DHSession.DHVersions dhVersions = session.getCurrent4DHVersions();
			return ForwardSecuritySessionState.fromDHState(dhState, dhVersions);
		} catch (Exception e) {
			logger.error("Could not get forward security state", e);
			return null;
		}
	}

	/**
	 * Fetch a public key for an identity and return it in a contact model.
	 *
	 * @param identity Identity to add a contact for
	 * @return the contact model of the identity in case of success, null otherwise
	 * @throws ch.threema.domain.protocol.api.APIConnector.HttpConnectionException when the identity cannot be fetched
	 * @throws ch.threema.domain.protocol.api.APIConnector.NetworkException        when the identity cannot be fetched
	 */
	@WorkerThread
	private @Nullable ContactModel fetchPublicKeyForIdentity(@NonNull String identity) throws APIConnector.HttpConnectionException, APIConnector.NetworkException {
		ContactModel contactModel = contactStore.getContactForIdentity(identity);
		if (contactModel != null) {
			return contactModel;
		}

		APIConnector.FetchIdentityResult result;
		try {
			result = this.apiConnector.fetchIdentity(identity);

			if (result == null || result.publicKey == null) {
				return null;
			}
		} catch (ThreemaException e) {
			logger.error("Fetch failed: ", e);
			throw new APIConnector.NetworkException(e);
		}

		ContactModel contact = new ContactModel(identity, result.publicKey);
		contact.setFeatureMask(result.featureMask);
		contact.setVerificationLevel(VerificationLevel.UNVERIFIED);
		contact.setDateCreated(new Date());
		contact.setIdentityType(result.type);
		switch (result.state) {
			case IdentityState.ACTIVE:
				contact.setState(ContactModel.State.ACTIVE);
				break;
			case IdentityState.INACTIVE:
				contact.setState(ContactModel.State.INACTIVE);
				break;
			case IdentityState.INVALID:
				contact.setState(ContactModel.State.INVALID);
				break;
		}

		return contact;
	}
}
