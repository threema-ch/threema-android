package ch.threema.app.services;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.bumptech.glide.RequestManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.AnyThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.glide.AvatarOptions;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.utils.GroupFeatureSupport;
import ch.threema.base.SessionScoped;
import ch.threema.data.datatypes.IdColor;
import ch.threema.base.ThreemaException;
import ch.threema.data.models.GroupIdentity;
import ch.threema.data.models.GroupModel;
import ch.threema.data.models.GroupModelData;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupMessageModel;
import ch.threema.storage.models.group.GroupModelOld;
import ch.threema.storage.models.access.GroupAccessModel;

/**
 * The group service provides basic group operations. These include accessing the group model,
 * checking for group members, and also creating or updating groups. Note that the group service
 * does <b>not</b> handle incoming group control messages, but is partially used for them.
 * <br>
 * Note that methods ending with {@code fromLocal} denote methods must only be called when the
 * change has been triggered locally (including the web client) - in contrast to {@code fromRemote}
 * and {@code fromSync}. {@code fromLocal} methods usually also send the corresponding group control
 * messages and fire the group listeners.
 */
@SessionScoped
public interface GroupService extends AvatarService<GroupModelOld> {

    /**
     * Group state not yet determined
     */
    int UNDEFINED = 0;
    /**
     * A local notes "group"
     */
    int NOTES = 1;
    /**
     * A group with other people in it
     */
    int PEOPLE = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({UNDEFINED, NOTES, PEOPLE})
    @interface GroupState {
    }

    interface GroupFilter {
        /**
         * Sort the group list by date. If {@link #sortByName()} also returns true, then it is still
         * sorted by date.
         */
        boolean sortByDate();

        /**
         * Sort the group list by name. If {@link #sortByDate()} also returns true, then it is
         * sorted by date.
         */
        boolean sortByName();

        /**
         * Sort the group list ascending. This is only applied, if {@link #sortByDate()} or
         * {@link #sortByName()} return true.
         */
        boolean sortAscending();

        /**
         * Include groups that the user is no longer a member of.
         */
        boolean includeLeftGroups();
    }

    /**
     * Reset the group model caches. This may be needed if group changes have been made outside of
     * the group service.
     *
     * @param groupModelId the local id of the group model
     */
    void resetCache(int groupModelId);

    /**
     * Remove the group model with the given identity from the cache. This may be needed if group
     * changes have been made outside of the group service, e.g. by the new group model.
     */
    void removeFromCache(@NonNull GroupIdentity groupIdentity);

    /**
     * Get the group with the local group id.
     *
     * @param groupId the group id
     * @return the group model of the requested group, or null if not available
     */
    @Nullable
    GroupModelOld getById(int groupId);

    /**
     * Get the group with the local group id.
     *
     * @param groupId the group id
     * @return the group model of the requested group, or null if not available
     */
    @Nullable
    default GroupModelOld getById(long groupId) {
        return getById((int) groupId);
    }

    /**
     * Tries to read the requested group models from cache first, and adds missing models from database (if existing). If one or all group-ids
     * could not be found, there will be no error. In this case the result list will just miss these models.
     * <br>
     * <br>
     * <b>Order:</b> The result list does not guarantee to be in the same order as the given {@code groupIds} list
     */
    @NonNull
    List<GroupModelOld> getByIds(@NonNull List<Integer> groupIds);

    /**
     * Get the group by api group id and creator identity from the given abstract group message.
     *
     * @param message the abstract group message
     * @return the group that belongs to the abstract group message, or null if no group was found
     */
    @Nullable
    GroupModelOld getByGroupMessage(@NonNull AbstractGroupMessage message);

    /**
     * Get the group by api group id and creator identity.
     *
     * @param apiGroupId      the api group id
     * @param creatorIdentity the creator identity
     * @return the group model that matches the group id and creator identity, null if there is no
     * group with the given group id and creator
     */
    @Nullable
    GroupModelOld getByApiGroupIdAndCreator(@NonNull GroupId apiGroupId, @NonNull String creatorIdentity);

    /**
     * Get the group by its group identity consisting of the creator identity and the group id.
     *
     * @param groupIdentity the group identity that identifies the group
     * @return the group model that matches the group id and creator identity, null if there is no
     * group with the given group identity
     */
    @Nullable
    GroupModelOld getByGroupIdentity(@NonNull GroupIdentity groupIdentity);

    /**
     * Get all groups.
     *
     * @return a list of group models
     */
    @NonNull
    List<GroupModelOld> getAll();

    /**
     * Get all groups according to the given filter.
     *
     * @param filter the filter that is applied
     * @return a list of group models
     */
    @NonNull
    List<GroupModelOld> getAll(@Nullable GroupFilter filter);

    /**
     * Run the rejected messages refresh steps: The receivers that requested a re-send of a rejected
     * message of this group will be updated with the current group member list. The re-send mark
     * will be deleted if there is no receiver left. This method should always be called, after the
     * group members changed.
     *
     * @param groupModel the group model that is refreshed
     */
    void runRejectedMessagesRefreshSteps(@NonNull GroupModel groupModel);

    /**
     * Remove different properties from the given group. This includes ballots, files from the
     * message models including thumbnails, wallpapers, ringtones, chat settings, share targets,
     * shortcuts, tags, and the group avatar from the provided group. This does not remove data in
     * the database except polls. No listeners are triggered.
     * TODO(ANDR-3633): Do not remove polls here as they should be deleted at the same place where
     *  the rest of the database is cleared.
     */
    void removeGroupBelongings(
        @NonNull GroupModel groupModel,
        @NonNull TriggerSource triggerSource
    );

    /**
     * Delete all groups. Note that this deletes all groups without triggering the listeners. The
     * groups are deleted completely from the database - including the api id and members.
     */
    void removeAll();

    /**
     * Get the group members including the group creator and the user (if the creator or user are
     * members).
     *
     * @param groupModel the group model
     * @return a list of the contact models
     */
    @NonNull
    Collection<ContactModel> getMembers(@NonNull GroupModelOld groupModel);

    /**
     * Get the group members including the group creator and the user (if the creator or user are
     * members).
     *
     * @param groupModelData the group model data
     * @return a list of the contact models
     */
    @NonNull
    Collection<ContactModel> getMembers(@NonNull GroupModelData groupModelData);

    /**
     * Return the identities of all members of this group including the creator (if the creator has
     * not left the group) and the user (if the user is part of the group). To check whether the
     * user is a member of the group, use {@link #isGroupMember(GroupModelOld)}.
     *
     * @param groupModel Group model of the group
     * @return String array of identities (i.e. Threema IDs)
     */
    @NonNull
    String[] getGroupMemberIdentities(@NonNull GroupModelOld groupModel);

    /**
     * Return the member identities (including the creator if part of the group) of the group except
     * the user.
     */
    @NonNull
    Set<String> getMembersWithoutUser(@NonNull GroupModelOld groupModel);

    /**
     * Get a string where the group members' display names are concatenated and separated by a
     * comma. This includes the group creator.
     *
     * @param groupModel the group model
     * @return a string of all members names, including the group creator
     */
    @NonNull
    String getMembersString(@Nullable GroupModelOld groupModel);

    /**
     * Get a string where the group members' display names are concatenated and separated by a
     * comma. This includes the group creator and the user (if member).
     *
     * @param groupModel the group model
     * @return a string of all members names, including the group creator
     */
    @NonNull
    String getMembersString(@Nullable GroupModel groupModel);

    /**
     * Create a receiver for the given group identity
     *
     * @return a group message receiver
     */
    @Nullable
    GroupMessageReceiver createReceiver(@NonNull GroupIdentity groupIdentity);

    /**
     * Create a receiver for the given group model.
     *
     * @param groupModel the group model
     * @return a group message receiver
     */
    @NonNull
    GroupMessageReceiver createReceiver(@NonNull GroupModelOld groupModel);

    /**
     * Create a receiver for the given group model.
     *
     * @param groupModel the group model
     * @return a group message receiver or null if the old group model could not be found
     */
    @Nullable
    GroupMessageReceiver createReceiver(@NonNull GroupModel groupModel);

    /**
     * Check whether the user is the creator of the given group.
     *
     * @param groupModel the group model
     * @return {@code true} if the user is the creator, {@code false} otherwise.
     */
    boolean isGroupCreator(@Nullable GroupModelOld groupModel);

    /**
     * Check whether the user is a member of the given group. Note that the group creator is also a
     * member.
     *
     * @param groupModel the group model
     * @return {@code true} if the user is a group member, {@code false} otherwise
     */
    boolean isGroupMember(@NonNull GroupModelOld groupModel);

    /**
     * Check whether the given identity is part of the group. Note that the group creator is also a
     * member.
     *
     * @param groupModel the group model
     * @param identity   the identity that is checked
     * @return {@code true} if the identity belongs to a group member, {@code false} otherwise
     */
    boolean isGroupMember(@NonNull GroupModelOld groupModel, @NonNull String identity);

    /**
     * Count members in a group. This includes the group creator and the user.
     *
     * @param groupModel the group model
     * @return Number of members in this group including group creator and the current user
     */
    int countMembers(@NonNull GroupModelOld groupModel);

    /**
     * Whether the provided group is an implicit note group (i.e. data is kept local). A group is
     * only a notes group, if the user is the creator of the group and there are no other members.
     *
     * @param groupModel the model of the group
     * @return true if the group is a note group, false otherwise
     */
    boolean isNotesGroup(@NonNull GroupModelOld groupModel);

    /**
     * Get the number of other members in the group (including the group creator). To check for notes
     * groups use {@link #isNotesGroup(GroupModelOld)}.
     *
     * @param model the group model
     * @return the number of other members
     */
    int countMembersWithoutUser(@NonNull GroupModelOld model);

    /**
     * Get a map from the group member identity to its id color.
     *
     * @param model the group model
     * @return a map with the ID colors of the members
     */
    @NonNull
    Map<String, IdColor> getGroupMemberIDColors(@NonNull GroupModel model);

    /**
     * Send a group sync request for the group id to the creator.
     *
     * @param groupCreator the group creator identity
     * @param groupId      the local group id
     */
    void scheduleSyncRequest(String groupCreator, GroupId groupId) throws ThreemaException;

    /**
     * Get all the groups where the given identity is a member (or creator) of.
     *
     * @param identity the identity
     * @return a list of groups
     */
    @NonNull
    List<GroupModelOld> getGroupsByIdentity(@Nullable String identity);

    /**
     * Get group status info for the provided group
     *
     * @param groupModel Group
     * @param allowEmpty Whether to allow access even if there are no other members in this group
     */
    GroupAccessModel getAccess(@Nullable GroupModelOld groupModel, boolean allowEmpty);

    /**
     * Mark the group as archived or unarchived. This change is reflected and uses the new group
     * model. Listeners are triggered by the group model repository.
     * <p>
     * TODO(ANDR-3721): Use this method with care until the pinned state is moved to the same
     *  database column as the archived state. This method must only be called with isArchived=true
     *  when the conversation of this group is *not* pinned.
     *
     * @param groupCreatorIdentity the identity of the group creator
     * @param groupId              the api id of the group
     * @param isArchived           whether the group should be archived or not
     * @param triggerSource        the source that triggered this action
     */
    void setIsArchived(
        @NonNull String groupCreatorIdentity,
        @NonNull GroupId groupId,
        boolean isArchived,
        @NonNull TriggerSource triggerSource
    );

    /**
     * Set the `lastUpdate` field of the specified group to the current date.
     * <p>
     * Save the model and notify listeners.
     */
    void bumpLastUpdate(@NonNull GroupModelOld groupModel);

    /**
     * Check whether the group contains the maximum supported amount of members.
     *
     * @param groupModel the group model
     * @return true if the group is full, false otherwise
     */
    boolean isFull(GroupModelOld groupModel);

    /**
     * Get the intent to open the group details.
     *
     * @param activity   the current activity
     * @return the intent to open the group details
     */
    @NonNull
    Intent getGroupDetailIntent(long groupDatabaseId, @NonNull Activity activity);

    /**
     * Get the intent to open the group details.
     *
     * @param groupModel the group model
     * @param activity   the current activity
     * @return the intent to open the group details
     */
    @NonNull
    Intent getGroupDetailIntent(@NonNull GroupModelOld groupModel, @NonNull Activity activity);

    /**
     * Get the intent to open the group details.
     *
     * @param groupModel the group model
     * @param activity   the current activity
     * @return the intent to open the group details
     */
    @NonNull
    Intent getGroupDetailIntent(@NonNull GroupModel groupModel, @NonNull Activity activity);

    /**
     * Get the avatar with the given avatar options of the given model as bitmap.
     *
     * @param groupModel the group model of which the avatar is returned
     * @param options    the options for loading the avatar
     * @return the avatar of the given model
     */
    @Nullable
    @AnyThread
    Bitmap getAvatar(@Nullable GroupModel groupModel, @NonNull AvatarOptions options);

    /**
     * Get the color of the default group profile picture.
     *
     * @param groupModel the group model for which the group profile picture color is to be determined.
     *                   If null, the default color is returned
     * @return the group profile picture color of the given group model, or the default color if the subject is null
     */
    int getGroupProfilePictureColor(@Nullable GroupModel groupModel);

    /**
     * Load the avatar of the given model into the provided image view. The avatar bitmap is loaded
     * asynchronously and the default avatar is shown as a placeholder.
     *
     * @param groupModel the group model
     * @param imageView  the image view
     * @param options    the options for loading the image
     */
    @AnyThread
    void loadAvatarIntoImageView(
        @NonNull GroupModel groupModel,
        @NonNull ImageView imageView,
        @NonNull AvatarOptions options,
        @NonNull RequestManager requestManager
    );

    void removeGroupMessageState(@NonNull GroupMessageModel messageModel, @NonNull String identityToRemove);

    /**
     * Check to which extent a feature is supported by the members of a group
     *
     * @param groupModelData group model data
     * @param featureMask    feature mask
     * @return the feature support indicating whether all, not all or none of the group members support the feature
     */
    GroupFeatureSupport getFeatureSupport(@NonNull GroupModelData groupModelData, @ThreemaFeature.Feature long featureMask);

    /**
     * This will wipe every value of `notificationTriggerPolicyOverride` and trigger
     * contact syncs for mutated models.
     */
    void resetAllNotificationTriggerPolicyOverrideFromLocal();
}
