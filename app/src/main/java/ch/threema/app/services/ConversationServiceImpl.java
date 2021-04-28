/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

import net.sqlcipher.Cursor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.utils.MessageUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.DistributionListMessageModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.TagModel;

public class ConversationServiceImpl implements ConversationService {
	private static final Logger logger = LoggerFactory.getLogger(ConversationServiceImpl.class);

	private final List<ConversationModel> conversationCache;
	private final ConversationTagService conversationTagService;
	private final DatabaseServiceNew databaseServiceNew;
	private final ContactService contactService;
	private final GroupService groupService;
	private final DistributionListService distributionListService;
	private final MessageService messageService;
	private final DeadlineListService hiddenChatsListService;
	private boolean initAllLoaded = false;
	private final TagModel starTag, unreadTag;

	static class ConversationResult {
		public final int messageId;
		public final long count;
		public final String refId;

		ConversationResult(int messageId, long count, String refId) {
			this.messageId = messageId;
			this.count = count;
			this.refId = refId;
		}
	}


	public ConversationServiceImpl(
			CacheService cacheService,
			DatabaseServiceNew databaseServiceNew,
			ContactService contactService,
			GroupService groupService,
			DistributionListService distributionListService,
			MessageService messageService,
			DeadlineListService hiddenChatsListService,
			ConversationTagService conversationTagService) {
		this.databaseServiceNew = databaseServiceNew;
		this.contactService = contactService;
		this.groupService = groupService;
		this.distributionListService = distributionListService;
		this.messageService = messageService;
		this.hiddenChatsListService = hiddenChatsListService;
		this.conversationCache = cacheService.getConversationModelCache();
		this.conversationTagService = conversationTagService;

		this.starTag = conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_PIN);
		this.unreadTag = conversationTagService.getTagModel(ConversationTagServiceImpl.FIXED_TAG_UNREAD);
	}

	@Override
	public synchronized List<ConversationModel> getAll(boolean forceReloadFromDatabase) {
		return this.getAll(forceReloadFromDatabase, null);
	}

	@Override
	public synchronized List<ConversationModel> getAll(boolean forceReloadFromDatabase, final Filter filter) {
		logger.debug("getAll forceReloadFromDatabase = " + forceReloadFromDatabase);
		synchronized (this.conversationCache) {
			if (forceReloadFromDatabase || !this.initAllLoaded) {
				this.conversationCache.clear();
			}
			if (this.conversationCache.size() == 0) {

				logger.debug("start selecting");
				for(ConversationModelParser parser: new ConversationModelParser[] {
						new ContactConversationModelParser(),
						new GroupConversationModelParser(),
						new DistributionListConversationModelParser()
				}) {
					parser.processAll();
				}

				logger.debug("selection finished");
				this.initAllLoaded = true;
			}

			this.sort();

			//filter only if a filter object is set and one of the filter property contains a filter
			if(filter != null
					&& (filter.onlyUnread()
						|| filter.noDistributionLists()
						|| filter.noHiddenChats()
						|| filter.noInvalid()
						|| !TestUtil.empty(filter.filterQuery()))) {

				List<ConversationModel> filtered = this.conversationCache;
				if (filter.onlyUnread()) {
					logger.debug("filter unread");
					filtered = Functional.filter(filtered, new IPredicateNonNull<ConversationModel>() {
						@Override
						public boolean apply(@NonNull ConversationModel conversationModel) {
							return conversationModel.hasUnreadMessage();
						}
					});
				}

				if (filter.noDistributionLists()) {
					logger.debug("filter distribution lists");
					filtered = Functional.filter(filtered, new IPredicateNonNull<ConversationModel>() {
						@Override
						public boolean apply(@NonNull ConversationModel conversationModel) {
							return !conversationModel.isDistributionListConversation();
						}
					});
				}

				if (filter.noHiddenChats()) {
					logger.debug("filter hidden lists");
					filtered = Functional.filter(filtered, new IPredicateNonNull<ConversationModel>() {
						@Override
						public boolean apply(@NonNull ConversationModel conversationModel) {
							return !hiddenChatsListService.has(conversationModel.getReceiver().getUniqueIdString());
						}
					});
				}

				if (filter.noInvalid()) {
					logger.debug("filter chats with revoked contacts / left group that cannot receive messages");
					filtered = Functional.filter(filtered, new IPredicateNonNull<ConversationModel>() {
						@Override
						public boolean apply(@NonNull ConversationModel conversationModel) {
							if (conversationModel.isContactConversation()) {
								return conversationModel.getContact() != null && !(conversationModel.getContact().getState() == ContactModel.State.INVALID);
							} else if (conversationModel.isGroupConversation()) {
								return conversationModel.getGroup() != null && groupService.isGroupMember(conversationModel.getGroup());
							}
							return true;
						}
					});
				}

				if (!TestUtil.empty(filter.filterQuery())) {
					logger.debug("filter query");
					filtered = Functional.filter(filtered, new IPredicateNonNull<ConversationModel>() {
						@Override
						public boolean apply(@NonNull ConversationModel conversationModel) {
							return TestUtil.contains(filter.filterQuery(), conversationModel.getReceiver().getDisplayName());
						}
					});
				}

				return filtered;
			}
		}

		return this.conversationCache;
	}

	@Override
	public List<ConversationModel> getArchived() {
		List<ConversationModel> conversationModels = new ArrayList<>();

		for (ConversationModelParser parser : new ConversationModelParser[]{
				new ContactConversationModelParser(),
				new GroupConversationModelParser(),
				new DistributionListConversationModelParser()
		}) {
			parser.processArchived(conversationModels);
		}

		Collections.sort(conversationModels, (conversationModel, conversationModel2) -> {
			if (conversationModel2.getSortDate() == null || conversationModel.getSortDate() == null) {
				return 0;
			}
			return conversationModel2.getSortDate().compareTo(conversationModel.getSortDate());
		});

		return conversationModels;
	}

	@Override
	public int getArchivedCount() {
		String query = "SELECT" +
				getArchivedContactsCountQuery() +
				" + " +
				getArchivedGroupsCountQuery() +
				" + " +
				getArchivedDistListsCountQuery();

		Cursor c = databaseServiceNew.getReadableDatabase().rawQuery(query, null);
		if (c != null) {
			try {
				c.moveToNext();
				return (int)c.getLong(0);
			}
			catch (Exception ignored) {}
			finally {
				c.close();
			}
		}
		return 0;
	}

	private String getArchivedContactsCountQuery() {
		return "(SELECT COUNT(DISTINCT c.identity) FROM contacts c " +
				"INNER JOIN message m " +
				"ON c.identity = m.identity " +
				"WHERE m.isSaved = 1 " +
				"AND c.isArchived = 1)";
	}

	private String getArchivedGroupsCountQuery() {
		return "(SELECT COUNT(DISTINCT g.id) FROM m_group g " +
				"LEFT OUTER JOIN m_group_message gm " +
				"ON gm.groupId = g.id " +
				"AND gm.isStatusMessage = 0 " +
				"AND gm.isSaved = 1 " +
				"WHERE g.deleted != 1 " +
				"AND g.isArchived = 1)";
	}

	private String getArchivedDistListsCountQuery() {
		return "(SELECT COUNT(DISTINCT d.id) FROM distribution_list d " +
				"LEFT OUTER JOIN distribution_list_message dm " +
				"ON dm.distributionListId = d.id " +
				"AND dm.isStatusMessage = 0 " +
				"AND dm.isSaved = 1 " +
				"WHERE d.isArchived = 1)";
	}

	@Override
	public void sort() {
		List<String> taggedConversationUids = new ArrayList<>();
		for (ConversationTagModel tagModel : conversationTagService.getAll()) {
			if (tagModel.getTag().equals(starTag.getTag())) {
				taggedConversationUids.add(tagModel.getConversationUid());
			}
		}

		int size = taggedConversationUids.size();

		synchronized (this.conversationCache) {
			Collections.sort(this.conversationCache, new Comparator<ConversationModel>() {
				@Override
				public int compare(ConversationModel conversationModel, ConversationModel conversationModel2) {
					if (conversationModel2.getSortDate() == null || conversationModel.getSortDate() == null) {
						return 0;
					}

					if (size > 0) {
						boolean tagged1 = taggedConversationUids.contains(conversationModel.getUid());
						boolean tagged2 = taggedConversationUids.contains(conversationModel2.getUid());

						return tagged1 == tagged2 ? conversationModel2.getSortDate().compareTo(conversationModel.getSortDate()) :
							tagged2 ? 1 : -1;
					} else {
						return conversationModel2.getSortDate().compareTo(conversationModel.getSortDate());
					}
				}
			});

			//set new position
			int pos = 0;
			for(ConversationModel m: this.conversationCache) {
				m.setPosition(pos++);
			}
		}
	}

	@Override
	public synchronized ConversationModel refresh(AbstractMessageModel modifiedMessageModel) {
		ConversationModelParser parser = this.createParser(modifiedMessageModel);
		if(parser != null) {
			return parser.refresh(modifiedMessageModel);
		}
		return null;
	}

	@Override
	public synchronized ConversationModel refresh(ContactModel contactModel) {
		return new ContactConversationModelParser()
				.refresh(contactModel);
	}

	@Override
	public synchronized ConversationModel refresh(GroupModel groupModel) {
		return new GroupConversationModelParser()
				.refresh(groupModel);
	}

	@Override
	public synchronized ConversationModel refresh(DistributionListModel distributionListModel) {
		return new DistributionListConversationModelParser()
				.refresh(distributionListModel);
	}

	@Override
	public synchronized ConversationModel refresh(@NonNull MessageReceiver receiver) {
		switch (receiver.getType()) {
			case MessageReceiver.Type_CONTACT:
				return this.refresh(((ContactMessageReceiver)receiver).getContact());
			case MessageReceiver.Type_GROUP:
				return this.refresh(((GroupMessageReceiver)receiver).getGroup());
			case MessageReceiver.Type_DISTRIBUTION_LIST:
				return this.refresh(((DistributionListMessageReceiver)receiver).getDistributionList());
		}
		throw new IllegalStateException("Got ReceiverModel with invalid receiver type!");
	}

	@Override
	public synchronized ConversationModel setIsTyping(ContactModel contact, boolean isTyping) {
		ContactConversationModelParser p = new ContactConversationModelParser();
		ConversationModel conversationModel = p.getCached(p.getIndex(contact));
		if(conversationModel != null) {
			conversationModel.setIsTyping(isTyping);
			this.fireOnModifiedConversation(conversationModel);
		}
		return conversationModel;
	}

	@Override
	public synchronized void refreshWithDeletedMessage(AbstractMessageModel modifiedMessageModel) {
		ConversationModelParser parser = this.createParser(modifiedMessageModel);
		if(parser != null) {
			parser.messageDeleted(modifiedMessageModel);
		}
	}

	@Override
	public synchronized void archive(ConversationModel conversationModel) {
		this.conversationTagService.removeAll(conversationModel);

		if (hiddenChatsListService.has(conversationModel.getUid())) {
			hiddenChatsListService.remove(conversationModel.getUid());
		}

		conversationModel.setUnreadCount(0);

		if (conversationModel.isContactConversation()) {
			contactService.setIsArchived(conversationModel.getContact().getIdentity(), true);
		}
		else if (conversationModel.isGroupConversation()) {
			groupService.setIsArchived(conversationModel.getGroup(), true);
		}
		else if (conversationModel.isDistributionListConversation()) {
			distributionListService.setIsArchived(conversationModel.getDistributionList(), true);
		}

		synchronized (conversationCache) {
			conversationCache.remove(conversationModel);
		}
		ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
			@Override
			public void handle(ConversationListener listener) {
				listener.onRemoved(conversationModel);
			}
		});
	}

	@Override
	public void unarchive(List<ConversationModel> conversationModels) {
		for (ConversationModel conversationModel : conversationModels) {
			if (conversationModel.isContactConversation()) {
				contactService.setIsArchived(conversationModel.getContact().getIdentity(), false);
			}
			else if (conversationModel.isGroupConversation()) {
				groupService.setIsArchived(conversationModel.getGroup(), false);
			}
			else if (conversationModel.isDistributionListConversation()) {
				distributionListService.setIsArchived(conversationModel.getDistributionList(), false);
			}

			// Note: Don't call the conversation listener (onNew) here, that will be handled
			// already by the save() call in the contact/group/distributionlist-service.
		}
	}

	@Override
	public synchronized boolean clear(final ConversationModel conversation) {
		return this.clear(conversation, false);
	}

	@Override
	public synchronized void clear(final ConversationModel[] conversations) {
		for (ConversationModel conversation: conversations) {
			// Remove tags
			this.conversationTagService.removeAll(conversation);

			// Remove from cache if the conversation is a contact conversation
			if (!conversation.isGroupConversation() && !conversation.isDistributionListConversation()) {
				synchronized (this.conversationCache) {
					this.conversationCache.remove(conversation);
				}

				if (conversations.length == 1) {
					ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
						@Override
						public void handle(ConversationListener listener) {
							listener.onRemoved(conversation);
						}
					});
				}
			}
			else {
				conversation.setLatestMessage(null);
				conversation.setMessageCount(0);
				conversation.setUnreadCount(0);
				if (conversations.length == 1) {
					this.fireOnModifiedConversation(conversation);
				}
			}
		}

		//resort!
		this.sort();

		if (conversations.length > 1) {
			ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
				@Override
				public void handle(ConversationListener listener) {
					listener.onModifiedAll();
				}
			});
		}
	}

	@Override
	public synchronized void clear(@NonNull MessageReceiver receiver) {
		switch (receiver.getType()) {
			case MessageReceiver.Type_CONTACT:
				this.removed(((ContactMessageReceiver)receiver).getContact());
				break;
			case MessageReceiver.Type_GROUP:
				this.removed(((GroupMessageReceiver)receiver).getGroup());
				break;
			case MessageReceiver.Type_DISTRIBUTION_LIST:
				this.removed(((DistributionListMessageReceiver)receiver).getDistributionList());
				break;
			default:
				throw new IllegalStateException("Got ReceiverModel with invalid receiver type!");
		}
	}

	private synchronized boolean clear(final ConversationModel conversation, boolean removeFromCache) {
		for (AbstractMessageModel m : this.messageService.getMessagesForReceiver(conversation.getReceiver())) {
			this.messageService.remove(m, true);
		}

		// Remove tags
		this.conversationTagService.removeAll(conversation);

		// Remove from cache if the conversation is a contact conversation
		if(removeFromCache || (
				!conversation.isGroupConversation() && !conversation.isDistributionListConversation())) {
			synchronized (this.conversationCache) {
				this.conversationCache.remove(conversation);
			}

			ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
				@Override
				public void handle(ConversationListener listener) {
					listener.onRemoved(conversation);
				}
			});

			//resort!
			this.sort();

			return true;
		}
		else {
			conversation.setLatestMessage(null);
			conversation.setMessageCount(0);
			conversation.setUnreadCount(0);
			this.fireOnModifiedConversation(conversation);
		}

		return false;
	}
	@Override
	public synchronized boolean removed(DistributionListModel distributionListModel) {
		return new DistributionListConversationModelParser()
				.removed(distributionListModel);
	}

	@Override
	public synchronized boolean removed(GroupModel groupModel) {
		return new GroupConversationModelParser()
				.removed(groupModel);
	}

	@Override
	public synchronized boolean removed(ContactModel contactModel) {
		return new ContactConversationModelParser()
				.removed(contactModel);
	}

	@Override
	public synchronized boolean reset() {
		synchronized (this.conversationCache) {
			this.conversationCache.clear();
		}
		return true;
	}

	@Override
	public boolean hasConversations() {
		synchronized (this.conversationCache) {
			if(this.conversationCache.size() > 0) {
				return true;
			}
		}

		long count = this.databaseServiceNew.getDistributionListMessageModelFactory().count();
		if(count > 0 ){
			return true;
		}

		count = this.databaseServiceNew.getMessageModelFactory().count();
		if(count > 0 ){
			return true;
		}

		count = this.databaseServiceNew.getGroupMessageModelFactory().count();
		return count > 0;
	}

	private void fireOnModifiedConversation(final ConversationModel c) {
		this.fireOnModifiedConversation(c, null);
	}
	private void fireOnModifiedConversation(final ConversationModel c, final Integer oldPosition) {
		ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
			@Override
			public void handle(ConversationListener listener) {
				listener.onModified(c, oldPosition);
			}
		});
	}

	private ConversationModelParser createParser(AbstractMessageModel m) {
		if(m instanceof GroupMessageModel) {
			return new GroupConversationModelParser();
		}
		else if(m instanceof DistributionListMessageModel) {
			return new DistributionListConversationModelParser();
		}
		else if(m instanceof MessageModel) {
			return new ContactConversationModelParser();
		}
		return null;
	}

	private abstract class ConversationModelParser<I, M extends AbstractMessageModel, P> {
		public abstract boolean belongsTo(ConversationModel conversationModel, I model);
		public abstract ConversationModel parseResult(ConversationResult result, ConversationModel conversationModel, boolean addToCache);
		public abstract List<ConversationResult> select(I model);
		public abstract List<ConversationResult> selectAll(boolean archived);
		protected abstract I getIndex(M messageModel);
		protected abstract I getIndex(P parentObject);

		public final ConversationModel getCached(final I index) {
			if(index == null) {
				return null;
			}
			synchronized (conversationCache) {
				return Functional.select(conversationCache, new IPredicateNonNull<ConversationModel>() {
					@Override
					public boolean apply(@NonNull ConversationModel conversationModel) {
						return belongsTo(conversationModel, index);
					}
				});
			}
		}

		public final void processAll() {
			List<ConversationResult> res = this.selectAll(false);
			for(ConversationResult r: res) {
				this.parseResult(r, null, true);
			}
		}

		public final List<ConversationModel> processArchived(List<ConversationModel> conversationModels) {
			List<ConversationResult> res = this.selectAll(true);
			for(ConversationResult r: res) {
				conversationModels.add(this.parseResult(r, null, false));
			}
			return conversationModels;
		}

		public final ConversationModel getSelected(final I index) {
			List<ConversationResult> results = this.select(index);
			if(results != null && results.size() > 0) {
				return this.parseResult(results.get(0), null, true);
			}
			return null;
		}

		public final ConversationModel refresh(P parentObject) {
			I index = this.getIndex(parentObject);
			ConversationModel model =  this.getCached(index);

			boolean newConversationModel = false;
			if(model == null) {
				newConversationModel = true;
				model = this.getSelected(index);
				//resort
				sort();
			} else {
				// refresh name if it's a distribution list
				if (model.isDistributionListConversation() && parentObject instanceof DistributionListModel) {
					model.getDistributionList().setName(((DistributionListModel) parentObject).getName());
				}
			}

			if(model == null) {
				return null;
			}
			final ConversationModel finalModel = model;
			if(newConversationModel) {
				logger.debug("refresh modified parent NEW");
				ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
					@Override
					public void handle(ConversationListener listener) {
						listener.onNew(finalModel);
					}
				});
			}
			else {
				logger.debug("refresh modified parent MODIFIED");
				ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
					@Override
					public void handle(ConversationListener listener) {
						listener.onModified(finalModel, null);
					}
				});
			}

			return model;
		}

		public final ConversationModel refresh(@Nullable M modifiedMessageModel) {
			if (modifiedMessageModel == null) {
				return null;
			}

			// Look up conversation in cache
			I index = this.getIndex(modifiedMessageModel);
			ConversationModel model = this.getCached(index);

			// On cache miss, get the conversation from the DB
			boolean newConversationModel = false;
			if(model == null) {
				newConversationModel = true;
				model = this.getSelected(index);
			}

			// If conversation was not found, give up
			if(model == null) {
				return null;
			}

			if((model.getLatestMessage() == null
					|| model.getLatestMessage().getId() < modifiedMessageModel.getId())) {
				//set this message as latest message
				model.setLatestMessage(modifiedMessageModel);
				//increase message count
				model.setMessageCount(model.getMessageCount()+1);
			}

			if(model.getReceiver() != null && MessageUtil.isUnread(model.getLatestMessage())) {
				//update unread count
				model.setUnreadCount(model.getReceiver().getUnreadMessagesCount());
			}
			else {
				if (model.getLatestMessage() == null) {
					// If there are no messages, mark the conversation as read
					model.setUnreadCount(0);
				} else if (model.getLatestMessage().getId() == modifiedMessageModel.getId() && modifiedMessageModel.isRead()) {
					// If the current message is the latest message in the conversation
					// and if it's read, mark the entire conversation as read.
					model.setUnreadCount(0);
				}
			}

			final ConversationModel finalModel = model;

			final int oldPosition = model.getPosition();
			sort();

			if(newConversationModel) {
				logger.debug("refresh modified message NEW");
				ListenerManager.conversationListeners.handle(listener -> listener.onNew(finalModel));
			}
			else {
				logger.debug("refresh modified message MODIFIED");
				ListenerManager.conversationListeners.handle(listener -> {
					listener.onModified(finalModel, oldPosition != finalModel.getPosition() ? oldPosition : null);
				});
			}

			return model;
		}

		public final ConversationModel messageDeleted(M messageModel) {
			final ConversationModel model = this.getCached(this.getIndex(messageModel));
			//if the newest message is deleted, reload
			if (model != null && model.getLatestMessage() != null && messageModel != null) {
				if(model.getLatestMessage().getId() == messageModel.getId()) {
					updateLatestConversationMessageAfterDelete(model);

					final int oldPosition = model.getPosition();
					sort();

					ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
						@Override
						public void handle(ConversationListener listener) {
							listener.onModified(model, oldPosition != model.getPosition() ? oldPosition : null);
						}
					});
				}
			}

			return model;
		}

		public final boolean removed(P parentObject) {
			ConversationModel model = this.getCached(this.getIndex(parentObject));
			if(model != null) {
				//remove from cache
				clear(model, true);
			}

			return true;
		}

		protected List<ConversationResult> parse(String query) {
			return parse(query, null);
		}

		protected List<ConversationResult> parse(String query, String[] args) {
			List<ConversationResult> r = new ArrayList<>();
			Cursor c = databaseServiceNew.getReadableDatabase().rawQuery(query, args);
			if(c != null) {
				try {
					while(c.moveToNext()) {
						r.add(new ConversationResult(c.getInt(0), c.getLong(1), c.getString(2)));
					}
				}
				finally {
					c.close();
				}
			}
			return r;
		}
	}

	private class ContactConversationModelParser extends ConversationModelParser<String, MessageModel, ContactModel> {
		@Override
		public boolean belongsTo(ConversationModel conversationModel, String identity) {
			return conversationModel.getContact() != null &&
					conversationModel.getContact().getIdentity().equals(identity);
		}

		@Override
		public List<ConversationResult> select(String identity) {
			return this.parse("SELECT MAX(id), COUNT(*), identity as id FROM message m WHERE " +
					"m.identity = ? " +
					"AND m.isStatusMessage = 0 " +
					"AND m.isSaved = 1 " +
					"GROUP BY identity", new String[]{
					identity
			});
		}

		@Override
		public List<ConversationResult> selectAll(boolean archived) {
			return this.parse("SELECT MAX(m.id), COUNT(*), m.identity as id FROM message m " +
					"INNER JOIN contacts c ON c.identity = m.identity " +
					"WHERE m.isSaved = 1 " +
					"AND c.isArchived = " + (archived ? "1 " : "0 ") +
					"GROUP BY m.identity");
		}

		@Override
		public ConversationModel parseResult(ConversationResult result, ConversationModel conversationModel, boolean addToCache) {
			final String identity = result.refId;

			//no cached contacts!?
			final ContactModel contactModel  = contactService.getByIdentity(identity);

			if (contactModel != null) {
				final ContactMessageReceiver receiver = contactService.createReceiver(contactModel);
				if(conversationModel == null) {
					conversationModel = new ConversationModel(receiver);
					if (addToCache && !contactModel.isArchived()) {
						synchronized (conversationCache) {
							conversationCache.add(conversationModel);
						}
					}
				}

				if(result.count > 0) {
					MessageModel latestMessage = messageService.getContactMessageModel(result.messageId, true);
					conversationModel.setLatestMessage(latestMessage);
					if(MessageUtil.isUnread(latestMessage)) {
						//update unread message count only if the "newest" message is unread (ANDR-398)
						conversationModel.setUnreadCount(receiver.getUnreadMessagesCount());
					}
					conversationModel.setMessageCount(result.count);
				}
				else {
					conversationModel.setUnreadCount(0);
					conversationModel.setMessageCount(0);
				}

				return conversationModel;
			}
			return null;
		}

		@Override
		protected String getIndex(MessageModel messageModel) {
			return messageModel != null ? messageModel.getIdentity() : null;
		}

		@Override
		protected String getIndex(ContactModel contactModel) {
			return contactModel != null ? contactModel.getIdentity() : null;
		}
	}

	private class GroupConversationModelParser extends ConversationModelParser<Integer, GroupMessageModel, GroupModel> {
		@Override
		public boolean belongsTo(ConversationModel conversationModel, Integer groupId) {
			return conversationModel.getGroup() != null &&
					conversationModel.getGroup().getId() == groupId;
		}

		@Override
		public ConversationModel parseResult(ConversationResult result, ConversationModel conversationModel, boolean addToCache) {
			final GroupModel groupModel = groupService.getById(Integer.valueOf(result.refId));
			GroupMessageReceiver receiver = groupService.createReceiver(groupModel);
			if (groupModel != null) {

				if(conversationModel == null) {
					conversationModel = new ConversationModel(receiver);
					if (addToCache && !groupModel.isArchived()) {
						synchronized (conversationCache) {
							conversationCache.add(conversationModel);
						}
					}
				}

				if(result.count > 0) {

					GroupMessageModel latestMessage = messageService.getGroupMessageModel(result.messageId, true);
					conversationModel.setLatestMessage(latestMessage);

					if(MessageUtil.isUnread(latestMessage)) {
						//update unread message count only if the "newest" message is unread (ANDR-398)
						conversationModel.setUnreadCount(receiver.getUnreadMessagesCount());
					}
					conversationModel.setMessageCount(result.count);
				}
				else {
					conversationModel.setUnreadCount(0);
				}

				conversationModel.setMessageCount(result.count);

				return conversationModel;
			}
			return null;
		}

		@Override
		public List<ConversationResult> select(Integer groupId) {
			return this.parse("SELECT MAX(gm.id), COUNT(gm.id), g.id FROM m_group g " +
					"LEFT OUTER JOIN m_group_message gm " +
					"ON gm.groupId = g.id " +
					"AND gm.isStatusMessage = 0 " +
					"AND gm.isSaved = 1 " +
					"WHERE g.id = ? " +
					"GROUP BY g.id", new String[]{
					String.valueOf(groupId)
			});
		}

		@Override
		public List<ConversationResult> selectAll(boolean archived) {
			return this.parse("SELECT MAX(gm.id), COUNT(gm.id), g.id FROM m_group g " +
					"LEFT OUTER JOIN m_group_message gm " +
					"ON gm.groupId = g.id " +
					"AND gm.isStatusMessage = 0 " +
					"AND gm.isSaved = 1 " +
					"WHERE g.deleted != 1 " +
					"AND g.isArchived = " + (archived ? "1 " : "0 ") +
					"GROUP BY g.id");
		}

		@Override
		protected Integer getIndex(GroupMessageModel messageModel) {
			return messageModel != null ? messageModel.getGroupId() : null;
		}

		@Override
		protected Integer getIndex(GroupModel groupModel) {
			return groupModel != null ? groupModel.getId() : null;
		}
	}


	private class DistributionListConversationModelParser extends ConversationModelParser<Integer, DistributionListMessageModel, DistributionListModel> {
		@Override
		public boolean belongsTo(ConversationModel conversationModel, Integer distributionListId) {
			return conversationModel.getDistributionList() != null &&
					conversationModel.getDistributionList().getId() == distributionListId;
		}

		@Override
		public ConversationModel parseResult(ConversationResult result, ConversationModel conversationModel, boolean addToCache) {
			final DistributionListModel distributionListModel = distributionListService.getById(Integer.valueOf(result.refId));
			DistributionListMessageReceiver receiver = distributionListService.createReceiver(distributionListModel);
			if (distributionListModel != null) {

				if(conversationModel == null) {
					conversationModel = new ConversationModel(receiver);
					if (addToCache && !distributionListModel.isArchived()) {
						synchronized (conversationCache) {
							conversationCache.add(conversationModel);
						}
					}
				}

				if(result.count > 0) {
					conversationModel.setLatestMessage(messageService.getDistributionListMessageModel(result.messageId, true));
				}

				conversationModel.setUnreadCount(0);
				conversationModel.setMessageCount(result.count);

				return conversationModel;
			}
			return null;
		}

		@Override
		public List<ConversationResult> select(Integer distributionListId) {
			return this.parse("SELECT MAX(dm.id), COUNT(dm.id), d.id FROM distribution_list d " +
					"LEFT OUTER JOIN distribution_list_message dm " +
					"ON dm.distributionListId = d.id " +
					"AND dm.isStatusMessage = 0 " +
					"AND dm.isSaved = 1 " +
					"WHERE d.id = ? " +
					"GROUP BY d.id", new String[]{
					String.valueOf(distributionListId)
			});
		}

		@Override
		public List<ConversationResult> selectAll(boolean archived) {
			return this.parse("SELECT MAX(dm.id), COUNT(dm.id), d.id FROM distribution_list d " +
					"LEFT OUTER JOIN distribution_list_message dm " +
					"ON dm.distributionListId = d.id " +
					"AND dm.isStatusMessage = 0 " +
					"AND dm.isSaved = 1 " +
					"WHERE d.isArchived = " + (archived ? "1 " : "0 ") +
					"GROUP BY d.id");
		}

		@Override
		protected Integer getIndex(DistributionListMessageModel messageModel) {
			return messageModel != null ? messageModel.getDistributionListId() : null;
		}

		@Override
		protected Integer getIndex(DistributionListModel distributionListModel) {
			return distributionListModel != null ? distributionListModel.getId() : null;
		}
	}


	private void updateLatestConversationMessageAfterDelete(ConversationModel conversationModel) {
		AbstractMessageModel newestMessage;

		//dirty diana hack
		newestMessage = Functional.select(this.messageService.getMessagesForReceiver(conversationModel.getReceiver(), new MessageService.MessageFilter() {
			@Override
			public long getPageSize() {
				return 1;
			}

			@Override
			public Integer getPageReferenceId() {
				return null;
			}

			@Override
			public boolean withStatusMessages() {
				return false;
			}

			@Override
			public boolean withUnsaved() {
				return false;
			}

			@Override
			public boolean onlyUnread() { return false; }

			@Override
			public boolean onlyDownloaded() {
				return true;
			}

			@Override
			public MessageType[] types() {
				return null;
			}

			@Override
			public int[] contentTypes() {
				return null;
			}
		}), new IPredicateNonNull<AbstractMessageModel>() {
			@Override
			public boolean apply(@NonNull AbstractMessageModel type) {
				return true;
			}
		});

		conversationModel.setLatestMessage(newestMessage);

		if(newestMessage == null || (newestMessage.isOutbox() || newestMessage.isRead())) {
			conversationModel.setUnreadCount(0);
		}

		if (newestMessage == null) {
			if (conversationModel.isGroupConversation() || conversationModel.isDistributionListConversation()) {
				// do not remove groups and distribution list conversations from cache as they should still be accessible in message list
				conversationModel.setMessageCount(0);
			}
			else {
				if (conversationModel.getMessageCount() == 1) {
					// remove model from cache completely
					synchronized (this.conversationCache) {
						this.conversationCache.remove(conversationModel);
					}
				}
			}
		}
	}
}
