package ch.threema.app.webclient.converter;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import ch.threema.app.services.GroupService;
import ch.threema.app.utils.GroupUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.group.GroupModelOld;

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
    static List<MsgpackBuilder> convert(List<GroupModelOld> groups) throws ConversionException {
        List<MsgpackBuilder> list = new ArrayList<>();
        for (GroupModelOld group : groups) {
            list.add(convert(group));
        }
        return list;
    }

    /**
     * Converts a group model to a MsgpackObjectBuilder instance.
     */
    public static MsgpackObjectBuilder convert(GroupModelOld group) throws ConversionException {
        MsgpackObjectBuilder builder = new MsgpackObjectBuilder();
        try {
            final boolean isDisabled = !getGroupService().isGroupMember(group);
            final boolean isPrivateChat = getConversationCategoryService().isPrivateChat(GroupUtil.getUniqueIdString(group));
            final boolean isVisible = !isPrivateChat || !getPreferenceService().arePrivateChatsHidden();

            builder.put(Receiver.ID, String.valueOf(group.getId()));
            builder.put(Receiver.DISPLAY_NAME, getDisplayName(group));
            builder.put(NAME, group.getName());
            builder.put(Receiver.COLOR, getColor(group));
            if (isDisabled) {
                builder.put(Receiver.DISABLED, true);
            }
            builder.put(CREATED_AT, group.getCreatedAt() != null ? group.getCreatedAt().getTime() / 1000 : 0);
            if (isPrivateChat) {
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
            boolean enabled = !isDisabled;
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

    private static String getDisplayName(GroupModelOld group) throws ConversionException {
        return NameUtil.getGroupDisplayName(group, getGroupService(), getPreferenceService().getContactNameFormat());
    }

    private static String getColor(@NonNull GroupModelOld group) {
        int idColor = group.getIdColor().getColorLight();
        return String.format("#%06X", (0xFFFFFF & idColor));
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
            public boolean includeLeftGroups() {
                return true;
            }
        };
    }
}
