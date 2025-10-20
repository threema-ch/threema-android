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

package ch.threema.app.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.slf4j.Logger;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.MessageUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;

public class WidgetViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WidgetViewsFactory");

    @NonNull
    private final Context context;
    @NonNull
    private final ConversationService conversationService;
    @NonNull
    private final GroupService groupService;
    @NonNull
    private final ContactService contactService;
    @NonNull
    private final DistributionListService distributionListService;
    @NonNull
    private final LockAppService lockAppService;
    @NonNull
    private final PreferenceService preferenceService;
    @NonNull
    private final NotificationPreferenceService notificationPreferenceService;
    @NonNull
    private final MessageService messageService;
    @NonNull
    private final ConversationCategoryService conversationCategoryService;

    private List<ConversationModel> conversations;

    public WidgetViewsFactory(@NonNull Context context) throws ThreemaException {
        this.context = context;

        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            throw new ThreemaException("Could not get the service manager");
        }
        this.conversationService = serviceManager.getConversationService();
        this.contactService = serviceManager.getContactService();
        this.groupService = serviceManager.getGroupService();
        this.distributionListService = serviceManager.getDistributionListService();
        this.messageService = serviceManager.getMessageService();
        this.lockAppService = serviceManager.getLockAppService();
        this.preferenceService = serviceManager.getPreferenceService();
        this.notificationPreferenceService = serviceManager.getNotificationPreferenceService();
        this.conversationCategoryService = serviceManager.getConversationCategoryService();
    }


    /**
     * Called when your factory is first constructed. The same factory may be shared across
     * multiple RemoteViewAdapters depending on the intent passed.
     */
    @Override
    public void onCreate() {
        // In onCreate() you setup any connections / cursors to your data source. Heavy lifting,
        // for example downloading or creating content etc, should be deferred to onDataSetChanged()
        // or getViewAt(). Taking more than 20 seconds in this call will result in an ANR.
    }

    /**
     * Called when notifyDataSetChanged() is triggered on the remote adapter. This allows a
     * RemoteViewsFactory to respond to data changes by updating any internal references.
     * <p/>
     * Note: expensive tasks can be safely performed synchronously within this method. In the
     * interim, the old data will be displayed within the widget.
     *
     * @see android.appwidget.AppWidgetManager#notifyAppWidgetViewDataChanged(int[], int)
     */
    @Override
    public void onDataSetChanged() {
        conversations = conversationService.getAll(false, new ConversationService.Filter() {
            @Override
            public boolean onlyUnread() {
                return true;
            }

            @Override
            public boolean noHiddenChats() {
                return preferenceService.isPrivateChatsHidden();
            }

        });
        logger.info("Conversations updated");
    }

    /**
     * Called when the last RemoteViewsAdapter that is associated with this factory is
     * unbound.
     */
    @Override
    public void onDestroy() {

    }

    /**
     * @return Count of items.
     */
    @Override
    public int getCount() {
        if (!lockAppService.isLocked() &&
            notificationPreferenceService.isShowMessagePreview() &&
            conversations != null
        ) {
            return conversations.size();
        } else {
            return 0;
        }
    }

    /**
     * Note: expensive tasks can be safely performed synchronously within this method, and a
     * loading view will be displayed in the interim. See {@link #getLoadingView()}.
     *
     * @param position The position of the item within the Factory's data set of the item whose
     *                 view we want.
     * @return A RemoteViews object corresponding to the data at the specified position.
     */
    @Override
    public RemoteViews getViewAt(int position) {
        if (conversations != null && !conversations.isEmpty() && position < conversations.size()) {
            ConversationModel conversationModel = conversations.get(position);

            if (conversationModel != null) {
                @Nullable String message = "";
                String sender = "", date = "", count = "";
                Bitmap avatar = null;
                Bundle extras = new Bundle();
                String uniqueId = conversationModel.messageReceiver.getUniqueIdString();

                if (!this.lockAppService.isLocked() && notificationPreferenceService.isShowMessagePreview()) {
                    sender = conversationModel.messageReceiver.getDisplayName();

                    if (conversationModel.isContactConversation()) {
                        ContactModel contact = conversationModel.getContact();
                        String identity = contact != null ? contact.getIdentity() : null;
                        avatar = contactService.getAvatar(identity, false);
                        extras.putString(AppConstants.INTENT_DATA_CONTACT, identity);
                    } else if (conversationModel.isGroupConversation()) {
                        avatar = groupService.getAvatar(conversationModel.getGroup(), false);
                        extras.putLong(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, conversationModel.getGroup().getId());
                    } else if (conversationModel.isDistributionListConversation()) {
                        avatar = distributionListService.getAvatar(conversationModel.getDistributionList(), false);
                        extras.putLong(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, conversationModel.getDistributionList().getId());
                    }

                    count = Long.toString(conversationModel.getUnreadCount());

                    if (conversationCategoryService.isPrivateChat(uniqueId)) {
                        message = context.getString(R.string.private_chat_subject);
                    } else if (conversationModel.latestMessage != null) {
                        AbstractMessageModel messageModel = conversationModel.latestMessage;
                        message = messageService.getMessageString(messageModel, 200).getMessage();
                        date = MessageUtil.getDisplayDate(context, conversationModel.latestMessage, false);
                    }
                } else {
                    sender = context.getString(R.string.new_unprocessed_messages);
                    message = context.getString(R.string.new_unprocessed_messages_description);
                    if (conversationModel.latestMessage != null) {
                        date = MessageUtil.getDisplayDate(context, conversationModel.latestMessage, false);
                    }
                }

                // Construct a remote views item based on the app widget item XML file,
                // and set the text based on the position.
                RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.item_widget);
                rv.setTextViewText(R.id.sender_text, sender);
                rv.setTextViewText(R.id.message_text, message);
                rv.setTextViewText(R.id.msg_date, date);
                rv.setTextViewText(R.id.message_count, count);
                if (avatar != null) {
                    rv.setImageViewBitmap(R.id.avatar, avatar);
                } else {
                    rv.setImageViewResource(R.id.avatar, R.drawable.ic_contact);
                }

                // Next, set a fill-intent, which will be used to fill in the pending intent template
                // that is set on the collection view in StackWidgetProvider.
                Intent fillInIntent = new Intent();
                fillInIntent.putExtras(extras);
                // Make it possible to distinguish the individual on-click
                // action of a given item
                rv.setOnClickFillInIntent(R.id.item_layout, fillInIntent);

                // Return the remote views object.
                return rv;
            }
        }
        return null;
    }

    /**
     * This allows for the use of a custom loading view which appears between the time that
     * {@link #getViewAt(int)} is called and returns. If null is returned, a default loading
     * view will be used.
     *
     * @return The RemoteViews representing the desired loading view.
     */
    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    /**
     * @return The number of types of Views that will be returned by this factory.
     */
    @Override
    public int getViewTypeCount() {
        return 1;
    }

    /**
     * @param position The position of the item within the data set whose row id we want.
     * @return The id of the item at the specified position.
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * @return True if the same id always refers to the same object.
     */
    @Override
    public boolean hasStableIds() {
        return true;
    }
}
