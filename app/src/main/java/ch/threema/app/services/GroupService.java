/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.processors.incomingcspmessage.groupcontrol.IncomingGroupSetupTask;
import ch.threema.app.utils.GroupFeatureSupport;
import ch.threema.base.ThreemaException;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.ThreemaFeature;
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
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
public interface GroupService extends AvatarService<GroupModel> {

    /**
     * Group state note yet determined
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
         * Include deleted groups.
         */
        boolean includeDeletedGroups();

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
     * Get the group with the local group id.
     *
     * @param groupId the group id
     * @return the group model of the requested group, or null if not available
     */
    @Nullable
    GroupModel getById(int groupId);

    /**
     * Get the group by api group id and creator identity from the given abstract group message.
     *
     * @param message the abstract group message
     * @return the group that belongs to the abstract group message, or null if no group was found
     */
    @Nullable
    GroupModel getByGroupMessage(@NonNull AbstractGroupMessage message);

    /**
     * Get the group by api group id and creator identity.
     *
     * @param apiGroupId      the api group id
     * @param creatorIdentity the creator identity
     * @return the group model that matches the group id and creator identity, null if there is no
     * group with the given group id and creator
     */
    @Nullable
    GroupModel getByApiGroupIdAndCreator(@NonNull GroupId apiGroupId, @NonNull String creatorIdentity);

    /**
     * Get all groups.
     *
     * @return a list of group models
     */
    @NonNull
    List<GroupModel> getAll();

    /**
     * Get all groups according to the given filter.
     *
     * @param filter the filter that is applied
     * @return a list of group models
     */
    @NonNull
    List<GroupModel> getAll(@Nullable GroupFilter filter);

    /**
     * Create a new group. This fires the listeners and sends the messages to the members.
     * <p>
     * Note that this method is not used for creating groups based on incoming group setup messages.
     * This is handled in {@link IncomingGroupSetupTask}.
     *
     * @param name                  the name of the group
     * @param groupMemberIdentities the group members
     * @param picture               the group picture
     * @return the group model of the created group
     * @throws Exception if creating the group failed
     */
    @NonNull
    GroupModel createGroupFromLocal(String name, Set<String> groupMemberIdentities, Bitmap picture) throws Exception;

    /**
     * Update group properties and members.
     * This method triggers protocol messages to all group members that are affected by the change.
     *
     * @param groupModel            Group that should be modified
     * @param name                  New name of group, {@code null} if unchanged.
     * @param groupDesc             New group description for the group, {@code null} if unchanged.
     * @param groupMemberIdentities Identities of all group members.
     * @param photo                 New group photo, {@code null} if unchanged.
     * @param removePhoto           Whether to remove the group photo.
     * @return Updated groupModel
     */
    @NonNull
    GroupModel updateGroup(
        @NonNull GroupModel groupModel,
        @Nullable String name,
        @Nullable String groupDesc,
        @Nullable String[] groupMemberIdentities,
        @Nullable Bitmap photo,
        boolean removePhoto
    ) throws Exception;

    /**
     * Add a member to a group. Note that the user's identity must not be added to the member list
     * and is therefore ignored by this method. If the contact does not exist, the identity won't be
     * added to the members list. Note that this does not fire any listeners nor sending any
     * messages.
     *
     * @return true if the identity is added or already in the group, false if no contact with this
     * identity exists or it is the user's identity
     */
    boolean addMemberToGroup(@NonNull GroupModel groupModel, @NonNull String identity);

    /**
     * Remove a member from a group. Note that this does not fire any listener nor sending any
     * messages.
     *
     * @param groupModel the group that is updated
     * @param identity   the identity of the member that is added
     * @return true if successful, false if the member could not be removed
     */
    boolean removeMemberFromGroup(@NonNull GroupModel groupModel, @NonNull String identity);

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
     * Remove the group. This includes deleting files, messages, pending messages, group invite
     * links, ballots, group avatar, and the settings specified for the group. Note that only left
     * groups can be removed.
     * <p>
     * The group model and the members are kept in the database. This is required to handle future
     * messages in this group correctly (Common Group Receive Steps).
     * <br>
     * This fires the group listener.
     *
     * @param groupModel the group that will be deleted
     * @return true if the group has been deleted, false otherwise
     */
    boolean remove(@NonNull GroupModel groupModel);

    /**
     * If the user is still member of this group, the group will be dissolved
     * through {@link #dissolveGroupFromLocal} (if own group)
     * or left through {@link #leaveGroupFromLocal} (if user is not creator).
     * Then, the group will be removed through {@link #remove}.
     */
    void leaveOrDissolveAndRemoveFromLocal(@NonNull GroupModel groupModel);

    /**
     * Delete all groups. Note that this deletes all groups without triggering the listeners. The
     * groups are deleted completely from the database - including the api id and members.
     */
    void removeAll();

    /**
     * Remove all members from the group and then leave the group also. This can only be done by the
     * group creator.
     *
     * @param groupModel the group model
     */
    void dissolveGroupFromLocal(@NonNull GroupModel groupModel);

    /**
     * Leave the group. This updates the database, triggers the listeners, and sends a group leave
     * message to the creator and the members.
     * <p>
     * Note: If this group was created by the user, then an error will be logged
     * and nothing happens.
     *
     * @param groupModel the group that will be left
     */
    void leaveGroupFromLocal(@NonNull GroupModel groupModel);

    /**
     * Get the group members including the group creator and the user (if the creator or user are
     * members).
     *
     * @param groupModel the group model
     * @return a list of the contact models
     */
    @NonNull
    Collection<ContactModel> getMembers(@NonNull GroupModel groupModel);

    /**
     * Return the identities of all members of this group including the creator (if the creator has
     * not left the group) and the user (if the user is part of the group). To check whether the
     * user is a member of the group, use {@link #isGroupMember(GroupModel)}.
     *
     * @param groupModel Group model of the group
     * @return String array of identities (i.e. Threema IDs)
     */
    @NonNull
    String[] getGroupIdentities(@NonNull GroupModel groupModel);

    /**
     * Return the member identities (including the creator if part of the group) of the group except
     * the user.
     */
    @NonNull
    Set<String> getMembersWithoutUser(@NonNull GroupModel groupModel);

    /**
     * Get a string where the group members' display names are concatenated and separated by a
     * comma. This includes the group creator (except in orphaned groups).
     *
     * @param groupModel the group model
     * @return a string of all members names, including the group creator
     */
    @NonNull
    String getMembersString(@Nullable GroupModel groupModel);

    /**
     * Create a receiver for the given group model.
     *
     * @param groupModel the group model
     * @return a group message receiver
     */
    @NonNull
    GroupMessageReceiver createReceiver(@NonNull GroupModel groupModel);

    /**
     * Check whether the user is the creator of the given group.
     *
     * @param groupModel the group model
     * @return {@code true} if the user is the creator, {@code false} otherwise.
     */
    boolean isGroupCreator(@Nullable GroupModel groupModel);

    /**
     * Check whether the user is a member of the given group. Note that the group creator is also a
     * member.
     *
     * @param groupModel the group model
     * @return {@code true} if the user is a group member, {@code false} otherwise
     */
    boolean isGroupMember(@NonNull GroupModel groupModel);

    /**
     * Check whether the given identity is part of the group. Note that the group creator is also a
     * member.
     *
     * @param groupModel the group model
     * @param identity   the identity that is checked
     * @return {@code true} if the identity belongs to a group member, {@code false} otherwise
     */
    boolean isGroupMember(@NonNull GroupModel groupModel, @NonNull String identity);

    /**
     * Check whether the group is orphaned or not. In an orphaned group, the group creator is not a
     * member. Additionally, the user must not be the creator, otherwise it is a dissolved group.
     * Legacy groups where no members are stored, are orphaned if the user is not the creator.
     * Otherwise it is a dissolved group. Note that for legacy groups there is no need to
     * distinguish between orphaned or dissolved, as we do not provide an option to clone the group
     * anyway because the former members are not known.
     *
     * @param groupModel the group model
     * @return true if the group is orphaned, false otherwise
     */
    boolean isOrphanedGroup(@NonNull GroupModel groupModel);

    /**
     * Count members in a group. This includes the group creator and the user.
     *
     * @param groupModel the group model
     * @return Number of members in this group including group creator and the current user
     */
    int countMembers(@NonNull GroupModel groupModel);

    /**
     * Whether the provided group is an implicit note group (i.e. data is kept local). A group is
     * only a notes group, if the user is the creator of the group and there are no other members.
     *
     * @param groupModel the model of the group
     * @return true if the group is a note group, false otherwise
     */
    boolean isNotesGroup(@NonNull GroupModel groupModel);

    /**
     * Get the group state of a group.
     *
     * @param groupModel the group model
     * @return the group state of the group, UNDEFINED when the group model is null
     */
    @GroupState
    int getGroupState(@Nullable GroupModel groupModel);

    /**
     * Get the number of other members in the group (including the group creator). Note that in
     * orphaned groups, this may return zero even if the user is not the creator. To check for notes
     * groups use {@link #isNotesGroup(GroupModel)}.
     *
     * @param model the group model
     * @return the number of other members
     */
    int countMembersWithoutUser(@NonNull GroupModel model);

    /**
     * Get a map from the group member identity to its id color index.
     *
     * @param model the group model
     * @return a map with the ID color indices of the members
     */
    @NonNull
    Map<String, Integer> getGroupMemberIDColorIndices(@NonNull GroupModel model);

    /**
     * Send a group sync request for the group id to the creator.
     *
     * @param groupCreator the group creator identity
     * @param groupId      the local group id
     */
    void scheduleSyncRequest(String groupCreator, GroupId groupId) throws ThreemaException;

    /**
     * Send a group sync to the members of the group. This includes a group setup message, followed
     * by a group name message and finally a set profile picture or delete profile picture message.
     *
     * @param groupModel the group model
     * @return true if the sync was successful, false otherwise
     */
    boolean scheduleSync(GroupModel groupModel);

    /**
     * Send a group sync to the members of the group. This includes a group setup message, followed
     * by a group name message and finally a set profile picture or delete profile picture message.
     *
     * @param groupModel         the group model
     * @param receiverIdentities to these identities the sync messages are sent
     * @return true if the sync messages have been sent to all given identities, false otherwise
     */
    boolean scheduleSync(GroupModel groupModel, String[] receiverIdentities);

    /**
     * Get all the groups where the given identity is a member (or creator) of.
     *
     * @param identity the identity
     * @return a list of groups
     */
    @NonNull
    List<GroupModel> getGroupsByIdentity(@Nullable String identity);

    /**
     * Get group status info for the provided group
     *
     * @param groupModel Group
     * @param allowEmpty Whether to allow access even if there are no other members in this group
     * @return GroupAccessModel
     */
    GroupAccessModel getAccess(@Nullable GroupModel groupModel, boolean allowEmpty);

    @Deprecated
    int getUniqueId(GroupModel groupModel);

    String getUniqueIdString(GroupModel groupModel);

    String getUniqueIdString(int groupId);

    void setIsArchived(GroupModel groupModel, boolean archived);

    /**
     * Set the `lastUpdate` field of the specified group to the current date.
     * <p>
     * Save the model and notify listeners.
     */
    void bumpLastUpdate(@NonNull GroupModel groupModel);

    /**
     * Save the given group model to the database.
     */
    void save(@NonNull GroupModel groupModel);

    /**
     * Check whether the group contains the maximum supported amount of members.
     *
     * @param groupModel the group model
     * @return true if the group is full, false otherwise
     */
    boolean isFull(GroupModel groupModel);

    /**
     * Get the intent to open the group details.
     *
     * @param groupModel the group model
     * @param activity   the current activity
     * @return the intent to open the group details
     */
    Intent getGroupDetailIntent(@NonNull GroupModel groupModel, @NonNull Activity activity);

    void removeGroupMessageState(@NonNull GroupMessageModel messageModel, @NonNull String identityToRemove);

    /**
     * Check to which extent a feature is supported by the members of a group
     *
     * @param groupModel  group
     * @param featureMask feature mask
     * @return the feature support indicating whether all, not all or none of the group members support the feature
     */
    GroupFeatureSupport getFeatureSupport(@NonNull GroupModel groupModel, @ThreemaFeature.Feature long featureMask);
}
