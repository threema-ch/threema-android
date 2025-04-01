/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.asynctasks;

import android.os.AsyncTask;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

/**
 * Empty or delete one or more conversation/chat.
 * <p>
 * The primary use case is a user pressing the "delete" icon on a conversation
 * in the conversation list. This will show a dialog while the process is ongoing.
 * <p>
 * Note: Behavior with Mode.DELETE depends on the {@link MessageReceiver} passed in:
 * <p>
 * - Contacts: Delete conversation, but not contact
 * - Groups:
 * - Left: Delete conversation and group
 * - We are creator: Dissolve and delete group
 * - We are not creator: Leave and delete group
 * - Distribution lists: Delete distribution list
 */
public class EmptyOrDeleteConversationsAsyncTask extends AsyncTask<Void, Void, Void> {
    private static final Logger logger = LoggingUtil.getThreemaLogger("EmptyOrDeleteConversationAsyncTask");

    private static final String DIALOG_TAG_EMPTYING_OR_DELETING_CHAT = "edc";

    // Services
    private final @NonNull ConversationService conversationService;
    private final @NonNull GroupService groupService;
    private final @NonNull DistributionListService distributionListService;

    private final @NonNull Mode mode;
    private final MessageReceiver[] messageReceivers;
    private final @Nullable FragmentManager fragmentManager;
    private final @Nullable View snackbarFeedbackView;
    private final @Nullable Runnable runOnCompletion;

    public enum Mode {
        EMPTY,
        DELETE,
    }

    /**
     * @param mode                    Either EMPTY or DELETE
     * @param messageReceivers        The list of receivers for which to empty or delete the conversation
     * @param conversationService     Conversation service
     * @param groupService            Group service
     * @param distributionListService Distribution list service
     * @param fragmentManager         Fragment manager, required to show progress dialog
     * @param snackbarFeedbackView    If view is set, a snackbar message will be shown after completion
     * @param runOnCompletion         A runnable invoked after completion
     */
    public EmptyOrDeleteConversationsAsyncTask(
        @NonNull Mode mode,
        MessageReceiver[] messageReceivers,
        @NonNull ConversationService conversationService,
        @NonNull GroupService groupService,
        @NonNull DistributionListService distributionListService,
        @Nullable FragmentManager fragmentManager,
        @Nullable View snackbarFeedbackView,
        @Nullable Runnable runOnCompletion
    ) {
        this.mode = mode;
        this.messageReceivers = messageReceivers;
        this.conversationService = conversationService;
        this.groupService = groupService;
        this.distributionListService = distributionListService;
        this.fragmentManager = fragmentManager;
        this.snackbarFeedbackView = snackbarFeedbackView;
        this.runOnCompletion = runOnCompletion;
    }

    @Override
    protected void onPreExecute() {
        if (fragmentManager != null) {
            @StringRes int title;
            switch (this.mode) {
                case EMPTY:
                    title = R.string.emptying_chat;
                    break;
                case DELETE:
                    title = R.string.deleting_chat;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid mode: " + this.mode);
            }
            final GenericProgressDialog dialog = GenericProgressDialog.newInstance(title, R.string.emptying_chat_deleting_messages);
            dialog.show(fragmentManager, DIALOG_TAG_EMPTYING_OR_DELETING_CHAT);
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        // Empty or delete conversations
        logger.info("{} chat for {} receivers.", this.mode, messageReceivers.length);
        switch (this.mode) {
            case EMPTY:
                for (MessageReceiver receiver : this.messageReceivers) {
                    int countRemoved = this.conversationService.empty(receiver);
                    logger.info("Removed {} messages for receiver {} (type={}).", countRemoved, receiver.getUniqueIdString(), receiver.getType());
                }
                break;
            case DELETE:
                for (MessageReceiver receiver : this.messageReceivers) {
                    logger.info("Delete chat with receiver {} (type={}).", receiver.getUniqueIdString(), receiver.getType());
                    if (receiver instanceof ContactMessageReceiver) {
                        final ContactModel contactModel = ((ContactMessageReceiver) receiver).getContact();
                        this.deleteContactConversation(contactModel);
                    } else if (receiver instanceof GroupMessageReceiver) {
                        final GroupModel groupModel = ((GroupMessageReceiver) receiver).getGroup();
                        this.deleteGroupConversation(groupModel);
                    } else if (receiver instanceof DistributionListMessageReceiver) {
                        final DistributionListModel distributionListModel = ((DistributionListMessageReceiver) receiver).getDistributionList();
                        this.deleteDistributionList(distributionListModel);
                    } else {
                        throw new IllegalArgumentException("Unknown message receiver type");
                    }
                }
                break;
        }

        // Refresh message receivers
        for (MessageReceiver receiver : this.messageReceivers) {
            conversationService.refresh(receiver);
        }

        return null;
    }

    private void deleteContactConversation(@NonNull ContactModel contactModel) {
        this.conversationService.delete(contactModel);
    }

    private void deleteGroupConversation(@NonNull GroupModel groupModel) {
        // Note: Group conversations cannot be deleted without removing the group as well
        this.groupService.leaveOrDissolveAndRemoveFromLocal(groupModel);
    }

    private void deleteDistributionList(@NonNull DistributionListModel distributionListModel) {
        // Note: Distribution list conversations are removed along with the distribution list model
        this.distributionListService.remove(distributionListModel);
    }

    @Override
    protected void onPostExecute(Void result) {
        if (fragmentManager != null) {
            DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG_EMPTYING_OR_DELETING_CHAT, true);
        }

        if (mode == Mode.DELETE && snackbarFeedbackView != null && snackbarFeedbackView.isAttachedToWindow()) {
            final int count = this.messageReceivers.length;
            Snackbar.make(
                snackbarFeedbackView,
                ConfigUtils.getSafeQuantityString(ThreemaApplication.getAppContext(), R.plurals.chat_deleted, count, count),
                Snackbar.LENGTH_SHORT
            ).show();
        }

        if (runOnCompletion != null) {
            runOnCompletion.run();
        }
    }
}
