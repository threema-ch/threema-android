package ch.threema.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import ch.threema.base.SessionScoped;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.ConversationTagModel;
import ch.threema.storage.models.ConversationTag;

@SessionScoped
public interface ConversationTagService {
    /**
     * Tag the {@link ConversationModel} with the given {@link ConversationTag}
     */
    void addTagAndNotify(@Nullable ConversationModel conversation, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource);

    /**
     * Untag the {@link ConversationModel} with the given {@link ConversationTag}
     */
    void removeTagAndNotify(@Nullable ConversationModel conversation, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource);

    /**
     * Remove the given tag of the conversation with the provided conversation uid.
     *
     * @return True if the tag was removed and false if it never existed before.
     */
    boolean removeTag(@NonNull String conversationUid, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource);

    /**
     * Tag the conversation with the given {@link ConversationTag}.
     *
     * @return True if the tag was newly created and false if the tag was already present.
     */
    boolean addTag(@NonNull String conversationUid, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource);

    /**
     * Toggle the {@link ConversationTag} of the {@link ConversationModel}
     *
     * @return {@code true} if the {@code conversation} is tagged after the toggle operation, {@code false} otherwise.
     */
    boolean toggle(@NonNull ConversationModel conversation, @NonNull ConversationTag tag, @NonNull TriggerSource triggerSource);

    /**
     * Return true, if the {@link ConversationModel} is tagged with {@link ConversationTag}
     */
    boolean isTaggedWith(@Nullable ConversationModel conversation, @NonNull ConversationTag tag);

    /**
     * Return true, if the {@link ConversationModel} with the given uid is tagged with
     * {@link ConversationTag}
     */
    boolean isTaggedWith(@NonNull String conversationUid, @NonNull ConversationTag tag);

    /**
     * Remove all tags linked with the given {@link ConversationModel}
     */
    void removeAll(@Nullable ConversationModel conversation, @NonNull TriggerSource triggerSource);

    /**
     * Remove all tags linked with the given conversation uid
     */
    void removeAll(@NonNull String conversationUid, @NonNull TriggerSource triggerSource);

    /**
     * Get all tags regardless of type
     */
    List<ConversationTagModel> getAll();

    /**
     * Get all conversation uids that are tagged with the provided type.
     */
    @NonNull
    List<String> getConversationUidsByTag(@NonNull ConversationTag tag);

    /**
     * Return the number of conversations with the provided tag
     *
     * @return number of conversations or 0 if there is none
     */
    long getCount(@NonNull ConversationTag tag);
}

