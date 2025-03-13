/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.preference;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.widget.Toast;

import org.slf4j.Logger;

import java.util.Date;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.asynctasks.ContactAvailable;
import ch.threema.app.asynctasks.PolicyViolation;
import ch.threema.app.debug.PatternLibraryActivity;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.preference.developer.ContentCreator;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.messages.TextMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

import static ch.threema.storage.models.data.status.VoipStatusDataModel.NO_CALL_ID;

@SuppressWarnings("unused")
public class SettingsDeveloperFragment extends ThreemaPreferenceFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SettingsDeveloperFragment");

    // Test identities.
    private static final String TEST_IDENTITY_1 = "ADDRTCNX";
    private static final String TEST_IDENTITY_2 = "H6AXSHKC";

    private PreferenceService preferenceService;
    private DatabaseServiceNew databaseService;
    private ContactService contactService;
    private MessageService messageService;
    private UserService userService;
    private MultiDeviceManager multiDeviceManager;
    private APIConnector apiConnector;
    private ContactModelRepository contactModelRepository;

    @Override
    public void initializePreferences() {
        if (!requiredInstances()) {
            return;
        }

        initMdSetting();
        initConversationSetting();

        // Reset reaction tooltip
        getPref(getResources().getString(R.string.preferences__dev_reset_reaction_tooltip_shown))
            .setOnPreferenceClickListener(this::resetReactionTooltipShown);

        // Generate messages with reactions
        final Preference generateReactionsPreference = getPref(R.string.preferences__dev_create_messages_with_reactions);
        generateReactionsPreference.setOnPreferenceClickListener(this::generateReactionMessages);

        // Generate nonces
        final Preference generateCspNoncesPreference = getPref(R.string.preferences__dev_create_nonces);
        generateCspNoncesPreference.setOnPreferenceClickListener(this::generateNonces);

        // Generate VoIP messages
        final Preference generateVoipPreference = getPref(getResources().getString(R.string.preferences__generate_voip_messages));
        generateVoipPreference.setSummary("Create the test identity " + TEST_IDENTITY_1
            + " and add all possible VoIP messages to that conversation.");
        generateVoipPreference.setOnPreferenceClickListener(this::generateVoipMessages);

        // Generate test quotes
        final Preference generateRecursiveQuote = getPref(getResources().getString(R.string.preferences__generate_test_quotes));
        generateRecursiveQuote.setSummary("Create the test identities " + TEST_IDENTITY_1 + " and "
            + TEST_IDENTITY_2 + " and add some test quotes.");
        generateRecursiveQuote.setOnPreferenceClickListener(this::generateTestQuotes);

        // Theming
        final Preference openPatternLibraryPreference = getPref(getResources().getString(R.string.preferences__open_pattern_library));
        openPatternLibraryPreference.setOnPreferenceClickListener((preference) -> {
            startActivity(new Intent(getContext(), PatternLibraryActivity.class));
            return true;
        });

        // Remove developer menu
        final Preference removeMenuPreference = getPref(getResources().getString(R.string.preferences__remove_menu));
        removeMenuPreference.setSummary("Hide the developer menu from the settings.");
        removeMenuPreference.setOnPreferenceClickListener(this::hideDeveloperMenu);
    }

    @UiThread
    private void showToast(CharSequence msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
    }

    @UiThread
    private void showError(Exception e) {
        logger.error("Exception", e);
        showToast(e.toString());
    }

    private boolean resetReactionTooltipShown(Preference ignored) {
        logger.info("Reset emoji reaction tooltip");
        String key = getString(R.string.preferences__tooltip_emoji_reactions_shown);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        sharedPreferences.edit()
            .putBoolean(getString(R.string.preferences__tooltip_emoji_reactions_shown), false)
            .putInt(getString(R.string.preferences__tooltip_emoji_reactions_shown_counter), 0)
            .apply();
        showToast("Reaction tooltip reset");
        return true;
    }

    @WorkerThread
    private ContactModel createTestContact(
        String identity,
        String firstName,
        String lastName
    ) throws InvalidEntryException, PolicyViolationException {
        ContactResult result = new BasicAddOrUpdateContactBackgroundTask(
            identity,
            ContactModel.AcquaintanceLevel.DIRECT,
            userService.getIdentity(),
            apiConnector,
            contactModelRepository,
            AddContactRestrictionPolicy.CHECK,
            ThreemaApplication.getAppContext(),
            null
        ).runSynchronously();

        if (result instanceof ContactAvailable) {
            ((ContactAvailable) result).getContactModel().setNameFromLocal(firstName, lastName);

            ContactModel contactModel = contactService.getByIdentity(identity);
            if (contactModel == null) {
                throw new IllegalStateException("Contact model is null after adding it");
            }
            return contactModel;
        } else if (result instanceof PolicyViolation) {
            throw new PolicyViolationException();
        } else {
            throw new InvalidEntryException(R.string.invalid_threema_id);
        }
    }

    @UiThread
    private boolean generateReactionMessages(Preference ignored) {
        ContentCreator.createReactionSpam(
            ThreemaApplication.requireServiceManager(),
            getParentFragmentManager()
        );
        return true;
    }

    @UiThread
    private boolean generateNonces(Preference ignored) {
        ContentCreator.createNonces(
            ThreemaApplication.requireServiceManager(),
            getParentFragmentManager()
        );
        return true;
    }

    @UiThread
    @SuppressLint("StaticFieldLeak")
    private boolean generateVoipMessages(Preference preference) {

        // Pojo for holding test data.
        class VoipMessage {
            final VoipStatusDataModel dataModel;
            final String description;

            VoipMessage(VoipStatusDataModel dataModel, String description) {
                this.dataModel = dataModel;
                this.description = description;
            }
        }

        // Test messages
        final VoipMessage[] testMessages = new VoipMessage[]{
            new VoipMessage(VoipStatusDataModel.createMissed(NO_CALL_ID, null), "missed"),
            new VoipMessage(VoipStatusDataModel.createFinished(NO_CALL_ID, 42), "finished"),
            new VoipMessage(VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.UNKNOWN), "rejected (unknown)"),
            new VoipMessage(VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.BUSY), "rejected (busy)"),
            new VoipMessage(VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.TIMEOUT), "rejected (timeout)"),
            new VoipMessage(VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.REJECTED), "rejected (rejected)"),
            new VoipMessage(VoipStatusDataModel.createRejected(NO_CALL_ID, VoipCallAnswerData.RejectReason.DISABLED), "rejected (disabled)"),
            new VoipMessage(VoipStatusDataModel.createRejected(NO_CALL_ID, (byte) 99), "rejected (invalid reason code)"),
            new VoipMessage(VoipStatusDataModel.createRejected(NO_CALL_ID, null), "rejected (null reason code)"),
            new VoipMessage(VoipStatusDataModel.createAborted(NO_CALL_ID), "aborted"),
        };

        new AsyncTask<Void, Void, Exception>() {
            @Override
            @Nullable
            protected Exception doInBackground(Void... voids) {
                try {
                    // Create test identity
                    final ContactModel contact = createTestContact(TEST_IDENTITY_1, "Developer", "Testcontact");

                    // Create test messages
                    final ContactMessageReceiver receiver = contactService.createReceiver(contact);
                    messageService.createStatusMessage("Creating test messages...", receiver);
                    for (boolean isOutbox : new boolean[]{true, false}) {
                        for (VoipMessage msg : testMessages) {
                            final String text = (isOutbox ? "Outgoing " : "Incoming ") + msg.description;
                            messageService.createStatusMessage(text, receiver);
                            messageService.createVoipStatus(msg.dataModel, receiver, isOutbox, true);
                        }
                    }

                    return null;
                } catch (Exception e) {
                    return e;
                }
            }

            @Override
            protected void onPostExecute(@Nullable Exception e) {
                if (e == null) {
                    showToast("Test messages created!");
                } else {
                    showError(e);
                }
            }
        }.execute();
        return true;
    }

    @UiThread
    @SuppressLint("StaticFieldLeak")
    private boolean generateTestQuotes(Preference preference) {
        new AsyncTask<Void, Void, Exception>() {
            @Override
            @Nullable
            protected Exception doInBackground(Void... voids) {
                try {
                    // Create test identity
                    final ContactModel contact1 = createTestContact(TEST_IDENTITY_1, "Developer", "Testcontact");
                    final ContactMessageReceiver receiver1 = contactService.createReceiver(contact1);
                    final ContactModel contact2 = createTestContact(TEST_IDENTITY_2, "Another Developer", "Testcontact");
                    final ContactMessageReceiver receiver2 = contactService.createReceiver(contact2);

                    messageService.createStatusMessage("Creating test quotes...", receiver1);

                    // Create recursive quote
                    final MessageId messageIdRecursive = new MessageId();
                    TextMessage messageRecursive = new TextMessage();
                    messageRecursive.setFromIdentity(contact1.getIdentity());
                    messageRecursive.setToIdentity(userService.getIdentity());
                    messageRecursive.setDate(new Date());
                    messageRecursive.setMessageId(messageIdRecursive);
                    messageRecursive.setText("> quote #" + messageIdRecursive + "\n\na quote that references itself");
                    messageService.processIncomingContactMessage(messageRecursive);

                    // Create cross-chat quote
                    final MessageId messageIdCrossChat1 = new MessageId();
                    final MessageId messageIdCrossChat2 = new MessageId();
                    TextMessage messageChat2 = new TextMessage();
                    messageChat2.setFromIdentity(contact2.getIdentity());
                    messageChat2.setToIdentity(userService.getIdentity());
                    messageChat2.setDate(new Date());
                    messageChat2.setMessageId(messageIdCrossChat2);
                    messageChat2.setText("hello, this is a secret message");
                    messageService.processIncomingContactMessage(messageChat2);
                    TextMessage messageChat1 = new TextMessage();
                    messageChat1.setFromIdentity(contact1.getIdentity());
                    messageChat1.setToIdentity(userService.getIdentity());
                    messageChat1.setDate(new Date());
                    messageChat1.setMessageId(messageIdCrossChat1);
                    messageChat1.setText("> quote #" + messageIdCrossChat2 + "\n\nOMG!");
                    messageService.processIncomingContactMessage(messageChat1);

                    messageService.createStatusMessage("Done creating test quotes", receiver1);

                    return null;
                } catch (Exception e) {
                    return e;
                }
            }

            @Override
            protected void onPostExecute(@Nullable Exception e) {
                if (e == null) {
                    showToast("Test quotes created!");
                } else {
                    showError(e);
                }
            }
        }.execute();
        return true;
    }

    @UiThread
    @SuppressLint("StaticFieldLeak")
    private boolean hideDeveloperMenu(Preference preference) {
        this.preferenceService.setShowDeveloperMenu(false);
        showToast("Not everybody can be a craaazy developer!");
        final Activity activity = this.getActivity();
        if (activity != null) {
            activity.finish();
        }
        return true;
    }

    private void initMdSetting() {
        CheckBoxPreference preference = getPref(R.string.preferences__md_unlocked);
        preference.setEnabled(multiDeviceManager.isMultiDeviceActive() || preferenceService.isMdUnlocked() || BuildConfig.MD_ENABLED);
        preference.setOnPreferenceChangeListener((p, v) -> {
            p.setEnabled((boolean) v || BuildConfig.MD_ENABLED);
            return true;
        });
    }

    private void initConversationSetting() {
        CheckBoxPreference preference = getPref(R.string.preferences__show_last_update_prefix);
    }

    final protected boolean requiredInstances() {
        if (!this.checkInstances()) {
            this.instantiate();
        }
        return this.checkInstances();
    }

    protected boolean checkInstances() {
        return TestUtil.required(
            this.preferenceService,
            this.databaseService,
            this.contactService,
            this.messageService,
            this.userService,
            this.multiDeviceManager
        );
    }

    protected void instantiate() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            try {
                this.preferenceService = serviceManager.getPreferenceService();
                this.databaseService = serviceManager.getDatabaseServiceNew();
                this.contactService = serviceManager.getContactService();
                this.messageService = serviceManager.getMessageService();
                this.userService = serviceManager.getUserService();
                this.multiDeviceManager = serviceManager.getMultiDeviceManager();
                this.apiConnector = serviceManager.getAPIConnector();
                this.contactModelRepository = serviceManager.getModelRepositories().getContacts();
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }

    @Override
    public int getPreferenceTitleResource() {
        return R.string.prefs_developers;
    }

    @Override
    public int getPreferenceResource() {
        return R.xml.preference_developers;
    }
}
