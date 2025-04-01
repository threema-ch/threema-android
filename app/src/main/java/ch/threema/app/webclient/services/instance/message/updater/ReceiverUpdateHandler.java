/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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

package ch.threema.app.webclient.services.instance.message.updater;

import org.msgpack.core.MessagePackException;
import org.slf4j.Logger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.converter.Contact;
import ch.threema.app.webclient.converter.DistributionList;
import ch.threema.app.webclient.converter.Group;
import ch.threema.app.webclient.converter.MsgpackObjectBuilder;
import ch.threema.app.webclient.converter.Receiver;
import ch.threema.app.webclient.converter.Utils;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.app.webclient.services.instance.MessageDispatcher;
import ch.threema.app.webclient.services.instance.MessageUpdater;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

/**
 * Notify Threema Web about changes to receivers (contacts, groups, distribution lists).
 */
@WorkerThread
public class ReceiverUpdateHandler extends MessageUpdater {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ReceiverUpdateHandler");

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
        Protocol.ARGUMENT_MODE_NEW,
        Protocol.ARGUMENT_MODE_MODIFIED,
        Protocol.ARGUMENT_MODE_REMOVED,
    })
    private @interface UpdateMode {
    }

    // Handler
    private final @NonNull HandlerExecutor handler;

    // Listeners
    private final ContactListener contactListener;
    private final GroupListener groupListener;
    private final DistributionListListener distributionListListener;

    // Dispatchers
    private final @NonNull MessageDispatcher dispatcher;

    // Services
    private final @NonNull DatabaseServiceNew databaseServiceNew;
    private final @NonNull SynchronizeContactsService synchronizeContactsService;

    @AnyThread
    public ReceiverUpdateHandler(
        @NonNull HandlerExecutor handler,
        @NonNull MessageDispatcher dispatcher,
        @NonNull DatabaseServiceNew databaseServiceNew,
        @NonNull SynchronizeContactsService synchronizeContactsService
    ) {
        super(Protocol.SUB_TYPE_RECEIVER);
        this.handler = handler;
        this.dispatcher = dispatcher;
        this.databaseServiceNew = databaseServiceNew;
        this.synchronizeContactsService = synchronizeContactsService;
        this.contactListener = new ContactListener();
        this.groupListener = new GroupListener();
        this.distributionListListener = new DistributionListListener();
    }

    @Override
    public void register() {
        logger.debug("register()");
        ListenerManager.contactListeners.add(this.contactListener);
        ListenerManager.groupListeners.add(this.groupListener);
        ListenerManager.distributionListListeners.add(this.distributionListListener);
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    @Override
    public void unregister() {
        logger.debug("unregister()");
        ListenerManager.contactListeners.remove(this.contactListener);
        ListenerManager.groupListeners.remove(this.groupListener);
        ListenerManager.distributionListListeners.remove(this.distributionListListener);
    }

    @AnyThread
    private void updateContact(final ContactModel contact, @UpdateMode String mode) {
        handler.post(new Runnable() {
            @Override
            @WorkerThread
            public void run() {
                try {
                    // Convert contact and dispatch
                    MsgpackObjectBuilder data = Contact.convert(contact);
                    ReceiverUpdateHandler.this.update(new Utils.ModelWrapper(contact), data, mode);
                } catch (ConversionException e) {
                    logger.error("Exception", e);
                }
            }
        });
    }

    @AnyThread
    private void updateGroup(GroupModel group, @UpdateMode String mode) {
        handler.post(new Runnable() {
            @Override
            @WorkerThread
            public void run() {
                try {
                    // Convert contact and dispatch
                    MsgpackObjectBuilder data = Group.convert(group);
                    ReceiverUpdateHandler.this.update(new Utils.ModelWrapper(group), data, mode);
                } catch (ConversionException e) {
                    logger.error("Exception", e);
                }
            }
        });
    }

    @AnyThread
    private void updateDistributionList(DistributionListModel distributionList, @UpdateMode String mode) {
        handler.post(new Runnable() {
            @Override
            @WorkerThread
            public void run() {
                try {
                    // Convert contact and dispatch
                    MsgpackObjectBuilder data = DistributionList.convert(distributionList);
                    ReceiverUpdateHandler.this.update(new Utils.ModelWrapper(distributionList), data, mode);
                } catch (ConversionException e) {
                    logger.error("Exception", e);
                }
            }
        });
    }

    private void update(final Utils.ModelWrapper model, final MsgpackObjectBuilder data, final @UpdateMode String mode) {
        try {
            // Convert message and prepare arguments
            MsgpackObjectBuilder args = Receiver.getArguments(model);
            args.put(Protocol.ARGUMENT_MODE, mode);

            // Send message
            logger.debug("Sending receiver update");
            send(dispatcher, data, args);
        } catch (ConversionException | MessagePackException e) {
            logger.error("Exception", e);
        }
    }

    /**
     * Listen for contact changes.
     */
    @AnyThread
    private class ContactListener implements ch.threema.app.listeners.ContactListener {
        @Override
        public void onModified(final @NonNull String identity) {
            if (synchronizeContactsService.isFullSyncInProgress()) {
                // A sync is currently in progress. This causes a *lot* of onModified
                // listeners to be called.
                // To avoid flooding the webclient with updates, we simply ignore the
                // updates and send the entire receivers list as soon as the sync is done.
                logger.debug("Ignoring onModified (contact sync in progress)");
                return;
            }

            final ContactModel modifiedContactModel = getContactModelByIdentity(identity);
            if (modifiedContactModel != null) {
                updateContact(modifiedContactModel, Protocol.ARGUMENT_MODE_MODIFIED);
            }
        }

        @Override
        public void onNew(final @NonNull String identity) {
            final ContactModel newContactModel = getContactModelByIdentity(identity);
            if (newContactModel != null) {
                updateContact(newContactModel, Protocol.ARGUMENT_MODE_NEW);
            }
        }

        @Override
        public void onRemoved(@NonNull String identity) {
            handler.post(new Runnable() {
                @Override
                @WorkerThread
                public void run() {
                    try {
                        MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
                        builder.put(Receiver.ID, identity);
                        ReceiverUpdateHandler.this.update(
                            new Utils.ModelWrapper(Receiver.Type.CONTACT, identity),
                            builder,
                            Protocol.ARGUMENT_MODE_REMOVED
                        );
                    } catch (ConversionException e) {
                        logger.error("Exception", e);
                    }
                }
            });
        }

        @Nullable
        private ContactModel getContactModelByIdentity(@NonNull String identity) {
            return databaseServiceNew.getContactModelFactory().getByIdentity(identity);
        }
    }

    @AnyThread
    private class GroupListener implements ch.threema.app.listeners.GroupListener {
        @Override
        public void onCreate(GroupModel newGroupModel) {
            logger.debug("Group Listener: onCreate");
            updateGroup(newGroupModel, Protocol.ARGUMENT_MODE_NEW);
        }

        @Override
        public void onRename(GroupModel groupModel) {
            logger.debug("Group Listener: onRename");
            updateGroup(groupModel, Protocol.ARGUMENT_MODE_MODIFIED);
        }

        @Override
        public void onRemove(GroupModel removedGroupModel) {
            // TODO: We should probably send an empty response
            logger.debug("Group Listener: onRemove");
            updateGroup(removedGroupModel, Protocol.ARGUMENT_MODE_REMOVED);
        }

        @Override
        public void onNewMember(GroupModel group, String newIdentity) {
            logger.debug("Group Listener: onNewMember");
            updateGroup(group, Protocol.ARGUMENT_MODE_MODIFIED);
        }

        @Override
        public void onMemberLeave(GroupModel group, String identity) {
            logger.debug("Group Listener: onMemberLeave");
            updateGroup(group, Protocol.ARGUMENT_MODE_MODIFIED);
        }

        @Override
        public void onMemberKicked(GroupModel group, String identity) {
            logger.debug("Group Listener: onMemberKicked");
            updateGroup(group, Protocol.ARGUMENT_MODE_MODIFIED);
        }

        @Override
        public void onUpdate(GroupModel groupModel) {
            logger.debug("Group Listener: onUpdate");
            updateGroup(groupModel, Protocol.ARGUMENT_MODE_MODIFIED);
        }

        @Override
        public void onLeave(GroupModel groupModel) {
            logger.debug("Group Listener: onLeave");
            updateGroup(groupModel, Protocol.ARGUMENT_MODE_MODIFIED);
        }
    }

    @AnyThread
    private class DistributionListListener implements ch.threema.app.listeners.DistributionListListener {
        @Override
        public void onCreate(DistributionListModel distributionListModel) {
            logger.debug("Distribution List Listener: onCreate");
            updateDistributionList(distributionListModel, Protocol.ARGUMENT_MODE_NEW);
        }

        @Override
        public void onModify(DistributionListModel distributionListModel) {
            logger.debug("Distribution List Listener: onModify");
            updateDistributionList(distributionListModel, Protocol.ARGUMENT_MODE_MODIFIED);
        }

        @Override
        public void onRemove(DistributionListModel distributionListModel) {
            logger.debug("Distribution List Listener: onRemove");
            updateDistributionList(distributionListModel, Protocol.ARGUMENT_MODE_REMOVED);
        }
    }
}
