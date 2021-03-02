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

package ch.threema.app.asynctasks;

import android.os.AsyncTask;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.base.VerificationLevel;
import ch.threema.client.IdentityType;
import ch.threema.storage.models.ContactModel;

public class AddContactAsyncTask extends AsyncTask<Void, Void, Boolean> {
	private static final Logger logger = LoggerFactory.getLogger(AddContactAsyncTask.class);

	private ContactService contactService;
	private final Runnable runOnCompletion;
	private final String firstName, lastName, threemaId;
	private final boolean markAsWorkVerified;

	public AddContactAsyncTask(String firstname, String lastname, String identity, boolean markAsWorkVerified, Runnable runOnCompletion) {
		this.firstName = firstname;
		this.lastName = lastname;
		this.threemaId = identity.toUpperCase();
		this.runOnCompletion = runOnCompletion;
		this.markAsWorkVerified = markAsWorkVerified;

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		try {
			this.contactService = serviceManager.getContactService();
		} catch (Exception e) {
			//
		}
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		if (this.contactService == null) {
			logger.error("ContactService not available");
			return null;
		}

		if (this.contactService.getByIdentity(this.threemaId) == null) {
			ContactModel contactModel;
			try {
				contactModel = contactService.createContactByIdentity(this.threemaId, false);

				if (this.firstName != null && this.lastName != null) {
					contactModel.setFirstName(this.firstName);
					contactModel.setLastName(this.lastName);
					contactService.save(contactModel);
				}

				if (contactModel.getType() == IdentityType.WORK || markAsWorkVerified) {
					contactModel.setIsWork(true);

					if(contactModel.getVerificationLevel() != VerificationLevel.FULLY_VERIFIED) {
						contactModel.setVerificationLevel(VerificationLevel.SERVER_VERIFIED);
					}
					contactService.save(contactModel);
				}
				return true;
			} catch (Exception e) {
				return null;
			}
		}
		// contact already exists
		return false;
	}

	@Override
	protected void onPostExecute(Boolean added) {
		if (added != null) {
			if (added) {
				Toast.makeText(ThreemaApplication.getAppContext(), R.string.creating_contact_successful, Toast.LENGTH_SHORT).show();
			}

			if (runOnCompletion != null) {
				runOnCompletion.run();
			}
		} else {
			Toast.makeText(ThreemaApplication.getAppContext(), R.string.contact_not_found, Toast.LENGTH_SHORT).show();
		}
	}
}
