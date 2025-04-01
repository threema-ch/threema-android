/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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

import androidx.annotation.NonNull;

import java.util.List;

import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public interface ConversationService {

    interface Filter {
        default boolean onlyUnread() {
            return false;
        }

        default boolean noDistributionLists() {
            return false;
        }

        default boolean noHiddenChats() {
            return false;
        }

        default boolean noInvalid() {
            return false;
        }

        default boolean onlyPersonal() {
            return false;
        }

        default String filterQuery() {
            return null;
        }
    }

    /**
     * Return all conversation models.
     *
     * @param forceReloadFromDatabase force a reload from database
     */
    @NonNull
    List<ConversationModel> getAll(boolean forceReloadFromDatabase);

    /**
     * Return a filtered list of all conversation models.
     *
     * @param forceReloadFromDatabase force a reload from database
     * @param filter                  an optional conversation filter
     */
    @NonNull
    List<ConversationModel> getAll(boolean forceReloadFromDatabase, @Nullable Filter filter);

    /**
     * Return a list of all conversation models that have been archived and match the
     * search query (case-insensitive match).
     */
    @NonNull
    List<ConversationModel> getArchived(@Nullable String searchQuery);

    /**
     * return the number of conversations that have been archived
     */
    int getArchivedCount();

    /**
     * update the conversation cache entry for the given contact model
     *
     * @param contactModel the contact model that is loaded again from the database
     */
    void updateContactConversation(@NonNull ContactModel contactModel);

    /**
     * refresh a conversation model with a modified message model
     */
    ConversationModel refresh(AbstractMessageModel modifiedMessageModel);

    /**
     * refresh conversation
     */
    ConversationModel refresh(ContactModel contactModel);

    /**
     * refresh conversation
     */
    ConversationModel refresh(GroupModel groupModel);

    /**
     * refresh conversation
     */
    ConversationModel refresh(DistributionListModel distributionListModel);

    /**
     * refresh conversation
     */
    ConversationModel refresh(@NonNull MessageReceiver receiverModel);

    /**
     * update the tags
     */
    void updateTags();

    /**
     * re-sort conversations
     */
    void sort();

    /**
     * set conversation model as "isTyping"
     */
    ConversationModel setIsTyping(ContactModel contact, boolean isTyping);

    /**
     * refresh a conversation model with a deleted message model
     */
    void refreshWithDeletedMessage(AbstractMessageModel modifiedMessageModel);

    /**
     * mark conversation as archived
     */
    void archive(@NonNull ConversationModel conversationModel);

    /**
     * clear archived flag in archived conversations
     *
     * @param conversations
     */
    void unarchive(List<ConversationModel> conversations);

    /**
     * Empty associated conversation (remove all messages).
     *
     * @param silentMessageUpdate do not fire MessageListener updates for removed messages if true
     * @return the number of removed messages.
     */
    int empty(ConversationModel conversation, boolean silentMessageUpdate);

    /**
     * Empty associated conversation (remove all messages).
     * <p>
     * The message listener onRemoved method will *not* be called for removed messages.
     *
     * @return the number of removed messages.
     */
    int empty(@NonNull MessageReceiver messageReceiver);

    /**
     * Empty associated conversation (remove all messages).
     * <p>
     * The message listener onRemoved method will *not* be called for removed messages.
     *
     * @return the number of removed messages.
     */
    int empty(@NonNull String identity);

    /**
     * Empty associated conversation (remove all messages).
     * <p>
     * The message listener onRemoved method will *not* be called for removed messages.
     *
     * @return the number of removed messages.
     */
    int empty(@NonNull GroupModel groupModel);

    /**
     * Empty associated conversation (remove all messages).
     * <p>
     * The message listener onRemoved method will *not* be called for removed messages.
     *
     * @return the number of removed messages.
     */
    int empty(@NonNull DistributionListModel distributionListModel);

    /**
     * Delete the contact conversation by removing all messages, setting `lastUpdate` to null
     * and removing it from the cache.
     *
     * @return the number of removed messages.
     */
    int delete(@NonNull ContactModel contactModel);

    /**
     * Delete the contact conversation by removing all messages, setting `lastUpdate` to null and
     * removing it from the cache.
     *
     * @param identity the identity of the contact
     * @return the number of removed messages.
     */
    int delete(@NonNull String identity);

    /**
     * Remove the group conversation from the cache, and thus hide it from the conversation list.
     * <p>
     * Note: The group model itself will not be removed, nor will lastUpdate be modified!
     * To delete a group and its messages, use the GroupService.
     */
    void removeFromCache(@NonNull GroupModel groupModel);

    /**
     * Remove the distribution list conversation from the cache, and thus hide it from the
     * conversation list.
     * <p>
     * Note: The distribution list model itself will not be removed, nor will lastUpdate be modified!
     * To delete a distribution list and its messages, use the DistributionListService.
     */
    void removeFromCache(@NonNull DistributionListModel distributionListModel);

    /**
     * reset all cached data
     */
    boolean reset();

    /**
     * Return whether or not there are any visible conversations.
     */
    boolean hasConversations();

    /**
     * Recalculate the `lastUpdate` field for all contacts, groups and distribution lists.
     */
    void calculateLastUpdateForAllConversations();
}
