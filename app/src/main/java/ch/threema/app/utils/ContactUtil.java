package ch.threema.app.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.icu.text.ListFormatter;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateUtils;

import org.slf4j.Logger;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.util.Pair;
import ch.threema.app.AppConstants;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.FileService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.tasks.OnFSFeatureMaskDowngradedTask;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.common.JavaCompat.isNullOrEmpty;

import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.models.WorkVerificationLevel;
import ch.threema.storage.models.ContactModel;

/**
 * Static utility functions related to contacts.
 * <p>
 * TODO(ANDR-2985): Most of these could be contact model methods.
 */
public class ContactUtil {
    private static final Logger logger = getThreemaLogger("ContactUtil");

    public static final int CHANNEL_NAME_MAX_LENGTH_BYTES = 256;

    public static final long PROFILE_PICTURE_BLOB_CACHE_DURATION = DateUtils.WEEK_IN_MILLIS;

    public static boolean canChangeFirstName(@Nullable ContactModel contact) {
        return contact != null && !contact.isLinkedToAndroidContact();
    }

    public static boolean canChangeLastName(@Nullable ContactModel contact) {
        return contact != null
            && !contact.isLinkedToAndroidContact()
            && !isGatewayContact(contact.getIdentity());
    }

    public static boolean canChangeAvatar(
        @NonNull ContactModel contactModel,
        @NonNull PreferenceService preferenceService,
        @NonNull FileService fileService
    ) {
        return canHaveCustomAvatar(contactModel)
            && !(preferenceService.getProfilePicReceive() && fileService.hasContactDefinedProfilePicture(contactModel.getIdentity()));
    }

    /**
     * Return whether this {@param contactModel} is a Threema Gateway contact.
     */
    public static boolean isGatewayContact(ContactModel contactModel) {
        if (contactModel != null) {
            return isGatewayContact(contactModel.getIdentity());
        }
        return false;
    }

    /**
     * Return whether this {@param identity} is a Threema Gateway contact (starting with "*").
     */
    public static boolean isGatewayContact(@NonNull String identity) {
        return identity.startsWith("*");
    }

    /**
     * Return whether this {@param contactModel} is a Threema Gateway contact
     * or the special contact "ECHOECHO".
     */
    public static boolean isEchoEchoOrGatewayContact(ContactModel contactModel) {
        return contactModel != null
            && (isGatewayContact(contactModel.getIdentity())
            || AppConstants.ECHO_USER_IDENTITY.equals(contactModel.getIdentity())
        );
    }

    /**
     * Return whether this {@param identity} is a Threema Gateway contact (starting with "*")
     * or the special contact "ECHOECHO".
     */
    public static boolean isEchoEchoOrGatewayContact(@NonNull String identity) {
        return isGatewayContact(identity)
            || AppConstants.ECHO_USER_IDENTITY.equals(identity);
    }

    public static boolean canReceiveVoipMessages(
        @Nullable ContactModel contactModel,
        @Nullable BlockedIdentitiesService blockedIdentitiesService
    ) {
        return contactModel != null
            && blockedIdentitiesService != null
            && !blockedIdentitiesService.isBlocked(contactModel.getIdentity())
            && !isEchoEchoOrGatewayContact(contactModel);
    }

    public static boolean canReceiveVoipMessages(
        @Nullable String identity,
        @Nullable BlockedIdentitiesService blockedIdentitiesService
    ) {
        return identity != null
            && blockedIdentitiesService != null
            && !blockedIdentitiesService.isBlocked(identity)
            && !isEchoEchoOrGatewayContact(identity);
    }

    public static boolean allowedChangeToState(
        @Nullable IdentityState oldState,
        @Nullable IdentityState newState
    ) {
        if (oldState == newState || newState == null) {
            return false;
        }
        switch (newState) {
            //change to active is always allowed
            case ACTIVE:
                return true;
            case INACTIVE:
                return oldState == IdentityState.ACTIVE;
            case INVALID:
                return true;
        }
        return false;
    }

    public static boolean canHaveCustomAvatar(@Nullable ContactModel contact) {
        return contact != null
            && !isGatewayContact(contact.getIdentity());
    }

    /**
     * returns a representation of the contact's name according to sort settings,
     * suitable for comparing
     */
    public static String getSafeNameString(ContactModel contactModel, PreferenceService.ContactSortOrder sortOrder) {
        return getSortKey(
            contactModel.getIdentity(),
            contactModel.getFirstName(),
            contactModel.getLastName(),
            contactModel.getPublicNickName(),
            sortOrder
        );
    }

    public static String getSortKey(
        @Nullable String identity,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String publicNickName,
        @NonNull PreferenceService.ContactSortOrder sortOrder
    ) {
        if (isNullOrEmpty(firstName) && isNullOrEmpty(lastName) && !isNullOrEmpty(publicNickName)) {
            Pair<String, String> namePair = NameUtil.getFirstLastNameFromDisplayName(publicNickName.trim());
            firstName = namePair.first;
            lastName = namePair.second;
        }
        String key = identity;
        if (sortOrder == PreferenceService.ContactSortOrder.FIRST_NAME) {
            if (!TextUtils.isEmpty(lastName)) {
                key = lastName + key;
            }
            if (!TextUtils.isEmpty(firstName)) {
                key = firstName + key;
            }
        } else {
            if (!TextUtils.isEmpty(firstName)) {
                key = firstName + key;
            }
            if (!TextUtils.isEmpty(lastName)) {
                key = lastName + key;
            }
        }
        return key;
    }

    public static @NonNull Comparator<ContactModel> getContactComparator(PreferenceService.ContactSortOrder sortOrder) {
        final Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        return (contact1, contact2) -> collator.compare(
            getSortKey(contact1, sortOrder), getSortKey(contact2, sortOrder)
        );
    }

    private static @NonNull String getSortKey(ContactModel contactModel, PreferenceService.ContactSortOrder sortOrder) {
        String key = ContactUtil.getSafeNameString(contactModel, sortOrder);

        if (contactModel.getIdentity().startsWith("*")) {
            key = "\uFFFF" + key;
        }
        return key;
    }

    public static @DrawableRes int getVerificationLevelIconResource(
        @NonNull VerificationLevel verificationLevel,
        @NonNull WorkVerificationLevel workVerificationLevel
    ) {
        int iconResource;
        boolean isWorkVerifiedOnWorkBuild = BuildFlavor.IS_WORK
            && workVerificationLevel == WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED;
        switch (verificationLevel) {
            case SERVER_VERIFIED:
                if (isWorkVerifiedOnWorkBuild) {
                    iconResource = R.drawable.ic_verification_server_work;
                } else {
                    iconResource = R.drawable.ic_verification_server;
                }
                break;
            case FULLY_VERIFIED:
                if (isWorkVerifiedOnWorkBuild) {
                    iconResource = R.drawable.ic_verification_full_work;
                } else {
                    iconResource = R.drawable.ic_verification_full;
                }
                break;
            case UNVERIFIED:
            default:
                iconResource = R.drawable.ic_verification_none;
                break;
        }
        return iconResource;
    }

    /**
     * Get the verification level description from the given verification level. This also depends
     * on the build and whether the contact is a work contact or not.
     *
     * @return String resource for the according verification level
     */
    @StringRes
    public static int getVerificationLevelDescription(
        @NonNull VerificationLevel verificationLevel,
        @NonNull WorkVerificationLevel workVerificationLevel
    ) {
        boolean isWorkVerifiedOnWorkBuild = BuildFlavor.IS_WORK && workVerificationLevel == WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED;
        switch (verificationLevel) {
            case FULLY_VERIFIED:
                if (isWorkVerifiedOnWorkBuild) {
                    return R.string.verification_level3_work_explain;
                } else {
                    return R.string.verification_level3_explain;
                }
            case SERVER_VERIFIED:
                if (isWorkVerifiedOnWorkBuild) {
                    return R.string.verification_level2_work_explain;
                }
                return R.string.verification_level2_explain;
            default:
                return R.string.verification_level1_explain;
        }
    }

    @Nullable
    public static Drawable getVerificationDrawable(
        @NonNull Context context,
        @NonNull VerificationLevel verificationLevel,
        @NonNull WorkVerificationLevel workVerificationLevel
    ) {
        return AppCompatResources.getDrawable(context, getVerificationLevelIconResource(
            verificationLevel,
            workVerificationLevel
        ));
    }

    public static String joinDisplayNames(
        @Nullable Context context,
        @Nullable List<ContactModel> contacts,
        @NonNull ContactNameFormat contactNameFormat
    ) {
        if (contacts == null) {
            return "";
        }

        List<String> contactNames = contacts.stream()
            .map(contact -> NameUtil.getContactDisplayNameOrNickname(contact, true, contactNameFormat))
            .collect(Collectors.toList());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context != null) {
            return ListFormatter.getInstance(LocaleUtil.getCurrentLocale()).format(contactNames);
        } else {
            return String.join(", ", contactNames);
        }
    }

    /**
     * Perform the required steps if a contact does not support forward security anymore due to a
     * change of its feature mask. This includes creating a status message in the conversation with
     * that contact to warn the user that forward security has been disabled for this contact. This
     * method also terminates all existing sessions with the contact.
     * <p>
     * Note that the status message is only created if a forward security session currently exists.
     * <p>
     * Note that this method must only be called if the feature mask of a contact is changed from a
     * feature mask that indicates forward security support to a feature mask without forward
     * security support.
     *
     * @param contactModel the affected contact
     */
    public static void onForwardSecurityNotSupportedAnymore(@NonNull ch.threema.data.models.ContactModel contactModel) {
        ServiceManager serviceManager = ServiceManager.get();
        if (serviceManager == null) {
            logger.error("Service manager is null");
            return;
        }

        serviceManager.getTaskManager().schedule(
            new OnFSFeatureMaskDowngradedTask(contactModel.getIdentity())
        );
    }
}
