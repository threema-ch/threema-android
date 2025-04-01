/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.app.services.group;


import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.UserService;
import ch.threema.base.Result;
import ch.threema.base.utils.Base64;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteData;
import ch.threema.domain.protocol.csp.messages.group.GroupInviteToken;
import ch.threema.protobuf.url_payloads.GroupInvite;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.GroupInviteModel;
import java8.util.Optional;

import static androidx.core.content.ContextCompat.startActivity;


@WorkerThread
public class GroupInviteServiceImpl implements GroupInviteService {

    private final @NonNull UserService userService;
    private final @NonNull GroupService groupService;
    private final @NonNull GroupInviteModelFactory groupInviteModelFactory;

    public GroupInviteServiceImpl(
        @NonNull UserService userService,
        @NonNull GroupService groupService,
        @NonNull DatabaseServiceNew databaseService
    ) {
        this.userService = userService;
        this.groupService = groupService;
        this.groupInviteModelFactory = databaseService.getGroupInviteModelFactory();
    }

    /**
     * Creates and persists a new group invite with a unique random token,
     *
     * @param groupModel GroupModel for the group for which the invite should be created
     * @param isDefault  flag if the groupInvite should serve as the default link
     * @return GroupInviteModel created
     * @throws GroupInviteToken.InvalidGroupInviteTokenException if the token has invalid length or format
     * @throws IOException                                       if an error occurred while persisting the new GroupInviteModel in the db
     */
    @Override
    public @NonNull GroupInviteModel createGroupInvite(@NonNull GroupModel groupModel, boolean isDefault) throws
        GroupInviteToken.InvalidGroupInviteTokenException,
        IOException, GroupInviteModel.MissingRequiredArgumentsException {


        String randomId = UUID.randomUUID().toString();
        GroupInviteToken groupInviteToken = new GroupInviteToken(
            Utils.hexStringToByteArray(
                randomId.substring(randomId.length() - (ProtocolDefines.GROUP_INVITE_TOKEN_LEN * 2))
            )
        );

        final Result<GroupInviteModel, Exception> insertResult = this.groupInviteModelFactory
            .insert(
                new GroupInviteModel.Builder()
                    .withId(-1)
                    .withGroupApiId(groupModel.getApiGroupId())
                    .withToken(groupInviteToken)
                    .withGroupName(groupModel.getName() == null ? groupService.getMembersString(groupModel) : groupModel.getName())
                    .withInviteName(isDefault ? ThreemaApplication.getAppContext().getResources().getString(R.string.default_link_name) : ThreemaApplication.getAppContext().getResources().getString(R.string.group_link_default_name))
                    .setIsInvalidated(false)
                    .setIsDefault(isDefault)
                    .build()
            );

        if (insertResult.isFailure()) {
            throw new IOException("Could not insert database record" + insertResult.getError());
        }

        return Objects.requireNonNull(insertResult.getValue());
    }

    /**
     * Creates and persists a new group invite with a unique random token that is flagged as default. If there was
     * a default link previously generated, the last added one is re-enabled meaning the invalid flag is inverted.
     *
     * @param groupModel GroupModel for the group for which the invite should be created/re-enabled
     * @return GroupInviteModel created
     * @throws GroupInviteToken.InvalidGroupInviteTokenException if the token has invalid length or format
     * @throws IOException                                       if an error occurred while persisting the new GroupInviteModel in the db
     */
    @Override
    public GroupInviteModel createOrEnableDefaultLink(GroupModel groupModel) throws IOException,
        GroupInviteToken.InvalidGroupInviteTokenException, GroupInviteModel.MissingRequiredArgumentsException {
        Optional<GroupInviteModel> groupInviteModelOptional = groupInviteModelFactory.getDefaultByGroupApiId(groupModel.getApiGroupId());

        // enable previously known invite again
        if (groupInviteModelOptional.isPresent()) {
            GroupInviteModel updatedValidGroupInviteModel = new GroupInviteModel
                .Builder(groupInviteModelOptional.get()).setIsInvalidated(false).build();
            if (groupInviteModelFactory.update(updatedValidGroupInviteModel)) {
                return updatedValidGroupInviteModel;
            }
        }

        // else create new default link
        return createGroupInvite(groupModel, true);
    }

    /**
     * Queries the default link for a group
     *
     * @param groupModel GroupModel for the group for which the default link should be queried
     * @return GroupInviteModel set as default
     */
    @Override
    @NonNull
    public Optional<GroupInviteModel> getDefaultGroupInvite(@NonNull GroupModel groupModel) {
        return groupInviteModelFactory.getDefaultByGroupApiId(groupModel.getApiGroupId());
    }

    @Override
    public int getCustomLinksCount(GroupId groupApiId) {
        return groupInviteModelFactory.getAllActiveCustomForGroup(groupApiId).size();
    }

    /**
     * Deletes the default link for a group if there is any valid one. Deleting in this context means
     * setting the invalid flag as we want to keep the token entries in the db to send a expired response to previously valid links.
     *
     * @param groupModel GroupModel for the group for which the  default link should be reset
     */
    @Override
    public void deleteDefaultLink(GroupModel groupModel) {
        // check if there is already a default link, create a new default link if not
        Optional<GroupInviteModel> optionalDefaultInvite = groupInviteModelFactory.getDefaultByGroupApiId(groupModel.getApiGroupId());
        if (optionalDefaultInvite.isPresent() && !optionalDefaultInvite.get().isInvalidated()) {
            groupInviteModelFactory.delete(optionalDefaultInvite.get());
        }
    }

    /**
     * Resets the default link for a group by deleting the last default link and creating a new one
     *
     * @param groupModel GroupModel for the group for which the  default link should be deleted
     * @return GroupInviteModel created
     */
    @Override
    public GroupInviteModel resetDefaultGroupInvite(@NonNull GroupModel groupModel) throws IOException, GroupInviteToken.InvalidGroupInviteTokenException, GroupInviteModel.MissingRequiredArgumentsException {
        // check if there is already a default link, create a new default link if not
        Optional<GroupInviteModel> optionalDefaultInvite = groupInviteModelFactory.getDefaultByGroupApiId(groupModel.getApiGroupId());

        if (optionalDefaultInvite.isEmpty() || optionalDefaultInvite.get().isInvalidated()) {
            return createGroupInvite(groupModel, true);
        }
        // delete and create a new one, we don't just update the token because we still want to
        // return a expired response on previous valid group links
        groupInviteModelFactory.delete(optionalDefaultInvite.get());
        return createGroupInvite(groupModel, true);
    }

    /**
     * base64 encodes a group invite and creates a link string with base url "https://threema.group/join#
     *
     * @param model GroupInviteModel to be encoded
     * @return Uri encoded group invite link
     */
    @Override
    public @NonNull Uri encodeGroupInviteLink(@NonNull GroupInviteModel model) {
        return new Uri.Builder()
            .scheme("https")
            .authority(BuildConfig.groupLinkActionUrl)
            .appendPath("join") // https://threema.group/join#
            .encodedFragment(
                Base64.encodeBytes(
                    (userService.getIdentity() + ":"
                        + model.getToken().toString() + ":"
                        + model.getOriginalGroupName() + ":"
                        + (model.getManualConfirmation() ? 1 : 0)
                    ).getBytes()
                )
            ).build();
    }

    /**
     * decodes a base64 group invite link string
     *
     * @param encodedGroupInvite String uri fragment to be decoded
     * @return GroupInviteData class holding the group invite attributes
     */
    @Override
    public @NonNull GroupInviteData decodeGroupInviteLink(@NonNull String encodedGroupInvite) throws IOException, IllegalStateException, GroupInviteToken.InvalidGroupInviteTokenException {
        final String decodedGroupInvite;
        decodedGroupInvite = new String(Base64.decode(encodedGroupInvite));
        String[] groupLinkInfos = decodedGroupInvite.split(":");
        if (groupLinkInfos.length != 4) {
            throw new IllegalStateException("Decoded group link has wrong number of attributes should be 4 (identity, token, group name, administration option) but found " + groupLinkInfos.length);
        }
        return new GroupInviteData(groupLinkInfos[0],
            GroupInviteToken.fromHexString(groupLinkInfos[1]),
            groupLinkInfos[2],
            Objects.requireNonNull(GroupInvite.ConfirmationMode.forNumber(Integer.parseInt(groupLinkInfos[3]))));
    }

    /**
     * shares a group invite through the general android share sheet options picker
     *
     * @param model GroupInviteModel to be shared
     */
    @Override
    public void shareGroupLink(@NonNull Context context, @NonNull GroupInviteModel model) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, String.format(context.getString(R.string.group_link_share_message), encodeGroupInviteLink(model)));
        startActivity(context, Intent.createChooser(shareIntent, context.getString(R.string.share_via)), null);
    }
}
