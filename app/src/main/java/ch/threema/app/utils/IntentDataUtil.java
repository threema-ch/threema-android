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

package ch.threema.app.utils;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.SystemClock;

import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ComposeMessageActivity;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.backuprestore.BackupRestoreDataService;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.WebClientSessionModel;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;

public class IntentDataUtil {

	public static final String ACTION_LICENSE_NOT_ALLOWED = BuildConfig.APPLICATION_ID + "license_not_allowed";
	public static final String ACTION_CONTACTS_CHANGED = BuildConfig.APPLICATION_ID + "contacts_changed";
    public static final String ACTION_UPDATE_AVAILABLE = BuildConfig.APPLICATION_ID + "update_available";

	public static final String INTENT_DATA_LOCATION_LAT ="latitude";
	public static final String INTENT_DATA_LOCATION_LNG ="longitude";
	public static final String INTENT_DATA_LOCATION_NAME ="lname";
	private static final String INTENT_DATA_LOCATION_ALT ="altitude";
	private static final String INTENT_DATA_LOCATION_ACCURACY ="accuracy";
	public static final String INTENT_DATA_LOCATION_PROVIDER = "location_provider";

	private static final String INTENT_DATA_CONTACT_LIST = "contactl";
	private static final String INTENT_DATA_GROUP_LIST = "groupl";
	private static final String INTENT_DATA_DIST_LIST = "distl";

	private static final String INTENT_DATA_SERVER_MESSAGE_TEXT = "server_message_text";
	private static final String INTENT_DATA_SERVER_MESSAGE_TYPE = "server_message_type";
	private static final String INTENT_DATA_MESSAGE = "message";
	private static final String INTENT_DATA_URL = "url";
	private static final String INTENT_DATA_CONTACTS = "contacts";
	public static final String INTENT_DATA_GROUP_ID = "group_id";
	private static final String INTENT_DATA_ABSTRACT_MESSAGE_ID = "abstract_message_id";
	private static final String INTENT_DATA_ABSTRACT_MESSAGE_IDS = "abstract_message_ids";
	private static final String INTENT_DATA_ABSTRACT_MESSAGE_TYPE = "abstract_message_type";
	private static final String INTENT_DATA_ABSTRACT_MESSAGE_TYPES = "abstract_message_types";
	public static final String INTENT_DATA_IDENTITY = "identity";
	public static final String INTENT_DATA_WEB_CLIENT_SESSION_MODEL_ID = "session_model_id";
	public static final String INTENT_DATA_PAYLOAD = "payload";

	private static final String INTENT_DATA_BACKUP_FILE = "backup_file";

	private static final String INTENT_HIDE_AFTER_UNLOCK = "hide_after_unlock";
	private static final String INTENT_DATA_BALLOT_ID = "ballot_id";
	private static final String INTENT_DATA_BALLOT_CHOICE_ID = "ballot_choide_id";

	public static void append(BackupRestoreDataService.BackupData backupData, Intent intent) {
		intent.putExtra(INTENT_DATA_BACKUP_FILE, backupData.getFile().getPath());
	}

	public static void append(byte[] payload, Intent intent) {
		intent.putExtra(INTENT_DATA_PAYLOAD, payload);
	}

	public static void append(ContactModel contactModel, Intent intent) {
		intent.putExtra(INTENT_DATA_IDENTITY, contactModel.getIdentity());
	}

	public static void append(String identity, Intent intent) {
		intent.putExtra(INTENT_DATA_IDENTITY, identity);
	}

	public static void append(GroupModel groupModel, Intent intent) {
		intent.putExtra(INTENT_DATA_GROUP_ID, groupModel.getId());
	}

	public static void append(LatLng latLng, String provider, String name, String address, Intent intent) {
		intent.putExtra(INTENT_DATA_LOCATION_LAT, latLng.getLatitude());
		intent.putExtra(INTENT_DATA_LOCATION_LNG, latLng.getLongitude());
		intent.putExtra(INTENT_DATA_LOCATION_PROVIDER, provider);
		if (TestUtil.empty(name)) {
			intent.putExtra(INTENT_DATA_LOCATION_NAME, address);
		} else {
			intent.putExtra(INTENT_DATA_LOCATION_NAME, name);
		}
	}

	public static void append(ServerMessageModel serverMessageModel, Intent intent) {
		intent.putExtra(INTENT_DATA_SERVER_MESSAGE_TEXT, serverMessageModel.getMessage());
		intent.putExtra(INTENT_DATA_SERVER_MESSAGE_TYPE, serverMessageModel.getType().toString());
	}

	public static void append(AbstractMessageModel abstractMessageModel, Intent intent) {
		intent.putExtra(INTENT_DATA_ABSTRACT_MESSAGE_ID, abstractMessageModel.getId());
		intent.putExtra(INTENT_DATA_ABSTRACT_MESSAGE_TYPE, abstractMessageModel.getClass().toString());
	}


	public static void append(WebClientSessionModel model, Intent intent) {
		intent.putExtra(INTENT_DATA_WEB_CLIENT_SESSION_MODEL_ID, model.getId());
	}

	public static void appendMultiple(List<AbstractMessageModel> models, Intent intent) {
		ArrayList<Integer> messageIDs = new ArrayList<>(models.size());

		for (AbstractMessageModel model: models) {
			messageIDs.add(model.getId());
		}
		intent.putExtra(INTENT_DATA_ABSTRACT_MESSAGE_IDS, messageIDs);
		intent.putExtra(INTENT_DATA_ABSTRACT_MESSAGE_TYPE, models.get(0).getClass().toString());
	}

	public static void appendMultipleMessageTypes(List<AbstractMessageModel> models, Intent intent) {
		ArrayList<Integer> messageIDs = new ArrayList<>(models.size());
		ArrayList<String> messageTypes = new ArrayList<>();

		Iterator<AbstractMessageModel> iterator = models.iterator();
		while (iterator.hasNext()) {
			AbstractMessageModel failedMessage = iterator.next();
			messageIDs.add(failedMessage.getId());
			messageTypes.add(failedMessage.getClass().toString());
		}
		intent.putExtra(INTENT_DATA_ABSTRACT_MESSAGE_IDS, messageIDs);
		intent.putExtra(INTENT_DATA_ABSTRACT_MESSAGE_TYPES, messageTypes);
	}

	public static void append(int id, String classname, Intent intent) {
		intent.putExtra(INTENT_DATA_ABSTRACT_MESSAGE_ID, id);
		intent.putExtra(INTENT_DATA_ABSTRACT_MESSAGE_TYPE, classname);
	}

	public static void append(List<ContactModel> contacts, Intent intent) {
		String[] identities = new String[contacts.size()];
		int p = 0;
		for(ContactModel c: contacts) {
			identities[p++] = c.getIdentity();
		}

		intent.putExtra(INTENT_DATA_CONTACTS, identities);
	}

	public static Location getLocation(Intent intent) {
		Location location = new Location(intent.getStringExtra(INTENT_DATA_LOCATION_PROVIDER));
		location.setLatitude(intent.getDoubleExtra(INTENT_DATA_LOCATION_LAT, 0));
		location.setLongitude(intent.getDoubleExtra(INTENT_DATA_LOCATION_LNG, 0));
		location.setAltitude(intent.getDoubleExtra(INTENT_DATA_LOCATION_ALT, 0));
		location.setAccuracy(intent.getFloatExtra(INTENT_DATA_LOCATION_ACCURACY, 0));

		return location;
	}

	public static ServerMessageModel getServerMessageModel(Intent intent) {
		return new ServerMessageModel(
				intent.getStringExtra(INTENT_DATA_SERVER_MESSAGE_TEXT),
				ServerMessageModel.Type.ALERT.toString().equals(intent.getStringExtra(INTENT_DATA_SERVER_MESSAGE_TYPE)) ?
						ServerMessageModel.Type.ALERT :
						ServerMessageModel.Type.ERROR
		);
	}

	public static Intent createActionIntentLicenseNotAllowed(String message) {
		Intent intent = new Intent();
		intent.putExtra(INTENT_DATA_MESSAGE, message);
		intent.setAction(ACTION_LICENSE_NOT_ALLOWED);
		return intent;
	}

    public static Intent createActionIntentUpdateAvailable(String updateMessage, String updateUrl) {
        Intent intent = new Intent();
        intent.putExtra(INTENT_DATA_MESSAGE, updateMessage);
        intent.putExtra(INTENT_DATA_URL, updateUrl);
        intent.setAction(ACTION_UPDATE_AVAILABLE);
        return intent;
    }

	public static Intent createActionIntentHideAfterUnlock(Intent intent) {
		intent.putExtra(INTENT_HIDE_AFTER_UNLOCK, true);
		return intent;
	}

	public static boolean hideAfterUnlock(Intent intent) {
		return intent.hasExtra(INTENT_HIDE_AFTER_UNLOCK) && intent.getBooleanExtra(INTENT_HIDE_AFTER_UNLOCK, false);
	}

	public static Intent createActionIntentContactsChanged() {
		Intent intent = new Intent();
		intent.setAction(ACTION_CONTACTS_CHANGED);
		return intent;
	}

	public static String getMessage(Intent intent) {
		return intent.getStringExtra(INTENT_DATA_MESSAGE);
	}

    public static String getUrl(Intent intent) {
        return intent.getStringExtra(INTENT_DATA_URL);
    }

	public static String[] getContactIdentities(Intent intent) {
		return intent.getStringArrayExtra(INTENT_DATA_CONTACTS);
	}

	public static int getGroupId(Intent intent) {
		if(intent.hasExtra(INTENT_DATA_GROUP_ID)) {
			return intent.getIntExtra(INTENT_DATA_GROUP_ID, -1);
		}

		return -1;
	}

	public static String getIdentity(Intent intent) {
		if(intent.hasExtra(INTENT_DATA_IDENTITY)) {
			return intent.getStringExtra(INTENT_DATA_IDENTITY);
		}

		return null;
	}

	public static void append(BallotModel ballotModel, Intent intent) {
		intent.putExtra(INTENT_DATA_BALLOT_ID, ballotModel.getId());
	}

	public static int getBallotId(Intent intent) {
		if(intent.hasExtra(INTENT_DATA_BALLOT_ID)) {
			return intent.getIntExtra(INTENT_DATA_BALLOT_ID, 0);
		}

		return 0;
	}

	public static void append(BallotChoiceModel ballotChoiceModel, Intent intent) {
		intent.putExtra(INTENT_DATA_BALLOT_CHOICE_ID, ballotChoiceModel.getId());
	}

	public static int getBallotChoiceId(Intent intent) {
		if(intent.hasExtra(INTENT_DATA_BALLOT_CHOICE_ID)) {
			return intent.getIntExtra(INTENT_DATA_BALLOT_CHOICE_ID, 0);
		}

		return 0;
	}

	public static String getAbstractMessageType(Intent intent) {
		return intent.getStringExtra(INTENT_DATA_ABSTRACT_MESSAGE_TYPE);
	}

	public static int getAbstractMessageId(Intent intent) {
		return intent.getIntExtra(INTENT_DATA_ABSTRACT_MESSAGE_ID, 0);
	}

	public static ArrayList<Integer> getAbstractMessageIds(Intent intent) {
		return intent.getIntegerArrayListExtra(INTENT_DATA_ABSTRACT_MESSAGE_IDS);
	}

	public static ArrayList<String> getAbstractMessageTypes(Intent intent) {
		return intent.getStringArrayListExtra(INTENT_DATA_ABSTRACT_MESSAGE_TYPES);
	}

	public static AbstractMessageModel getAbstractMessageModel(Intent intent, MessageService messageService) {
		if (intent != null && messageService != null) {

			int id = getAbstractMessageId(intent);
			String type = getAbstractMessageType(intent);

			return messageService.getMessageModelFromId(id, type);
		}
		return null;
	}

	public static ArrayList<AbstractMessageModel> getAbstractMessageModels(Intent intent, MessageService messageService) {
		ArrayList<Integer> messageIDs = getAbstractMessageIds(intent);
		ArrayList<String> messageTypes = getAbstractMessageTypes(intent);
		ArrayList<AbstractMessageModel> messageModels = new ArrayList<>(messageIDs.size());

		Iterator<Integer> ids = messageIDs.iterator();
		Iterator<String> types = messageTypes.iterator();

		while (ids.hasNext() && types.hasNext()) {
			messageModels.add(messageService.getMessageModelFromId(ids.next(), types.next()));
		}
		return messageModels;
	}

	public static MessageReceiver getMessageReceiverFromIntent(Context context, Intent intent) {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		ContactService contactService;
		GroupService groupService;
		DistributionListService distributionListService;

		try {
			contactService = serviceManager.getContactService();
			groupService = serviceManager.getGroupService();
			distributionListService = serviceManager.getDistributionListService();
		} catch (Exception e) {
			return null;
		}

		if (!TestUtil.required(contactService, groupService, distributionListService)) {
			return null;
		}

		String identity = ContactUtil.getIdentityFromViewIntent(context, intent);
		if (!TestUtil.empty(identity)) {
			return contactService.createReceiver(contactService.getByIdentity(identity));
		}

		if (intent.hasExtra(ThreemaApplication.INTENT_DATA_CONTACT)) {
			String cIdentity = intent.getStringExtra(ThreemaApplication.INTENT_DATA_CONTACT);
			return contactService.createReceiver(contactService.getByIdentity(cIdentity));
		} else if (intent.hasExtra(ThreemaApplication.INTENT_DATA_GROUP)) {
			int groupId = intent.getIntExtra(ThreemaApplication.INTENT_DATA_GROUP, 0);
			return groupService.createReceiver(groupService.getById(groupId));
		} else if (intent.hasExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST)) {
			int distId = intent.getIntExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, 0);
			return distributionListService.createReceiver(distributionListService.getById(distId));
		}

		return null;
	}

	/**
	 * Get a list of message receivers from an intent
	 * @param intent
	 * @return ArrayList of MessageReceivers
	 */
	public static ArrayList<MessageReceiver> getMessageReceiversFromIntent(Intent intent) {
		ArrayList<MessageReceiver> messageReceivers = new ArrayList<>();
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		ContactService contactService;
		GroupService groupService;
		DistributionListService distributionListService;

		try {
			contactService = serviceManager.getContactService();
			groupService = serviceManager.getGroupService();
			distributionListService = serviceManager.getDistributionListService();
		} catch (Exception e) {
			return null;
		}

		if (!TestUtil.required(contactService, groupService, distributionListService)) {
			return null;
		}

		if (intent.hasExtra(INTENT_DATA_CONTACT_LIST)) {
			ArrayList<String> contactIds = intent.getStringArrayListExtra(INTENT_DATA_CONTACT_LIST);
			for (String contactId : contactIds) {
				messageReceivers.add(contactService.createReceiver(contactService.getByIdentity(contactId)));
			}
		}
		if (intent.hasExtra(INTENT_DATA_GROUP_LIST)) {
			ArrayList<Integer> groupIds = intent.getIntegerArrayListExtra(INTENT_DATA_GROUP_LIST);
			for (int groupId : groupIds) {
				messageReceivers.add(groupService.createReceiver(groupService.getById(groupId)));
			}
		}
		if (intent.hasExtra(INTENT_DATA_DIST_LIST)) {
			ArrayList<Integer> distributionListIds = intent.getIntegerArrayListExtra(INTENT_DATA_DIST_LIST);
			for (int distributionListId : distributionListIds) {
				messageReceivers.add(distributionListService.createReceiver(distributionListService.getById(distributionListId)));
			}
		}
		return messageReceivers;
	}

	public static Intent addMessageReceiverToIntent(Intent intent, MessageReceiver messageReceiver) {
		switch (messageReceiver.getType()) {
			case MessageReceiver.Type_CONTACT:
				intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, ((ContactMessageReceiver) messageReceiver).getContact().getIdentity());
				break;
			case MessageReceiver.Type_GROUP:
				intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, ((GroupMessageReceiver) messageReceiver).getGroup().getId());
				break;
			case MessageReceiver.Type_DISTRIBUTION_LIST:
				intent.putExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, ((DistributionListMessageReceiver) messageReceiver).getDistributionList().getId());
				break;
			default:
				break;
		}

		return intent;
	}

	/**
	 * Add extras to an existing intent representing a list of MessageReceivers
	 * @param intent
	 * @param messageReceivers
	 * @return intent
	 */
	public static Intent addMessageReceiversToIntent(Intent intent, MessageReceiver[] messageReceivers) {
		ArrayList<String> contactIds = new ArrayList<>();
		ArrayList<Integer> groupIds = new ArrayList<>();
		ArrayList<Integer> distributionListIds = new ArrayList<>();

		for (MessageReceiver messageReceiver: messageReceivers) {
			switch (messageReceiver.getType()) {
				case MessageReceiver.Type_CONTACT:
					contactIds.add(((ContactMessageReceiver) messageReceiver).getContact().getIdentity());
					break;
				case MessageReceiver.Type_GROUP:
					groupIds.add(((GroupMessageReceiver) messageReceiver).getGroup().getId());
					break;
				case MessageReceiver.Type_DISTRIBUTION_LIST:
					distributionListIds.add(((DistributionListMessageReceiver) messageReceiver).getDistributionList().getId());
					break;
				default:
					break;
			}
		}

		if (contactIds.size() > 0) {
			intent.putExtra(INTENT_DATA_CONTACT_LIST, contactIds);
		}
		if (groupIds.size() > 0) {
			intent.putExtra(INTENT_DATA_GROUP_LIST, groupIds);
		}
		if (distributionListIds.size() > 0) {
			intent.putExtra(INTENT_DATA_DIST_LIST, distributionListIds);
		}

		return intent;
	}

	public static AbstractMessageModel getMessageModelFromReceiver(Intent intent, MessageReceiver messageReceiver) {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		MessageService messageService;
		try {
			messageService = serviceManager.getMessageService();
		} catch (Exception e) {
			return null;
		}

		if (intent.hasExtra(ThreemaApplication.INTENT_DATA_MESSAGE_ID)) {
			int id = intent.getIntExtra(ThreemaApplication.INTENT_DATA_MESSAGE_ID, -1);

			if (id >= 0) {
				if (messageReceiver.getType() == MessageReceiver.Type_CONTACT) {
					return messageService.getContactMessageModel(id, true);
				} else if (messageReceiver.getType() == MessageReceiver.Type_GROUP) {
					return messageService.getGroupMessageModel(id, true);
				}
			}
		}
		return null;
	}

	/**
	 * get the payload byte array or null
	 * @param intent
	 * @return
	 */
	public static byte[] getPayload(Intent intent) {
		return intent.hasExtra(INTENT_DATA_PAYLOAD) ?
				intent.getByteArrayExtra(INTENT_DATA_PAYLOAD)
				: null;
	}

	public static Intent getShowConversationIntent(ConversationModel conversationModel, Context context) {
		if (conversationModel == null) {
			return null;
		}

		Intent intent = new Intent(context, ComposeMessageActivity.class);

		if (conversationModel.isGroupConversation()) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, conversationModel.getGroup().getId());
		} else if (conversationModel.isDistributionListConversation()) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, conversationModel.getDistributionList().getId());
		} else {
			intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, conversationModel.getContact().getIdentity());
		}
		return  intent;
	}

	public static Intent getComposeIntentForReceivers(Context context, ArrayList<MessageReceiver> receivers) {
		Intent intent;

		if (receivers.size() >= 1) {
			intent = addMessageReceiverToIntent(new Intent(context, ComposeMessageActivity.class), receivers.get(0));
			intent.putExtra(ThreemaApplication.INTENT_DATA_EDITFOCUS, Boolean.TRUE);
		} else {
			intent = new Intent(context, HomeActivity.class);
		}

		// fix for <4.1 - keeps android from re-using existing intent and stripping extras
		intent.setData((Uri.parse("foobar://" + SystemClock.elapsedRealtime())));
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.putExtra(ThreemaApplication.INTENT_DATA_TIMESTAMP, SystemClock.elapsedRealtime());

		return intent;
	}

	public static Intent getJumpToMessageIntent(Context context, AbstractMessageModel messageModel) {
		Intent intent = new Intent(context, ComposeMessageActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

		if (messageModel instanceof GroupMessageModel) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, ((GroupMessageModel) messageModel).getGroupId());
		} else if (messageModel instanceof DistributionListMessageModel) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_DISTRIBUTION_LIST, ((DistributionListMessageModel) messageModel).getDistributionListId());
		} else {
			intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, messageModel.getIdentity());
		}
		intent.putExtra(ComposeMessageFragment.EXTRA_API_MESSAGE_ID, messageModel.getApiMessageId());
		intent.putExtra(ComposeMessageFragment.EXTRA_SEARCH_QUERY, " ");

		return intent;
	}
}
