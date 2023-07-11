/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public interface ConversationService {

	interface Filter {
		boolean onlyUnread();
		boolean noDistributionLists();
		boolean noHiddenChats();
		boolean noInvalid();
		default String filterQuery() {
			return null;
		}
	}

	/**
	 * return all conversation model
	 * @param forceReloadFromDatabase force a reload from database
	 */
	List<ConversationModel> getAll(boolean forceReloadFromDatabase);

	/**
	 * return a list of all conversation models
	 * @param forceReloadFromDatabase force a reload from database
	 */
	List<ConversationModel> getAll(boolean forceReloadFromDatabase, Filter filter);

	/**
	 * return a list of all conversation models that have been archived and match the constraint (case-insensitive match)
	 */
	List<ConversationModel> getArchived(String constraint);

	/**
	 * return the number of conversations that have been archived
	 */
	int getArchivedCount();

	/**
	 * update the conversation cache entry for the given contact model
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
	 * @param conversationModel
	 */
	void archive(ConversationModel conversationModel);

	/**
	 * clear archived flag in archived conversations
	 * @param conversations
	 */
	void unarchive(List<ConversationModel> conversations);

	/**
	 * clear a conversation (remove all messages)
	 */
	boolean clear(ConversationModel conversation);

	/**
	 * clear multiple conversations and fire appropriate listeners. this does not delete the messages itself!
	 * @param conversations
	 */
	void clear(ConversationModel[] conversations);

	/**
	 * clear a conversation (remove all messages)
	 */
	void clear(@NonNull MessageReceiver messageReceiver);

	/**
	 * clear conversation mapped with a distribution list
	 */
	boolean removed(DistributionListModel distributionListModel);

	/**
	 * clear a conversation model mapped with a group
	 */
	boolean removed(GroupModel groupModel);

	/**
	 * clear a conversation model mapped with a contact
	 */
	boolean removed(ContactModel contactModel);

	/**
	 * reset all cached data
	 */
	boolean reset();

	/**
	 *
	 */
	boolean hasConversations();
}
