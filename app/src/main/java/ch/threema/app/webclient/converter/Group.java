/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

package ch.threema.app.webclient.converter;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

@AnyThread
public class Group extends Converter {
    final static String NAME = "name";
    final static String ADMINISTRATOR = "administrator";
    final static String MEMBERS = "members";
    final static String CREATED_AT = "createdAt";
    final static String CAN_CHANGE_NAME = "canChangeName";
    final static String CAN_CHANGE_MEMBERS = "canChangeMembers";
    final static String CAN_LEAVE = "canLeave";
    final static String CAN_CHANGE_AVATAR = "canChangeAvatar";
    final static String CAN_SYNC = "canSync";

    /**
     * Converts multiple group models to MsgpackObjectBuilder instances.
     */
    static List<MsgpackBuilder> convert(List<GroupModel> groups) throws ConversionException {
        List<MsgpackBuilder> list = new ArrayList<>();
        for (GroupModel group : groups) {
            list.add(convert(group));
        }
        return list;
    }

    /**
     * Converts a group model to a MsgpackObjectBuilder instance.
     */
    public static MsgpackObjectBuilder convert(GroupModel group) throws ConversionException {
        MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            final boolean isDisabled = !getGroupService().isGroupMember(group);
            final boolean isSecretChat = getHiddenChatListService().has(getGroupService().getUniqueIdString(group));
            final boolean isVisible = !isSecretChat || !getPreferenceService().isPrivateChatsHidden();

            builder.put(Receiver.ID, String.valueOf(group.getId()));
            builder.put(Receiver.DISPLAY_NAME, getDisplayName(group));
            builder.put(NAME, group.getName());
            builder.put(Receiver.COLOR, getColor(group));
            if (isDisabled) {
                builder.put(Receiver.DISABLED, true);
            }
            builder.put(CREATED_AT, group.getCreatedAt() != null ? group.getCreatedAt().getTime() / 1000 : 0);
            if (isSecretChat) {
                builder.put(Receiver.LOCKED, true);
            }
            if (!isVisible) {
                builder.put(Receiver.VISIBLE, false);
            }

            final MsgpackArrayBuilder memberBuilder = new MsgpackArrayBuilder();

            for (ContactModel contactModel : getGroupService().getMembers(group)) {
                memberBuilder.put(contactModel.getIdentity());
            }
            builder.put(MEMBERS, memberBuilder);
            builder.put(ADMINISTRATOR, group.getCreatorIdentity());

            //TODO
            //create util class or use access object
            boolean admin = getGroupService().isGroupCreator(group);
            boolean left = !getGroupService().isGroupMember(group);
            boolean enabled = !(group.isDeleted() || isDisabled);
            //define access
            builder.put(Receiver.ACCESS, (new MsgpackObjectBuilder())
                .put(Receiver.CAN_DELETE, admin || left)
                .put(CAN_CHANGE_AVATAR, admin && enabled)
                .put(CAN_CHANGE_NAME, admin && enabled)
                .put(CAN_CHANGE_MEMBERS, admin && enabled)
                .put(CAN_SYNC, admin && enabled)
                .put(CAN_LEAVE, enabled));
        } catch (NullPointerException e) {
            throw new ConversionException(e);
        }
        return builder;
    }

    private static String getDisplayName(GroupModel group) throws ConversionException {
        return NameUtil.getDisplayName(group, getGroupService());
    }

    private static String getColor(GroupModel group) throws ConversionException {
        return String.format("#%06X", (0xFFFFFF & group.getColorLight()));
    }

    /**
     * Return the filter used to query groups from the group service.
     */
    @NonNull
    public static GroupService.GroupFilter getGroupFilter() {
        return new GroupService.GroupFilter() {
            @Override
            public boolean sortByDate() {
                return false;
            }

            @Override
            public boolean sortAscending() {
                return false;
            }

            @Override
            public boolean sortByName() {
                return false;
            }

            @Override
            public boolean includeDeletedGroups() {
                return false;
            }

            @Override
            public boolean includeLeftGroups() {
                return true;
            }
        };
    }
}
