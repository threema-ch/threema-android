package ch.threema.app.services;

import androidx.annotation.NonNull;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.base.SessionScoped;
import ch.threema.domain.models.ReceiverIdentifier;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTag;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.group.GroupModelOld;

@SessionScoped
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
     * Return all (non-archived) conversation models.
     *
     * @param forceReloadFromDatabase force a reload from database
     */
    @NonNull
    List<ConversationModel> getAll(boolean forceReloadFromDatabase);

    /**
     * Return a filtered list of all (non-archived) conversation models.
     *
     * @param forceReloadFromDatabase force a reload from database
     * @param filter                  an optional conversation filter
     */
    @NonNull
    List<ConversationModel> getAll(boolean forceReloadFromDatabase, @Nullable Filter filter);

    @Nullable
    ConversationModel get(@NonNull ReceiverIdentifier receiverIdentifier);

    /**
     * Return a list of all archived conversation models.
     */
    @NonNull
    List<ConversationModel> getArchived();

    /**
     * Return a list of all conversation models that have been archived and match the
     * search query (case-insensitive match).
     */
    @NonNull
    List<ConversationModel> getArchived(@Nullable String searchQuery);

    /**
     * Return the count of archived conversations that match the search query (case-insensitive match).
     */
    long countArchived(@Nullable String searchQuery, boolean excludePrivateConversations);

    /**
     * Marks the conversation that the message belongs to as fully read,
     * i.e., its unread count is reset and the 'unread' tag is removed if it exists.
     */
    void markConversationAsRead(@NonNull AbstractMessageModel messageModel);

    /**
     * Marks the conversation as fully read,
     * i.e., its unread count is reset and the 'unread' tag is removed if it exists.
     */
    void markConversationAsRead(@NonNull MessageReceiver<?> messageReceiver);

    /**
     * update the conversation cache entry for the given contact model
     *
     * @param identity the identity of the contact model that is loaded again from the database
     */
    void updateContactConversation(@NonNull String identity);

    /**
     * refresh a conversation model with a modified message model
     */
    ConversationModel refresh(AbstractMessageModel modifiedMessageModel);

    /**
     * refresh conversation
     */
    ConversationModel refresh(ch.threema.data.models.ContactModel contactModel);

    /**
     * refresh conversation
     */
    ConversationModel refresh(GroupModelOld groupModel);

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
    void archive(@NonNull ConversationModel conversationModel, @NonNull TriggerSource triggerSource);

    /**
     * clear archived flag in archived conversations
     */
    void unarchive(List<ConversationModel> conversations, @NonNull TriggerSource triggerSource);

    /**
     * Toggle the {@code ConversationTag.PINNED} tag for the given {@code ConversationModel} and notify the conversation listeners with the updated
     * model.
     */
    void togglePinned(@NonNull ConversationModel conversationModel, @NonNull TriggerSource triggerSource);

    /**
     * Tag the given {@code ConversationModel} with a given {@code ConversationTag} and notify the conversation listeners with the updated model.
     * <br><br>
     * The listeners will only be notified if the tag did not exist before and was effectively added by this call.
     */
    void tag(@NonNull ConversationModel conversationModel, @NonNull ConversationTag conversationTag, @NonNull TriggerSource triggerSource);

    /**
     * Untag the given {@code ConversationModel} from a given {@code ConversationTag} and notify the conversation listeners with the updated model.
     * <br><br>
     * The listeners will only be notified if the tag existed and was effectively removed.
     */
    void untag(@NonNull ConversationModel conversationModel, @NonNull ConversationTag conversationTag, @NonNull TriggerSource triggerSource);

    /**
     * clear archived flag in archived conversations
     */
    @WorkerThread
    void unarchiveByReceiverIdentifiers(@NonNull List<ReceiverIdentifier> receiverIdentifiers, @NonNull TriggerSource triggerSource);

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
    int empty(@NonNull DistributionListModel distributionListModel);

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
    void removeFromCache(@NonNull GroupModelOld groupModel);

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
