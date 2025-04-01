/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.app.receivers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.widget.Toast;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import androidx.core.app.NotificationManagerCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageModel;

public class ReSendMessagesBroadcastReceiver extends ActionBroadcastReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ReSendMessagesBroadcastReceiver");

    @Override
    @SuppressLint("StaticFieldLeak")
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult pendingResult = goAsync();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ArrayList<AbstractMessageModel> failedMessages = IntentDataUtil.getAbstractMessageModels(intent, messageService);

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.cancel(ThreemaApplication.UNSENT_MESSAGE_NOTIFICATION_ID);

                if (failedMessages.size() > 0) {
                    // we need to make sure there's a connection during delivery
                    lifetimeService.acquireConnection(TAG);

                    for (AbstractMessageModel failedMessage : failedMessages) {
                        MessageReceiver messageReceiver = getMessageReceiverFromMessageModel(failedMessage);
                        if (messageReceiver == null) {
                            logger.warn("Message receiver is null for failed message {}", failedMessage.getApiMessageId());
                            continue;
                        }
                        List<String> receiverIdentities = new ArrayList<>();
                        if (failedMessage instanceof GroupMessageModel) {
                            GroupMessageModel failedGroupMessage = (GroupMessageModel) failedMessage;
                            GroupModel group = groupService.getById(failedGroupMessage.getGroupId());
                            if (group == null) {
                                logger.warn("Group model not found for failed message {}", failedGroupMessage.getApiMessageId());
                                continue;
                            }
                            receiverIdentities.addAll(Arrays.asList(groupService.getGroupIdentities(group)));
                        } else {
                            receiverIdentities.add(failedMessage.getIdentity());
                        }
                        try {
                            messageService.resendMessage(failedMessage, messageReceiver, null, receiverIdentities, new MessageId(), TriggerSource.LOCAL);
                            notificationService.cancel(messageReceiver);
                        } catch (Exception e) {
                            RuntimeUtil.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(context, R.string.original_file_no_longer_avilable, Toast.LENGTH_LONG).show();
                                }
                            });
                            logger.error("Exception", e);
                        }
                    }
                    lifetimeService.releaseConnectionLinger(TAG, WEARABLE_CONNECTION_LINGER);
                }
                pendingResult.finish();
                return null;
            }
        }.execute();
    }

    private MessageReceiver getMessageReceiverFromMessageModel(AbstractMessageModel messageModel) {
        if (messageModel instanceof MessageModel) {
            return contactService.createReceiver(contactService.getByIdentity(messageModel.getIdentity()));
        } else if (messageModel instanceof GroupMessageModel) {
            return groupService.createReceiver(groupService.getById(((GroupMessageModel) messageModel).getGroupId()));
        } else if (messageModel instanceof DistributionListMessageModel) {
            return distributionListService.createReceiver(distributionListService.getById(((DistributionListMessageModel) messageModel).getDistributionListId()));
        }
        return null;
    }
}
