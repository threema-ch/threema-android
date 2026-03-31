package ch.threema.app.services;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.profilepicture.ProfilePicture;
import ch.threema.base.SessionScoped;
import ch.threema.data.models.ContactModelData;
import ch.threema.domain.fs.DHSession;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.taskmanager.ActiveTaskCodec;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.access.AccessModel;
import java.util.function.Consumer;

@SessionScoped
public interface ContactService extends AvatarService<String> {

    String ALL_USERS_PLACEHOLDER_ID = "@@@@@@@@";

    class ProfilePictureUploadData {
        public ProfilePicture profilePicture;
        public byte[] blobId;
        public byte[] encryptionKey;
        public int size;
        public long uploadedAt;
    }

    class ProfilePictureSharePolicy {

        // Do NOT change the order of these values, as features rely on the `ordinal` value to stay the same
        public enum Policy {
            NOBODY,
            EVERYONE,
            ALLOW_LIST;

            @Nullable
            public static Policy fromIntOrNull(final int value) {
                switch (value) {
                    case PreferenceService.PROFILEPIC_RELEASE_NOBODY:
                        return Policy.NOBODY;
                    case PreferenceService.PROFILEPIC_RELEASE_EVERYONE:
                        return Policy.EVERYONE;
                    case PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST:
                        return Policy.ALLOW_LIST;
                    default:
                        return null;
                }
            }
        }

        @NonNull
        private final Policy policy;

        @NonNull
        private final List<String> allowedIdentities;

        ProfilePictureSharePolicy(@NonNull Policy policy, @NonNull List<String> allowedIdentities) {
            this.policy = policy;
            this.allowedIdentities = allowedIdentities;
        }

        @NonNull
        public Policy getPolicy() {
            return policy;
        }

        @NonNull
        public List<String> getAllowedIdentities() {
            return allowedIdentities;
        }
    }

    class ForwardSecuritySessionState {
        private enum ForwardSecurityState {
            NO_SESSION,
            UNSUPPORTED_BY_REMOTE,
            L20,
            R20,
            R24,
            RL44,
        }

        private final ForwardSecurityState forwardSecurityState;
        private final DHSession.DHVersions currentDHVersions;

        private ForwardSecuritySessionState(@NonNull ForwardSecurityState forwardSecurityState) {
            this(forwardSecurityState, null);
        }

        private ForwardSecuritySessionState(
            @NonNull ForwardSecurityState forwardSecurityState,
            @Nullable DHSession.DHVersions currentDHVersions
        ) {
            this.forwardSecurityState = forwardSecurityState;
            this.currentDHVersions = currentDHVersions;
        }

        protected static ForwardSecuritySessionState noSession() {
            return new ForwardSecuritySessionState(ForwardSecurityState.NO_SESSION);
        }

        protected static ForwardSecuritySessionState unsupportedByRemote() {
            return new ForwardSecuritySessionState(ForwardSecurityState.UNSUPPORTED_BY_REMOTE);
        }

        protected static ForwardSecuritySessionState fromDHState(
            @NonNull DHSession.State state,
            @Nullable DHSession.DHVersions dhVersions
        ) {
            switch (state) {
                case L20:
                    return new ForwardSecuritySessionState(ForwardSecurityState.L20, dhVersions);
                case R20:
                    return new ForwardSecuritySessionState(ForwardSecurityState.R20, dhVersions);
                case R24:
                    return new ForwardSecuritySessionState(ForwardSecurityState.R24, dhVersions);
                case RL44:
                    return new ForwardSecuritySessionState(ForwardSecurityState.RL44, dhVersions);
                default:
                    throw new IllegalStateException("No such dh state: " + state);
            }
        }

        @NonNull
        @Override
        public String toString() {
            switch (forwardSecurityState) {
                case NO_SESSION:
                    return "No session";
                case L20:
                    return "L20 " + currentDHVersions;
                case R20:
                    return "R20 " + currentDHVersions;
                case R24:
                    return "R24 " + currentDHVersions;
                case RL44:
                    return "RL44 " + currentDHVersions;
                case UNSUPPORTED_BY_REMOTE:
                    return "Unsupported by remote";
                default:
                    return "Unknown state";
            }
        }
    }

    interface Filter {

        /**
         * States filter
         */
        IdentityState[] states();

        @Nullable
        default Long requiredFeature() {
            return null;
        }

        /**
         * Update feature Level of Contacts.
         * <p>
         * NOTE: The feature level will only be fetched if `requiredFeature()` is set
         * and for contacts that don't have that feature.
         */
        Boolean fetchMissingFeatureLevel();

        /**
         * Include own identity
         */
        Boolean includeMyself();

        /**
         * include hidden contacts
         */
        Boolean includeHidden();

        /*
         * Limit to contacts with individual settings for read receipts and typing indicators
         */
        default boolean onlyWithReceiptSettings() {
            return false;
        }
    }

    /**
     * The contact selection to include or exclude invalid contacts.
     */
    enum ContactSelection {
        /**
         * Includes invalid contacts. Note that in the methods {@link #getAllDisplayed(ContactSelection)}
         * and {@link #getAllDisplayedWork(ContactSelection)}, this may be overridden by the
         * preferences.
         */
        INCLUDE_INVALID,
        /**
         * Don't include invalid contacts.
         */
        EXCLUDE_INVALID,
    }

    /**
     * Get all contacts (including work contacts) depending on the preference to display inactive
     * contacts. If inactive contacts are hidden by the preference, then invalid contacts are not
     * included in this list neither. If inactive contacts should be shown according to the
     * preference, then depending on the argument, inactive and invalid contacts may be included.
     * <p>
     * Note that the result does not include hidden contacts.
     * <p>
     * This list also includes work contacts.
     * <p>
     * If a list of contacts is sought without depending on the inactive contacts preference, the
     * method {@link #find(Filter)} can be used.
     *
     * @param contactSelection the option to include or exclude invalid contacts
     * @return a list of the contact models
     */
    @NonNull
    List<ContactModel> getAllDisplayed(@NonNull ContactSelection contactSelection);

    /**
     * Get all contacts. This does not depend on any user preferences. Invalid and hidden contacts
     * are included as well.
     *
     * @return a list of all contact models
     */
    @NonNull
    List<ContactModel> getAll();

    /**
     * Get a list of contact models based on the given filter.
     *
     * @param filter the filter that is applied to the result
     * @return a list of contact models
     * @see #getAllDisplayed
     * @see #getAllDisplayedWork
     */
    @NonNull
    List<ContactModel> find(Filter filter);

    @Nullable
    ContactModel getByLookupKey(String lookupKey);

    /**
     * Returns the contact model of the provided identity if a contact with this identity exists or
     * the identity is the user's identity.
     * <br>
     * <br>
     * @deprecated Note that {@code ContactModelRepository#getByIdentity} or {@code ContactModelRepository#existsByIdentity}
     * should be used instead of this method. Be aware that these won't consider the user's identity
     * as a contact's identity.
     * <br>
     * TODO(ANDR-4555): Do not return the user's contact model if queried
     */
    @Nullable
    @Deprecated
    ContactModel getByIdentity(@Nullable String identity);

    /**
     * Tries to read the requested contact models from cache first, and adds missing models from database (if existing). If one or all identit(y/ies)
     * could not be found, there will be no error. In this case the result list will just miss these models.
     * <br>
     * <br>
     * <b>Order:</b> The result list does not guarantee to be in the same order as the given {@code identities} list
     * <br>
     * <br>
     * <b>Own user:</b> The own users model (see {@code getMe()}) will be part of the result list if requested.
     * <br>
     * <br>
     * @deprecated Note that {@code ContactModelRepository#getByIdentities} should be used instead
     * of this method. Be aware that this won't include the user as a contact model.
     * <br>
     * TODO(ANDR-4555): Do not include the user's contact model
     */
    @NonNull
    @Deprecated
    List<ContactModel> getByIdentities(@NonNull List<String> identities);

    /**
     * Get all work contacts depending on the preference to display inactive contacts. If inactive
     * contacts are hidden by the preference, then invalid contacts are not included in this list
     * neither. If inactive contacts should be shown according to the preference, then depending on
     * the contact selection argument, invalid contacts may be included.
     * <p>
     * Note that this does not include hidden contacts.
     * <p>
     * If a list of contacts is sought without depending on the inactive contacts preference, the
     * method {@link #find(Filter)} can be used.
     *
     * @param contactSelection the states that should be included (if enabled in preferences)
     * @return a list of the contact models
     */
    @NonNull
    List<ContactModel> getAllDisplayedWork(ContactSelection contactSelection);

    /**
     * Get all work contacts. This does not depend on any user preferences. Invalid and hidden
     * contacts are also included.
     *
     * @return a list of the work contact models
     */
    @NonNull
    List<ContactModel> getAllWork();

    int countIsWork();

    List<ContactModel> getCanReceiveProfilePics();

    List<String> getSynchronizedIdentities();

    @Nullable
    List<String> getIdentitiesByVerificationLevel(VerificationLevel verificationLevel);

    /**
     * Update the acquaintance level of the given identity if it exists as a contact.
     *
     * @param identity          the identity of the contact
     * @param acquaintanceLevel the new acquaintance level
     */
    void setAcquaintanceLevel(
        @NonNull String identity,
        @NonNull ContactModel.AcquaintanceLevel acquaintanceLevel
    );

    /**
     * Mark the contact as archived or unarchived. This change is reflected and uses the new contact
     * model. Listeners are triggered by the contact model repository.
     * <p>
     * TODO(ANDR-3721): Use this method with care until the pinned state is moved to the same
     *  database column as the archived state. This method must only be called with isArchived=true
     *  when the conversation of this contact is *not* pinned.
     *
     * @param identity      the identity of the contact
     * @param isArchived    whether the contact should be archived or not
     * @param triggerSource the source that triggered this action
     */
    void setIsArchived(
        @NonNull String identity,
        boolean isArchived,
        @NonNull TriggerSource triggerSource
    );

    /**
     * Set the `lastUpdate` field of the specified contact to the current date.
     * <p>
     * This will also save the model and notify listeners.
     */
    void bumpLastUpdate(@NonNull String identity);

    /**
     * Get the `lastUpdate` field of the specified contact.
     */
    @Nullable
    Date getLastUpdate(@NonNull String identity);

    /**
     * Clear the `lastUpdate` field of the specified contact.
     * <p>
     * This will result in the conversation being removed from the conversation list.
     * <p>
     * Save the model and notify listeners.
     */
    void clearLastUpdate(@NonNull String identity);

    /**
     * Persists the forward security state for the given identity. Note that the old contact model
     * isn't updated.
     */
    void persistForwardSecurityState(
        @NonNull String identity,
        @ContactModel.ForwardSecurityState int forwardSecurityState
    );

    AccessModel getAccess(@Nullable String identity);

    void setIsTyping(@NonNull String identity, boolean isTyping);

    boolean isTyping(String identity);

    /**
     * Send a typing indicator if the preferences do not prevent that.
     *
     * @param toIdentity the identity that should receive the typing indicator
     * @param isTyping   whether the user is typing or not
     */
    void sendTypingIndicator(String toIdentity, boolean isTyping);

    /**
     * Create a new message receiver for the specified contact model.
     */
    @NonNull
    ContactMessageReceiver createReceiver(ContactModel contact);

    /**
     * Create a new message receiver for the specified contact model. Note that the return value is
     * null, if the old contact model does not exist with the identity of the given model.
     */
    @Nullable
    ContactMessageReceiver createReceiver(@NonNull ch.threema.data.models.ContactModel contact);

    /**
     * Create a new message receiver for the given identity. Note that the return value is null if
     * there is no contact with the provided identity.
     */
    @Nullable
    ContactMessageReceiver createReceiver(@NonNull String identity);

    void removeAllSystemContactLinks();

    /**
     * Set the user defined profile picture. Depending on the trigger source, the change is also
     * reflected.
     *
     * @return true if storing the profile picture succeeded
     */
    boolean setUserDefinedProfilePicture(
        @Nullable ContactModel contactModel,
        @Nullable File temporaryAvatarFile,
        @NonNull TriggerSource triggerSource
    );

    /**
     * Set the user defined profile picture. Depending on the trigger source, the change is also
     * reflected.
     *
     * @return true if storing the profile picture succeeded
     */
    boolean setUserDefinedProfilePicture(
        @NonNull String identity,
        @Nullable File temporaryAvatarFile,
        @NonNull TriggerSource triggerSource
    );

    /**
     * Set the user defined profile picture. Depending on the trigger source, the change is also
     * reflected.
     *
     * @return true if storing the profile picture succeeded
     * @throws IOException              when the byte array cannot be written to the file
     * @throws MasterKeyLockedException when the master key is locked
     */
    boolean setUserDefinedProfilePicture(
        @NonNull String identity,
        @Nullable byte[] avatar,
        @NonNull TriggerSource triggerSource
    ) throws IOException, MasterKeyLockedException;

    /**
     * Remove the user defined profile picture. Depending on the trigger source, the change is also
     * reflected.
     *
     * @return true if the removal succeeded
     */
    boolean removeUserDefinedProfilePicture(
        @NonNull String identity,
        @NonNull TriggerSource triggerSource
    );

    @NonNull
    ProfilePictureSharePolicy getProfilePictureSharePolicy();

    /**
     * Check whether the app settings allow the profile picture to be sent to the identity. Note that
     * this method does <b>not</b> check whether the contact is a gateway ID or ECHOECHO.
     *
     * @return {@code true} if the profile picture could be sent, {@code false} otherwise
     */
    boolean isContactAllowedToReceiveProfilePicture(@NonNull String identity);

    boolean showBadge(@Nullable String string);

    boolean showBadge(@Nullable ContactModel contactModel);

    boolean showBadge(@NonNull ContactModelData contactModelData);

    String getAndroidContactLookupUriString(ContactModel contactModel);

    /**
     * Remove the specified contact from the contact cache.
     * This has to be called after every mutation of a ContactModel
     */
    void invalidateCache(@NonNull String identity);

    @WorkerThread
    void resetReceiptsSettings();

    void reportSpam(@NonNull String identity, @Nullable Consumer<Void> onSuccess, @Nullable Consumer<String> onFailure);

    /**
     * Get the forward security state of a given contact.
     *
     * @return the forward security state of a given contact
     */
    @Nullable
    ForwardSecuritySessionState getForwardSecurityState(
        @NonNull ContactModel contactModel,
        @NonNull ActiveTaskCodec handle
    );

    /**
     * This will wipe every value of `notificationTriggerPolicyOverride` and trigger
     * contact syncs for mutated models.
     */
    void resetAllNotificationTriggerPolicyOverrideFromLocal();

    /**
     * @return A list of all removed contact identities loaded from database. A contact is removed when it`s acquaintance level
     * is {@code AcquaintanceLevel.GROUP} and it is not part of any common group with the current user anymore. In case of an error,
     * an empty result set is returned.
     */
    @NonNull
    Set<String> getRemovedContacts();

    /**
     * @return A snapshot of the currently typing contacts
     */
    @NonNull
    Set<String> getTypingIdentities();
}
