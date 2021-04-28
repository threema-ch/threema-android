/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.preference.Preference;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.TestUtil;
import ch.threema.client.BoxTextMessage;
import ch.threema.client.MessageId;
import ch.threema.client.voip.VoipCallAnswerData;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;

public class SettingsDeveloperFragment extends ThreemaPreferenceFragment {
	private static final Logger logger = LoggerFactory.getLogger(SettingsDeveloperFragment.class);

	// Test identities.
	private static final String TEST_IDENTITY_1 = "ADDRTCNX";
	private static final String TEST_IDENTITY_2 = "H6AXSHKC";

	private PreferenceService preferenceService;
	private DatabaseServiceNew databaseService;
	private ContactService contactService;
	private MessageService messageService;
	private UserService userService;

	@Override
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
		if (!requiredInstances()) {
			return;
		}

		addPreferencesFromResource(R.xml.preference_developers);

		// Generate VoIP messages
		final Preference generateVoipPreference = findPreference(getResources().getString(R.string.preferences__generate_voip_messages));
		generateVoipPreference.setSummary("Create the test identity " + TEST_IDENTITY_1
			+ " and add all possible VoIP messages to that conversation.");
		generateVoipPreference.setOnPreferenceClickListener(this::generateVoipMessages);

		// Generate test quotes
		final Preference generateRecursiveQuote = findPreference(getResources().getString(R.string.preferences__generate_test_quotes));
		generateRecursiveQuote.setSummary("Create the test identities " + TEST_IDENTITY_1 + " and "
			+ TEST_IDENTITY_2 + " and add some test quotes.");
		generateRecursiveQuote.setOnPreferenceClickListener(this::generateTestQuotes);

		// Remove developer menu
		final Preference removeMenuPreference = findPreference(getResources().getString(R.string.preferences__remove_menu));
		removeMenuPreference.setSummary("Hide the developer menu from the settings.");
		removeMenuPreference.setOnPreferenceClickListener(this::hideDeveloperMenu);
	}

	@UiThread
	private void showOk(CharSequence msg) {
		Toast.makeText(this.getContext(), msg, Toast.LENGTH_LONG).show();
	}

	@UiThread
	private void showError(Exception e) {
		logger.error("Exception", e);
		Toast.makeText(this.getContext(), e.toString(), Toast.LENGTH_LONG).show();
	}

	@WorkerThread
	private ContactModel createTestContact(
		String identity,
		String firstName,
		String lastName
	) throws EntryAlreadyExistsException, InvalidEntryException, PolicyViolationException {
		ContactModel contact = contactService.getByIdentity(identity);
		if (contact == null) {
			contact = contactService.createContactByIdentity(identity, true);
		}
		contact.setName(firstName, lastName);
		databaseService.getContactModelFactory().createOrUpdate(contact);
		return contact;
	}

	@UiThread
	@SuppressLint("StaticFieldLeak")
	private boolean generateVoipMessages(Preference preference) {

		// Pojo for holding test data.
		class VoipMessage {
			VoipStatusDataModel dataModel;
			String description;
			VoipMessage(VoipStatusDataModel dataModel, String description) {
				this.dataModel = dataModel;
				this.description = description;
			}
		}

		// Test messages
		final VoipMessage[] testMessages = new VoipMessage[] {
			new VoipMessage(VoipStatusDataModel.createMissed(), "missed"),
			new VoipMessage(VoipStatusDataModel.createFinished(42), "finished"),
			new VoipMessage(VoipStatusDataModel.createRejected(VoipCallAnswerData.RejectReason.UNKNOWN), "rejected (unknown)"),
			new VoipMessage(VoipStatusDataModel.createRejected(VoipCallAnswerData.RejectReason.BUSY), "rejected (busy)"),
			new VoipMessage(VoipStatusDataModel.createRejected(VoipCallAnswerData.RejectReason.TIMEOUT), "rejected (timeout)"),
			new VoipMessage(VoipStatusDataModel.createRejected(VoipCallAnswerData.RejectReason.REJECTED), "rejected (rejected)"),
			new VoipMessage(VoipStatusDataModel.createRejected(VoipCallAnswerData.RejectReason.DISABLED), "rejected (disabled)"),
			new VoipMessage(VoipStatusDataModel.createRejected((byte)99), "rejected (invalid reason code)"),
			new VoipMessage(VoipStatusDataModel.createRejected(null), "rejected (null reason code)"),
			new VoipMessage(VoipStatusDataModel.createAborted(), "aborted"),
		};

		new AsyncTask<Void, Void, Exception>() {
			@Override
			@Nullable protected Exception doInBackground(Void... voids) {
				try {
					// Create test identity
					final ContactModel contact = createTestContact(TEST_IDENTITY_1, "Developer", "Testcontact");

					// Create test messages
					final ContactMessageReceiver receiver = contactService.createReceiver(contact);
					messageService.createStatusMessage("Creating test messages...", receiver);
					for (boolean isOutbox : new boolean[] { true, false }) {
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
					showOk("Test messages created!");
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
			@Nullable protected Exception doInBackground(Void... voids) {
				try {
					// Create test identity
					final ContactModel contact1 = createTestContact(TEST_IDENTITY_1, "Developer", "Testcontact");
					final ContactMessageReceiver receiver1 = contactService.createReceiver(contact1);
					final ContactModel contact2 = createTestContact(TEST_IDENTITY_2, "Another Developer", "Testcontact");
					final ContactMessageReceiver receiver2 = contactService.createReceiver(contact2);

					messageService.createStatusMessage("Creating test quotes...", receiver1);

					// Create recursive quote
					final MessageId messageIdRecursive = new MessageId();
					BoxTextMessage messageRecursive = new BoxTextMessage();
					messageRecursive.setFromIdentity(contact1.getIdentity());
					messageRecursive.setToIdentity(userService.getIdentity());
					messageRecursive.setDate(new Date());
					messageRecursive.setMessageId(messageIdRecursive);
					messageRecursive.setText("> quote #" + messageIdRecursive.toString() + "\n\na quote that references itself");
					messageService.processIncomingContactMessage(messageRecursive);

					// Create cross-chat quote
					final MessageId messageIdCrossChat1 = new MessageId();
					final MessageId messageIdCrossChat2 = new MessageId();
					BoxTextMessage messageChat2 = new BoxTextMessage();
					messageChat2.setFromIdentity(contact2.getIdentity());
					messageChat2.setToIdentity(userService.getIdentity());
					messageChat2.setDate(new Date());
					messageChat2.setMessageId(messageIdCrossChat2);
					messageChat2.setText("hello, this is a secret message");
					messageService.processIncomingContactMessage(messageChat2);
					BoxTextMessage messageChat1 = new BoxTextMessage();
					messageChat1.setFromIdentity(contact1.getIdentity());
					messageChat1.setToIdentity(userService.getIdentity());
					messageChat1.setDate(new Date());
					messageChat1.setMessageId(messageIdCrossChat1);
					messageChat1.setText("> quote #" + messageIdCrossChat2.toString() + "\n\nOMG!");
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
					showOk("Test quotes created!");
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
		this.showOk("Not everybody can be a craaazy developer!");
		final Activity activity = this.getActivity();
		if (activity != null) {
			activity.finish();
		}
		return true;
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
			this.userService
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
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		preferenceFragmentCallbackInterface.setToolbarTitle(R.string.prefs_developers);
		super.onViewCreated(view, savedInstanceState);
	}
}
