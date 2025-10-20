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

package ch.threema.app;

import android.app.ForegroundServiceStartNotAllowedException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.provider.ContactsContract;

import org.slf4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.DistributionListListener;
import ch.threema.app.listeners.EditMessageListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.MessageDeletedForAllListener;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.listeners.NewSyncedContactsListener;
import ch.threema.app.listeners.ServerMessageListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.AvatarCacheService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.ConversationNotificationUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.Toaster;
import ch.threema.app.utils.WidgetUtil;
import ch.threema.app.voip.listeners.VoipCallEventListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.listeners.WebClientWakeUpListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.SessionAndroidService;
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.GroupIdentity;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.WebClientSessionModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.GroupBallotModel;
import ch.threema.storage.models.ballot.IdentityBallotModel;
import ch.threema.storage.models.ballot.LinkBallotModel;
import ch.threema.storage.models.data.status.GroupStatusDataModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

// TODO(ANDR-3400) This code was moved out from ThreemaApplication and needs some heavy refactoring
public class GlobalListeners {

    private static final Logger logger = LoggingUtil.getThreemaLogger("GlobalListeners");

    public static final Lock onAndroidContactChangeLock = new ReentrantLock();

    public GlobalListeners(
        @NonNull Context appContext,
        @NonNull ServiceManager serviceManager
    ) {
        this.appContext = appContext;
        this.serviceManager = serviceManager;
    }

    @NonNull
    private final Context appContext;
    @NonNull
    private final ServiceManager serviceManager;

    public void setUp() {
        ListenerManager.groupListeners.add(groupListener);
        ListenerManager.distributionListListeners.add(distributionListListener);
        ListenerManager.messageListeners.add(messageListener);
        ListenerManager.editMessageListener.add(editMessageListener);
        ListenerManager.messageDeletedForAllListener.add(messageDeletedForAllListener);
        ListenerManager.serverMessageListeners.add(serverMessageListener);
        ListenerManager.contactListeners.add(contactListener);
        ListenerManager.contactSettingsListeners.add(contactSettingsListener);
        ListenerManager.conversationListeners.add(conversationListener);
        ListenerManager.ballotVoteListeners.add(ballotVoteListener);
        ListenerManager.synchronizeContactsListeners.add(synchronizeContactsListener);
        ListenerManager.contactTypingListeners.add(getContactTypingListener());
        ListenerManager.newSyncedContactListener.add(getNewSyncedContactListener());
        WebClientListenerManager.serviceListener.add(webClientServiceListener);
        WebClientListenerManager.wakeUpListener.add(webClientWakeUpListener);
        VoipListenerManager.callEventListener.add(voipCallEventListener);
        registerContactNameChangeListener();
    }

    public void tearDown() {
        ListenerManager.groupListeners.remove(groupListener);
        ListenerManager.distributionListListeners.remove(distributionListListener);
        ListenerManager.messageListeners.remove(messageListener);
        ListenerManager.editMessageListener.remove(editMessageListener);
        ListenerManager.messageDeletedForAllListener.remove(messageDeletedForAllListener);
        ListenerManager.serverMessageListeners.remove(serverMessageListener);
        ListenerManager.contactListeners.remove(contactListener);
        ListenerManager.contactSettingsListeners.remove(contactSettingsListener);
        ListenerManager.conversationListeners.remove(conversationListener);
        ListenerManager.ballotVoteListeners.remove(ballotVoteListener);
        ListenerManager.synchronizeContactsListeners.remove(synchronizeContactsListener);
        ListenerManager.contactTypingListeners.remove(getContactTypingListener());
        ListenerManager.newSyncedContactListener.remove(getNewSyncedContactListener());
        WebClientListenerManager.serviceListener.remove(webClientServiceListener);
        WebClientListenerManager.wakeUpListener.remove(webClientWakeUpListener);
        VoipListenerManager.callEventListener.remove(voipCallEventListener);
        unregisterContactNameChangeListener();
    }

    private void registerContactNameChangeListener() {
        if (ContextCompat.checkSelfPermission(appContext,
            android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            appContext.getContentResolver()
                .registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                    false,
                    contentObserverChangeContactNames);
        }
    }

    private void unregisterContactNameChangeListener() {
        if (ContextCompat.checkSelfPermission(appContext,
            android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            appContext.getContentResolver()
                .unregisterContentObserver(contentObserverChangeContactNames);
        }
    }

    private void showNotesGroupNotice(GroupModel groupModel, @GroupService.GroupState int oldState, @GroupService.GroupState int newState) {
        if (oldState != newState) {
            try {
                GroupService groupService = serviceManager.getGroupService();
                MessageService messageService = serviceManager.getMessageService();
                GroupStatusDataModel.GroupStatusType type = null;

                if (newState == GroupService.NOTES) {
                    type = GroupStatusDataModel.GroupStatusType.IS_NOTES_GROUP;
                } else if (newState == GroupService.PEOPLE && oldState != GroupService.UNDEFINED) {
                    type = GroupStatusDataModel.GroupStatusType.IS_PEOPLE_GROUP;
                }

                if (type != null) {
                    messageService.createGroupStatus(
                        groupService.createReceiver(groupModel),
                        type,
                        null,
                        null,
                        null
                    );
                }
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        }
    }

    private void showConversationNotification(AbstractMessageModel newMessage, boolean updateExisting) {
        try {
            if (!newMessage.isOutbox()
                && !newMessage.isStatusMessage()
                && !newMessage.isRead()) {

                NotificationService notificationService = serviceManager.getNotificationService();
                ContactService contactService = serviceManager.getContactService();
                GroupService groupService = serviceManager.getGroupService();
                ConversationCategoryService conversationCategoryService = serviceManager.getConversationCategoryService();

                if (TestUtil.required(notificationService, contactService, groupService)) {
                    if (newMessage.getType() != MessageType.GROUP_CALL_STATUS) {
                        notificationService.showConversationNotification(ConversationNotificationUtil.convert(
                                appContext,
                                newMessage,
                                contactService,
                                groupService,
                                conversationCategoryService),
                            updateExisting);
                    }

                    // update widget on incoming message
                    WidgetUtil.updateWidgets(serviceManager.getContext());
                }
            }
        } catch (ThreemaException e) {
            logger.error("Exception", e);
        }
    }

    @NonNull
    private final GroupListener groupListener = new GroupListener() {
        @Override
        public void onCreate(@NonNull GroupIdentity groupIdentity) {
            try {
                GroupModel groupModel = getGroupModel(groupIdentity);
                if (groupModel == null) {
                    return;
                }
                serviceManager.getConversationService().refresh(groupModel);
                serviceManager.getMessageService().createGroupStatus(
                    serviceManager.getGroupService().createReceiver(groupModel),
                    GroupStatusDataModel.GroupStatusType.CREATED,
                    null,
                    null,
                    null
                );
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        }

        @Override
        public void onRename(@NonNull GroupIdentity groupIdentity) {
            RuntimeUtil.runOnWorkerThread(() -> {
                try {
                    GroupModel groupModel = getGroupModel(groupIdentity);
                    if (groupModel == null) {
                        return;
                    }
                    GroupMessageReceiver messageReceiver = serviceManager.getGroupService().createReceiver(groupModel);
                    serviceManager.getConversationService().refresh(groupModel);
                    String groupName = groupModel.getName();
                    if (groupName == null) {
                        groupName = "";
                    }
                    serviceManager.getMessageService().createGroupStatus(
                        messageReceiver,
                        GroupStatusDataModel.GroupStatusType.RENAMED,
                        null,
                        null,
                        groupName
                    );
                    ShortcutUtil.updatePinnedShortcut(messageReceiver);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            });
        }

        @Override
        public void onUpdatePhoto(@NonNull GroupIdentity groupIdentity) {
            RuntimeUtil.runOnWorkerThread(() -> {
                try {
                    GroupModel groupModel = getGroupModel(groupIdentity);
                    if (groupModel == null) {
                        return;
                    }
                    GroupMessageReceiver messageReceiver = serviceManager.getGroupService().createReceiver(groupModel);
                    serviceManager.getConversationService().refresh(groupModel);
                    serviceManager.getMessageService().createGroupStatus(
                        messageReceiver,
                        GroupStatusDataModel.GroupStatusType.PROFILE_PICTURE_UPDATED,
                        null,
                        null,
                        null
                    );
                    ShortcutUtil.updatePinnedShortcut(messageReceiver);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            });
        }

        @Override
        public void onNewMember(@NonNull GroupIdentity groupIdentity, String identityNew) {
            GroupModel groupModel = getGroupModel(groupIdentity);
            if (groupModel == null) {
                return;
            }
            try {
                final GroupMessageReceiver receiver = serviceManager.getGroupService()
                    .createReceiver(groupModel);
                final String myIdentity = serviceManager.getUserService().getIdentity();

                if (!TestUtil.isEmptyOrNull(myIdentity)) {
                    serviceManager.getMessageService().createGroupStatus(
                        receiver,
                        GroupStatusDataModel.GroupStatusType.MEMBER_ADDED,
                        identityNew,
                        null,
                        null
                    );
                }
            } catch (ThreemaException x) {
                logger.error("Could not create group state after new member was added", x);
            }

            //reset avatar to recreate it!
            serviceManager.getAvatarCacheService().reset(groupModel);
        }

        @Override
        public void onMemberLeave(@NonNull GroupIdentity groupIdentity, @NonNull String identityLeft) {
            GroupModel groupModel = getGroupModel(groupIdentity);
            if (groupModel == null) {
                return;
            }
            try {
                final GroupMessageReceiver receiver = serviceManager.getGroupService()
                    .createReceiver(groupModel);

                serviceManager.getMessageService().createGroupStatus(
                    receiver,
                    GroupStatusDataModel.GroupStatusType.MEMBER_LEFT,
                    identityLeft,
                    null,
                    null
                );

                BallotService ballotService = serviceManager.getBallotService();
                ballotService.removeVotes(receiver, identityLeft);
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        }

        @Override
        public void onMemberKicked(@NonNull GroupIdentity groupIdentity, String identityKicked) {
            final String myIdentity = serviceManager.getUserService().getIdentity();

            GroupModel groupModel = getGroupModel(groupIdentity);
            if (groupModel == null) {
                return;
            }

            if (myIdentity != null && myIdentity.equals(identityKicked)) {
                // my own member status has changed
                try {
                    serviceManager.getNotificationService().cancelGroupCallNotification(groupModel.getId());
                    serviceManager.getConversationService().refresh(groupModel);
                } catch (Exception e) {
                    logger.error("Exception", e);
                }
            }
            try {
                final GroupMessageReceiver receiver = serviceManager.getGroupService().createReceiver(groupModel);

                serviceManager.getMessageService().createGroupStatus(
                    receiver,
                    GroupStatusDataModel.GroupStatusType.MEMBER_KICKED,
                    identityKicked,
                    null,
                    null
                );

                BallotService ballotService = serviceManager.getBallotService();
                ballotService.removeVotes(receiver, identityKicked);
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        }

        @Override
        public void onUpdate(@NonNull GroupIdentity groupIdentity) {
            try {
                GroupModel groupModel = getGroupModel(groupIdentity);
                if (groupModel == null) {
                    return;
                }
                serviceManager.getConversationService().refresh(groupModel);
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        }

        @Override
        public void onLeave(@NonNull GroupIdentity groupIdentity) {
            RuntimeUtil.runOnWorkerThread(() -> {
                try {
                    GroupModel groupModel = getGroupModel(groupIdentity);
                    if (groupModel == null) {
                        return;
                    }
                    serviceManager.getConversationService().refresh(groupModel);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            });
        }

        @Override
        public void onGroupStateChanged(@NonNull GroupIdentity groupIdentity, @GroupService.GroupState int oldState, @GroupService.GroupState int newState) {
            logger.debug("onGroupStateChanged: {} -> {}", oldState, newState);
            GroupModel groupModel = getGroupModel(groupIdentity);
            if (groupModel == null) {
                return;
            }

            showNotesGroupNotice(groupModel, oldState, newState);
        }

        @Nullable
        private GroupModel getGroupModel(@NonNull GroupIdentity groupIdentity) {
            try {
                GroupService groupService = serviceManager.getGroupService();
                groupService.removeFromCache(groupIdentity);
                GroupModel groupModel = groupService.getByGroupIdentity(groupIdentity);
                if (groupModel == null) {
                    logger.error("Group model is null");
                }
                return groupModel;
            } catch (MasterKeyLockedException e) {
                logger.error("Could not get group service", e);
                return null;
            }
        }
    };

    @NonNull
    private final DistributionListListener distributionListListener = new DistributionListListener() {
        @Override
        public void onCreate(DistributionListModel distributionListModel) {
            try {
                serviceManager.getConversationService().refresh(distributionListModel);
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        }

        @Override
        public void onModify(DistributionListModel distributionListModel) {
            RuntimeUtil.runOnWorkerThread(() -> {
                try {
                    serviceManager.getConversationService().refresh(distributionListModel);
                    ShortcutUtil.updatePinnedShortcut(serviceManager.getDistributionListService().createReceiver(distributionListModel));
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            });
        }
    };

    @NonNull
    private final MessageListener messageListener = new MessageListener() {
        @Override
        public void onNew(AbstractMessageModel newMessage) {
            logger.debug("MessageListener.onNewMessage");
            ConversationService conversationService;
            try {
                conversationService = serviceManager.getConversationService();
            } catch (ThreemaException e) {
                logger.error("Could not get conversation service", e);
                return;
            }
            if (!newMessage.isStatusMessage()) {
                ConversationModel conversationModel = conversationService.refresh(newMessage);
                if (conversationModel != null) {
                    // Show notification only if there is a conversation
                    showConversationNotification(newMessage, false);
                }
            } else if (newMessage.getType() == MessageType.GROUP_CALL_STATUS) {
                conversationService.refresh(newMessage);
            }
        }

        @Override
        public void onModified(List<AbstractMessageModel> modifiedMessageModels) {
            logger.debug("MessageListener.onModified");
            for (final AbstractMessageModel modifiedMessageModel : modifiedMessageModels) {
                if (modifiedMessageModel.isStatusMessage()) {
                    continue;
                }
                try {
                    ConversationService conversationService =
                        serviceManager.getConversationService();
                    ConversationModel conversationModel =
                        conversationService.refresh(modifiedMessageModel);
                    if (conversationModel != null &&
                        modifiedMessageModel.getType() == MessageType.IMAGE) {
                        // Only show a notification if there is a conversation
                        showConversationNotification(modifiedMessageModel, true);
                    }
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            }
        }

        @Override
        public void onRemoved(AbstractMessageModel removedMessageModel) {
            logger.debug("MessageListener.onRemoved");
            if (!removedMessageModel.isStatusMessage()) {
                try {
                    serviceManager.getConversationService().refreshWithDeletedMessage(removedMessageModel);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            }
        }

        @Override
        public void onRemoved(List<AbstractMessageModel> removedMessageModels) {
            logger.debug("MessageListener.onRemoved multi");
            for (final AbstractMessageModel removedMessageModel : removedMessageModels) {
                if (!removedMessageModel.isStatusMessage()) {
                    try {
                        serviceManager.getConversationService().refreshWithDeletedMessage(removedMessageModel);
                    } catch (ThreemaException e) {
                        logger.error("Exception", e);
                    }
                }
            }
        }
    };

    @NonNull
    private final EditMessageListener editMessageListener = message -> showConversationNotification(message, true);

    @NonNull
    private final MessageDeletedForAllListener messageDeletedForAllListener = message -> showConversationNotification(message, true);

    @NonNull
    private final ServerMessageListener serverMessageListener = new ServerMessageListener() {
        @Override
        public void onAlert(ServerMessageModel serverMessage) {
            NotificationService notificationService = serviceManager.getNotificationService();
            notificationService.showServerMessage(serverMessage);
        }

        @Override
        public void onError(ServerMessageModel serverMessage) {
            NotificationService notificationService = serviceManager.getNotificationService();
            notificationService.showServerMessage(serverMessage);
        }
    };

    @NonNull
    private final ContactListener contactListener = new ContactListener() {
        @Override
        public void onModified(final @NonNull String identity) {
            final ContactModel modifiedContact = serviceManager.getDatabaseService().getContactModelFactory().getByIdentity(identity);
            if (modifiedContact == null) {
                return;
            }
            final ch.threema.data.models.ContactModel modifiedContactModel = serviceManager.getModelRepositories().getContacts().getByIdentity(identity);
            if (modifiedContactModel == null) {
                return;
            }

            RuntimeUtil.runOnWorkerThread(() -> {
                try {
                    final ConversationService conversationService = serviceManager.getConversationService();
                    final ContactService contactService = serviceManager.getContactService();

                    // Refresh conversation cache
                    conversationService.updateContactConversation(modifiedContact);
                    conversationService.refresh(modifiedContactModel);

                    ContactMessageReceiver messageReceiver = contactService.createReceiver(modifiedContactModel);
                    if (messageReceiver != null) {
                        ShortcutUtil.updatePinnedShortcut(messageReceiver);
                    }
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            });
        }

        @Override
        public void onAvatarChanged(final @NonNull String identity) {
            RuntimeUtil.runOnWorkerThread(() -> {
                try {
                    ContactMessageReceiver messageReceiver = serviceManager.getContactService().createReceiver(identity);
                    if (messageReceiver != null) {
                        ShortcutUtil.updatePinnedShortcut(messageReceiver);
                    }
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            });
        }
    };

    @NonNull
    private final ContactSettingsListener contactSettingsListener = new ContactSettingsListener() {
        @Override
        public void onSortingChanged() {
            //do nothing!
        }

        @Override
        public void onNameFormatChanged() {
            //do nothing
        }

        @Override
        public void onAvatarSettingChanged() {
            //reset the avatar cache!
            AvatarCacheService s = serviceManager.getAvatarCacheService();
            s.clear();
        }

        @Override
        public void onInactiveContactsSettingChanged() {

        }

        @Override
        public void onNotificationSettingChanged(String uid) {

        }
    };

    @NonNull
    private final ConversationListener conversationListener = new ConversationListener() {
        @Override
        public void onNew(@NonNull ConversationModel conversationModel) {
        }

        @Override
        public void onModified(@NonNull ConversationModel modifiedConversationModel, @Nullable Integer oldPosition) {
        }

        @Override
        public void onRemoved(@NonNull ConversationModel conversationModel) {
            //remove notification!
            NotificationService notificationService = serviceManager.getNotificationService();
            notificationService.cancel(conversationModel);
        }

        @Override
        public void onModifiedAll() {
        }
    };

    @NonNull
    private final BallotVoteListener ballotVoteListener = new BallotVoteListener() {
        @Override
        public void onSelfVote(BallotModel ballotModel) {
        }

        @Override
        public void onVoteChanged(BallotModel ballotModel, String votingIdentity, boolean isFirstVote) {
            //add group state

            //DISABLED
            ServiceManager s = ThreemaApplication.getServiceManager();
            if (s != null) {
                try {
                    BallotService ballotService = s.getBallotService();
                    ContactService contactService = s.getContactService();
                    GroupService groupService = s.getGroupService();
                    MessageService messageService = s.getMessageService();
                    UserService userService = s.getUserService();

                    if (TestUtil.required(ballotModel, contactService, groupService, messageService, userService)) {
                        LinkBallotModel linkBallotModel = ballotService.getLinkedBallotModel(ballotModel);
                        if (linkBallotModel != null) {
                            GroupStatusDataModel.GroupStatusType type = null;
                            MessageReceiver<? extends AbstractMessageModel> receiver = null;
                            if (linkBallotModel instanceof GroupBallotModel) {
                                GroupModel groupModel = groupService.getById(((GroupBallotModel) linkBallotModel).getGroupId());

                                // its a group ballot,write status
                                receiver = groupService.createReceiver(groupModel);
                                // reset archived status
                                groupService.setIsArchived(
                                    groupModel.getCreatorIdentity(),
                                    groupModel.getApiGroupId(),
                                    false,
                                    TriggerSource.LOCAL
                                );

                            } else if (linkBallotModel instanceof IdentityBallotModel) {
                                String identity = ((IdentityBallotModel) linkBallotModel).getIdentity();

                                // not implemented
                                receiver = contactService.createReceiver(contactService.getByIdentity(identity));
                                // reset archived status
                                contactService.setIsArchived(identity, false, TriggerSource.LOCAL);
                            }

                            if (ballotModel.getType() == BallotModel.Type.RESULT_ON_CLOSE) {
                                // Only show status message for first vote from a voter on private voting
                                if (isFirstVote) {
                                    // On private voting, only show default update msg!
                                    type = GroupStatusDataModel.GroupStatusType.RECEIVED_VOTE;
                                }
                            } else if (receiver != null) {
                                if (isFirstVote) {
                                    type = GroupStatusDataModel.GroupStatusType.FIRST_VOTE;
                                } else {
                                    type = GroupStatusDataModel.GroupStatusType.MODIFIED_VOTE;
                                }
                            }

                            if (
                                linkBallotModel instanceof GroupBallotModel
                                    && (type == GroupStatusDataModel.GroupStatusType.FIRST_VOTE
                                    || type == GroupStatusDataModel.GroupStatusType.MODIFIED_VOTE)
                                    && !BallotUtil.isMine(ballotModel, userService)
                            ) {
                                // Only show votes (and vote changes) to the creator of the ballot in a group
                                return;
                            }

                            if (type != null && receiver instanceof GroupMessageReceiver) {
                                messageService.createGroupStatus(
                                    (GroupMessageReceiver) receiver,
                                    type,
                                    votingIdentity,
                                    ballotModel.getName(),
                                    null
                                );
                            }

                            // now check if every participant has voted
                            if (isFirstVote
                                && ballotService.getPendingParticipants(ballotModel.getId()).isEmpty()
                                && receiver instanceof GroupMessageReceiver
                            ) {
                                messageService.createGroupStatus(
                                    (GroupMessageReceiver) receiver,
                                    GroupStatusDataModel.GroupStatusType.VOTES_COMPLETE,
                                    null,
                                    ballotModel.getName(),
                                    null
                                );
                            }
                        }
                    }
                } catch (ThreemaException x) {
                    logger.error("Exception", x);
                }
            }
        }

        @Override
        public void onVoteRemoved(BallotModel ballotModel, String votingIdentity) {
            //ignore
        }

        @Override
        public boolean handle(BallotModel ballotModel) {
            //handle all
            return true;
        }
    };

    @NonNull
    private final ContentObserver contentObserverChangeContactNames = new ContentObserver(null) {
        private boolean isRunning = false;

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            logger.info("Contact name change observed");

            if (selfChange || isRunning) {
                logger.info("Contact name change observer already running");
                return;
            }

            try {
                this.isRunning = true;
                onAndroidContactChangeLock.lock();
                logger.info("Starting to update all contact names from android contacts");

                if (serviceManager.getSynchronizeContactsService().isSynchronizationInProgress()) {
                    logger.warn("Aborting contact name change observer as a contact synchronization is currently in progress");
                    return;
                }

                if (!serviceManager.getPreferenceService().isSyncContacts()) {
                    logger.warn("Contact synchronization is not enabled. Aborting.");
                    return;
                }

                boolean success = serviceManager.getContactService().updateAllContactNamesFromAndroidContacts();
                logger.info("Finished updating contact names from android contacts (success={})", success);
            } catch (MasterKeyLockedException masterKeyLockedException) {
                logger.error("Cantact name change observer could not be run successfully", masterKeyLockedException);
            } finally {
                this.isRunning = false;
                onAndroidContactChangeLock.unlock();
            }
        }
    };

    @NonNull
    private final SynchronizeContactsListener synchronizeContactsListener = new SynchronizeContactsListener() {
        @Override
        public void onStarted(SynchronizeContactsRoutine startedRoutine) {
            //disable contact observer
            serviceManager.getContext().getContentResolver().unregisterContentObserver(contentObserverChangeContactNames);
        }

        @Override
        public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
            //enable contact observer
            serviceManager.getContext().getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                false,
                contentObserverChangeContactNames);
        }

        @Override
        public void onError(SynchronizeContactsRoutine finishedRoutine) {
            //enable contact observer
            serviceManager.getContext().getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                false,
                contentObserverChangeContactNames);
        }
    };

    @Nullable
    private ContactTypingListener contactTypingListener = null;

    private ContactTypingListener getContactTypingListener() {
        if (contactTypingListener == null) {
            contactTypingListener = (contactModel, isTyping) -> {
                //update the conversations
                try {
                    serviceManager.getConversationService()
                        .setIsTyping(contactModel, isTyping);
                } catch (ThreemaException e) {
                    logger.error("Exception", e);
                }
            };
        }

        return contactTypingListener;
    }

    @Nullable
    private NewSyncedContactsListener newSyncedContactListener = null;

    @NonNull
    private NewSyncedContactsListener getNewSyncedContactListener() {
        if (newSyncedContactListener == null) {
            newSyncedContactListener = contactModels -> {
                NotificationService notificationService = serviceManager.getNotificationService();
                notificationService.showNewSyncedContactsNotification(contactModels);
            };
        }
        return newSyncedContactListener;
    }

    @NonNull
    private final WebClientServiceListener webClientServiceListener = new WebClientServiceListener() {
        @Override
        public void onEnabled() {
            SessionWakeUpServiceImpl.getInstance()
                .processPendingWakeupsAsync();
        }

        @Override
        public void onStarted(
            @NonNull final WebClientSessionModel model,
            @NonNull final byte[] permanentKey,
            @NonNull final String browser
        ) {
            logger.info("WebClientListenerManager: onStarted");

            RuntimeUtil.runOnUiThread(() -> {
                String toastText = appContext.getString(R.string.webclient_new_connection_toast);
                if (model.getLabel() != null) {
                    toastText += " (" + model.getLabel() + ")";
                }
                Toaster.Companion.showToast(toastText, Toaster.Duration.LONG);

                final Intent intent = new Intent(appContext, SessionAndroidService.class);

                if (SessionAndroidService.isRunning()) {
                    intent.setAction(SessionAndroidService.ACTION_UPDATE);
                    logger.info("sending ACTION_UPDATE to SessionAndroidService");
                    appContext.startService(intent);
                } else {
                    logger.info("SessionAndroidService not running...starting");
                    intent.setAction(SessionAndroidService.ACTION_START);
                    logger.info("sending ACTION_START to SessionAndroidService");
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        // Starting on version S, foreground services cannot be started from the background.
                        // When battery optimizations are disabled (recommended for Threema Web), then no
                        // exception is thrown. Otherwise we just log it.
                        try {
                            ContextCompat.startForegroundService(appContext, intent);
                        } catch (ForegroundServiceStartNotAllowedException exception) {
                            logger.error("Couldn't start foreground service", exception);
                        }
                    } else {
                        ContextCompat.startForegroundService(appContext, intent);
                    }
                }
            });
        }

        @Override
        public void onStateChanged(
            @NonNull final WebClientSessionModel model,
            @NonNull final WebClientSessionState oldState,
            @NonNull final WebClientSessionState newState
        ) {
            logger.info("WebClientListenerManager: onStateChanged");

            if (newState == WebClientSessionState.DISCONNECTED) {
                RuntimeUtil.runOnUiThread(() -> {
                    logger.info("updating SessionAndroidService");
                    if (SessionAndroidService.isRunning()) {
                        final Intent intent = new Intent(appContext, SessionAndroidService.class);
                        intent.setAction(SessionAndroidService.ACTION_UPDATE);
                        logger.info("sending ACTION_UPDATE to SessionAndroidService");
                        appContext.startService(intent);
                    } else {
                        logger.info("SessionAndroidService not running...not updating");
                    }
                });
            }
        }

        @Override
        public void onStopped(@NonNull final WebClientSessionModel model, @NonNull final DisconnectContext reason) {
            logger.info("WebClientListenerManager: onStopped");

            RuntimeUtil.runOnUiThread(() -> {
                if (SessionAndroidService.isRunning()) {
                    final Intent intent = new Intent(appContext, SessionAndroidService.class);
                    intent.setAction(SessionAndroidService.ACTION_STOP);
                    logger.info("sending ACTION_STOP to SessionAndroidService");
                    appContext.startService(intent);
                } else {
                    logger.info("SessionAndroidService not running...not stopping");
                }
            });
        }
    };

    @NonNull
    private final WebClientWakeUpListener webClientWakeUpListener =
        () -> Toaster.Companion.showToast(R.string.webclient_protocol_version_to_old,
            Toaster.Duration.LONG);

    @NonNull
    private final VoipCallEventListener voipCallEventListener = new VoipCallEventListener() {
        private final Logger logger = LoggingUtil.getThreemaLogger("VoipCallEventListener");

        @Override
        public void onRinging(String peerIdentity) {
            this.logger.debug("onRinging {}", peerIdentity);
        }

        @Override
        public void onStarted(String peerIdentity, boolean outgoing) {
            final String direction = outgoing ? "to" : "from";
            this.logger.info("Call {} {} started", direction, peerIdentity);
        }

        @Override
        public void onFinished(long callId, @NonNull String peerIdentity, boolean outgoing, int duration) {
            final String direction = outgoing ? "to" : "from";
            this.logger.info("Call {} {} finished", direction, peerIdentity);
            this.saveStatus(peerIdentity,
                outgoing,
                VoipStatusDataModel.createFinished(callId, duration),
                true);
        }

        @Override
        public void onRejected(long callId, String peerIdentity, boolean outgoing, byte reason) {
            final String direction = outgoing ? "to" : "from";
            this.logger.info("Call {} {} rejected (reason {})", direction, peerIdentity, reason);
            this.saveStatus(peerIdentity,
                // on rejected incoming, the outgoing was rejected!
                !outgoing,
                VoipStatusDataModel.createRejected(callId, reason),
                true);
        }

        @Override
        public void onMissed(long callId, String peerIdentity, boolean accepted, @Nullable Date date) {
            this.logger.info("Call from {} missed", peerIdentity);
            this.saveStatus(peerIdentity,
                false,
                VoipStatusDataModel.createMissed(callId, date),
                accepted);
        }

        @Override
        public void onAborted(long callId, String peerIdentity) {
            this.logger.info("Call to {} aborted", peerIdentity);
            this.saveStatus(peerIdentity,
                true,
                VoipStatusDataModel.createAborted(callId),
                true);
        }

        private void saveStatus(
            @NonNull String identity,
            boolean isOutbox,
            @NonNull VoipStatusDataModel status,
            boolean isRead
        ) {
            try {
                // Services
                final IdentityStore identityStore = serviceManager.getIdentityStore();
                final ContactService contactService = serviceManager.getContactService();
                final MessageService messageService = serviceManager.getMessageService();

                // If an incoming status message is not targeted at our own identity, something's wrong
                final String appIdentity = identityStore.getIdentity();
                if (TestUtil.compare(identity, appIdentity) && !isOutbox) {
                    this.logger.error("Could not save voip status (identity={}, appIdentity={}, outbox={})", identity, appIdentity, isOutbox);
                    return;
                }

                // Create status message
                final ContactModel contactModel = contactService.getByIdentity(identity);
                final ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
                messageService.createVoipStatus(status, receiver, isOutbox, isRead);
            } catch (ThreemaException e) {
                logger.error("Exception", e);
            }
        }
    };
}
