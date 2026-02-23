package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.app.services.GroupService;
import ch.threema.data.models.GroupIdentity;
import ch.threema.storage.models.GroupModel;

public interface GroupListener {
    @AnyThread
    default void onCreate(@NonNull GroupIdentity groupIdentity) {
    }

    @AnyThread
    default void onRename(@NonNull GroupIdentity groupIdentity) {
    }

    @AnyThread
    default void onUpdatePhoto(@NonNull GroupIdentity groupIdentity) {
    }

    @AnyThread
    default void onRemove(long groupDbId) {
    }

    @AnyThread
    default void onNewMember(@NonNull GroupIdentity groupIdentity, String identityNew) {
    }

    @AnyThread
    default void onMemberLeave(@NonNull GroupIdentity groupIdentity, @NonNull String identityLeft) {
    }

    @AnyThread
    default void onMemberKicked(@NonNull GroupIdentity groupIdentity, String identityKicked) {
    }

    /**
     * Group was updated.
     */
    @AnyThread
    default void onUpdate(@NonNull GroupIdentity groupIdentity) {
    }

    /**
     * User left his own group.
     */
    @AnyThread
    default void onLeave(@NonNull GroupIdentity groupIdentity) {
    }

    /**
     * Group State has possibly changed
     * Note that oldState may be equal to newState
     */
    @AnyThread
    default void onGroupStateChanged(@NonNull GroupIdentity groupIdentity, @GroupService.GroupState int oldState, @GroupService.GroupState int newState) {
    }
}
