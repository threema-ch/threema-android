package ch.threema.app.utils;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.UserService;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.group.GroupModelOld;
import java.util.Arrays;
import java.util.stream.Collectors;

public class NameUtil {

    private NameUtil() {
        // Don't allow creating an instance of this class
    }

    /**
     * Deprecated: See {@code GetGroupDisplayNameUseCase}
     */
    @NonNull
    @Deprecated
    public static String getGroupDisplayName(
        @NonNull GroupModelData groupModelData,
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull UserService userService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        // Use the name if it is not empty
        if (groupModelData.name != null && !groupModelData.name.isEmpty()) {
            return groupModelData.name;
        }

        String myIdentity = userService.getIdentity();
        if (myIdentity == null) {
            return "";
        }

        // List members if the name is empty
        String memberList = groupModelData.otherMembers.stream().map(identity -> {
                ch.threema.data.models.ContactModel contactModel =
                    contactModelRepository.getByIdentity(identity);
                if (contactModel != null) {
                    return getContactDisplayName(contactModel, contactNameFormat);
                } else {
                    return identity;
                }
            })
            .sorted()
            .collect(java.util.stream.Collectors.joining(", "));

        if (groupModelData.groupIdentity.getCreatorIdentity().equals(myIdentity)) {
            // If the user is the creator, we prepend it to the list
            return prependUserToList(userService, memberList);
        } else if (groupModelData.isMember()) {
            // If the user is not the creator but a member, we prepend the creator and the user to
            // the list
            memberList = prependCreatorToList(
                contactModelRepository,
                groupModelData.groupIdentity.getCreatorIdentity(),
                memberList,
                contactNameFormat
            );
            return prependUserToList(userService, memberList);
        } else {
            // If the user is not the creator and not a member, we prepend the creator to the list.
            return prependCreatorToList(
                contactModelRepository,
                groupModelData.groupIdentity.getCreatorIdentity(),
                memberList,
                contactNameFormat
            );
        }
    }

    @NonNull
    private static String prependUserToList(@NonNull UserService userService, @NonNull String memberList) {
        @NonNull String userName = userService.getDisplayName();
        if (!memberList.isBlank()) {
            return userName + ", " + memberList;
        } else {
            return userName;
        }
    }

    @NonNull
    private static String prependCreatorToList(
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull String creatorIdentity,
        @NonNull String memberList,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        ch.threema.data.models.ContactModel creatorContactModel = contactModelRepository.getByIdentity(creatorIdentity);
        String creatorName = creatorContactModel != null
            ? getContactDisplayName(creatorContactModel, contactNameFormat)
            : creatorIdentity;
        if (!memberList.isBlank()) {
            return creatorName + ", " + memberList;
        } else {
            return creatorName;
        }
    }

    /**
     * Return the display name for a group.
     * <br>
     * <br>
     * Deprecated: See {@code GetGroupDisplayNameUseCase}
     */
    @Nullable
    @Deprecated
    public static String getGroupDisplayName(
        @NonNull GroupModelOld groupModel,
        @NonNull GroupService groupService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (groupModel.getName() != null && !groupModel.getName().isEmpty()) {
            return groupModel.getName();
        }

        // list members
        StringBuilder name = new StringBuilder();
        for (ContactModel contactModel : groupService.getMembers(groupModel)) {
            if (name.length() > 0) {
                name.append(", ");
            }
            name.append(NameUtil.getContactDisplayName(contactModel, contactNameFormat));
        }

        if (name.length() > 0) {
            return name.toString();
        }

        return groupModel.getApiGroupId().toString();
    }

    /**
     * Return the display name for a distribution list.
     */
    @NonNull
    public static String getDistributionListDisplayName(
        @NonNull DistributionListModel distributionListModel,
        @NonNull DistributionListService distributionListService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        final @Nullable String distributionListModelName = distributionListModel.getName();
        if (distributionListModelName != null && !distributionListModelName.isEmpty()) {
            return distributionListModelName;
        }

        StringBuilder name = new StringBuilder();
        for (ContactModel contactModel : distributionListService.getMembers(distributionListModel)) {
            if (name.length() > 0) {
                name.append(", ");
            }
            name.append(NameUtil.getContactDisplayName(contactModel, contactNameFormat));
        }

        if (name.length() > 0) {
            return name.toString();
        }

        return String.valueOf(distributionListModel.getId());
    }

    @Nullable
    public static String getContactDisplayNameOrNickname(
        @Nullable Context context,
        @Nullable AbstractMessageModel messageModel,
        @NonNull ContactService contactService,
        @NonNull UserService userService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (context == null || messageModel == null) {
            return null;
        }
        if (messageModel.isOutbox()) {
            return userService.getDisplayName();
        } else {
            return getContactDisplayNameOrNickname(
                contactService.getByIdentity(messageModel.getIdentity()),
                true,
                contactNameFormat
            );
        }
    }

    @NonNull
    public static String getShortName(
        @NonNull String identity,
        @Nullable ContactService contactService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        @Nullable String shortname = null;
        if (identity.equals(ContactService.ALL_USERS_PLACEHOLDER_ID)) {
            return ThreemaApplication.getAppContext().getString(R.string.all);
        }
        if (contactService != null) {
            shortname = NameUtil.getShortName(contactService.getByIdentity(identity), contactNameFormat);
        }
        return shortname != null ? shortname : identity;
    }

    @Nullable
    public static String getShortName(
        @Nullable ContactModel contactModel,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (contactModel == null) {
            return null;
        }
        if (TestUtil.isEmptyOrNull(contactModel.getFirstName())) {
            if (TestUtil.isEmptyOrNull(contactModel.getLastName())) {
                return getFallbackName(contactModel);
            } else {
                return getContactDisplayName(contactModel, contactNameFormat);
            }
        } else {
            return contactModel.getFirstName();
        }
    }

    @Nullable
    public static String getShortName(
        @Nullable Context context,
        @Nullable AbstractMessageModel messageModel,
        @NonNull ContactService contactService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (context == null || messageModel == null) {
            return null;
        }
        return messageModel.isOutbox()
            ? context.getString(R.string.me_myself_and_i)
            : getShortName(contactService.getByIdentity(messageModel.getIdentity()), contactNameFormat);
    }

    @NonNull
    private static String getFallbackName(@NonNull ContactModel model) {
        if (!TestUtil.isEmptyOrNull(model.getPublicNickName()) && !model.getPublicNickName().equals(model.getIdentity())) {
            return "~" + model.getPublicNickName();
        } else {
            return model.getIdentity();
        }
    }

    /**
     * Return the display name for a contact.
     */
    @NonNull
    public static String getContactDisplayName(
        @Nullable ContactModel contactModel,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (contactModel == null) {
            return "undefined";
        }

        if (contactModel.getIdentity().isEmpty()) {
            return "invalid contact";
        }

        final @Nullable String firstName = contactModel.getFirstName();
        final @Nullable String lastName = contactModel.getLastName();

        return getContactDisplayName(contactModel.getIdentity(), firstName, lastName, contactNameFormat);
    }

    @NonNull
    public static String getContactDisplayName(
        @Nullable ch.threema.data.models.ContactModel contactModel,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (contactModel == null) {
            return "undefined";
        }

        if (contactModel.getIdentity().isEmpty()) {
            return "invalid contact";
        }

        ContactModelData data = contactModel.getData();
        if (data == null) {
            return "undefined";
        }

        return getContactDisplayName(data.identity, data.firstName, data.lastName, contactNameFormat);
    }

    @NonNull
    public static String getContactDisplayName(
        @NonNull String identity,
        @Nullable String firstName,
        @Nullable String lastName,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if ((firstName == null || firstName.isEmpty()) && (lastName == null || lastName.isEmpty())) {
            return identity;
        }

        @NonNull String displayName = "";

        if (contactNameFormat == ContactNameFormat.FIRSTNAME_LASTNAME) {
            if (firstName != null) {
                displayName += firstName + " ";
            }
            if (lastName != null) {
                displayName += lastName;
            }
        } else if (contactNameFormat == ContactNameFormat.LASTNAME_FIRSTNAME) {
            if (lastName != null) {
                displayName += lastName + " ";
            }
            if (firstName != null) {
                displayName += firstName;
            }
        }

        return displayName.trim();
    }

    /**
     * Return the display name for a contact, or fall back to the nickname.
     */
    @NonNull
    public static String getContactDisplayNameOrNickname(
        @Nullable ContactModel contactModel,
        boolean nicknameWithPrefix,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (contactModel == null) {
            return "";
        }
        String displayName = NameUtil.getContactDisplayName(contactModel, contactNameFormat);
        String nickName = contactModel.getPublicNickName();
        if (
            displayName.equals(contactModel.getIdentity()) &&
                nickName != null &&
                !nickName.isEmpty() &&
                !displayName.equals(nickName)) {
            return nicknameWithPrefix ? "~" + nickName : nickName;
        } else {
            return displayName;
        }
    }

    @NonNull
    public static String getContactDisplayNameOrNickname(
        @NonNull String identity,
        @Nullable ContactService contactService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (contactService == null) {
            return "";
        }
        @NonNull String displayName = NameUtil.getContactDisplayNameOrNickname(
            contactService.getByIdentity(identity),
            true,
            contactNameFormat
        );
        return TextUtils.isEmpty(displayName) ? identity : displayName.substring(0, Math.min(displayName.length(), 24));
    }

    /**
     * Return the name used for quotes and mentions.
     */
    @NonNull
    public static String getQuoteName(
        @Nullable ContactModel contactModel,
        @NonNull UserService userService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (contactModel == null) {
            return "";
        }

        // If the contact is the local user, and the nickname does not equal the identity,
        // return the nickname.
        if (userService.isMe(contactModel.getIdentity())) {
            final String myNickname = userService.getPublicNickname();
            if (!TestUtil.isEmptyOrNull(myNickname) && !myNickname.equals(userService.getIdentity())) {
                return myNickname;
            }
        }

        return getContactDisplayNameOrNickname(contactModel, true, contactNameFormat);
    }

    /**
     * Return the name used for quotes and mentions. If the contact is not known or an error occurs
     * while getting the quote name, the identity is returned if not null. Otherwise an empty string
     * is returned.
     */
    @NonNull
    public static String getQuoteName(
        @Nullable String identity,
        @Nullable ContactService contactService,
        @Nullable UserService userService,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (contactService == null || userService == null || identity == null) {
            return (identity != null) ? identity : "";
        }
        if (ContactService.ALL_USERS_PLACEHOLDER_ID.equals(identity)) {
            return ThreemaApplication.getAppContext().getString(R.string.all);
        }
        final @Nullable ContactModel contactModel = contactService.getByIdentity(identity);
        final @NonNull String quoteName = getQuoteName(contactModel, userService, contactNameFormat);
        return (quoteName.isBlank()) ? identity : quoteName;
    }

    /**
     * Extract first and last name from display name as provided by the Android contact database
     * If displayName is empty or null, empty strings will be returned for first/last name.
     *
     * @param displayName Name of the contact to split
     * @return A Pair containing first and last name
     */
    @NonNull
    public static Pair<String, String> getFirstLastNameFromDisplayName(@Nullable String displayName) {
        final String[] parts = displayName == null ? null : displayName.split(" ");
        if (parts == null || parts.length == 0) {
            return new Pair<>("", "");
        }
        final String firstName = parts[0];
        final String lastName = Arrays.stream(parts)
            .skip(1)
            .collect(Collectors.joining(" "));
        return new Pair<>(firstName, lastName);
    }
}
