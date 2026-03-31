package ch.threema.app.utils;

import org.slf4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.base.utils.Base32;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.data.models.GroupModel;
import ch.threema.domain.models.Contact;
import ch.threema.storage.models.group.GroupModelOld;

public class GroupUtil {

    private static final Logger logger = getThreemaLogger("GroupUtil");

    public static String CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX = "☁";

    private static final String GROUP_UID_PREFIX = "g-";

    /**
     * Return true, if the group is created by a normal threema user
     * or by a gateway id and marked with a special prefix (cloud emoji {@link CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX}) as "centrally managed group"
     *
     * @see <a href="https://broadcast.threema.ch/en/faq#central-groups">What are centrally managed group chats?</a>
     */
    public static boolean shouldSendMessagesToCreator(@NonNull GroupModelOld groupModel) {
        return shouldSendMessagesToCreator(groupModel.getCreatorIdentity(), groupModel.getName());
    }

    public static boolean shouldSendMessagesToCreator(@NonNull String groupCreator, @Nullable String groupName) {
        return
            !ContactUtil.isGatewayContact(groupCreator)
                || (groupName != null && groupName.startsWith(CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX));
    }

    public static Set<String> getRecipientIdentitiesByFeatureSupport(@NonNull GroupFeatureSupport groupFeatureSupport) {
        return groupFeatureSupport
            .getContactsWithFeatureSupport()
            .stream()
            .map(Contact::getIdentity)
            .collect(Collectors.toSet());
    }

    @Deprecated
    public static int getUniqueId(@Nullable GroupModelOld groupModel) {
        if (groupModel == null) {
            return 0;
        }
        return getUniqueId(groupModel.getId());
    }

    @Deprecated
    public static int getUniqueId(@Nullable GroupModel groupModel) {
        if (groupModel == null) {
            return 0;
        }
        return getUniqueId(groupModel.getDatabaseId());
    }

    @NonNull
    public static String getUniqueIdString(@Nullable GroupModelOld groupModel) {
        if (groupModel != null) {
            return getUniqueIdString(groupModel.getId());
        }
        return "";
    }

    @NonNull
    public static String getUniqueIdString(@Nullable GroupModel groupModel) {
        if (groupModel != null) {
            return getUniqueIdString(groupModel.getDatabaseId());
        }
        return "";
    }

    @NonNull
    public static String getUniqueIdString(long groupId) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update((GROUP_UID_PREFIX + groupId).getBytes());
            return Base32.encode(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            logger.warn("Could not calculate unique id string", e);
        }
        return "";
    }

    @Deprecated
    private static int getUniqueId(long groupDatabaseId) {
        return (GROUP_UID_PREFIX + groupDatabaseId).hashCode();
    }

}
