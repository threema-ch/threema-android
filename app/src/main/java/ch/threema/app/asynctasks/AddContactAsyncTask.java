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

package ch.threema.app.asynctasks;

import android.os.AsyncTask;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.EntryAlreadyExistsException;
import ch.threema.app.exceptions.InvalidEntryException;
import ch.threema.app.exceptions.PolicyViolationException;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.storage.models.ContactModel;

public class AddContactAsyncTask extends AsyncTask<Void, Void, Boolean> {
    private static final Logger logger = LoggingUtil.getThreemaLogger("AddContactAsyncTask");

    private final Runnable runOnCompletion;
    private final String firstName, lastName, threemaId;
    private final boolean markAsWorkVerified;

    public AddContactAsyncTask(String firstname, String lastname, String identity, boolean markAsWorkVerified, Runnable runOnCompletion) {
        this.firstName = firstname;
        this.lastName = lastname;
        this.threemaId = identity.toUpperCase();
        this.runOnCompletion = runOnCompletion;
        this.markAsWorkVerified = markAsWorkVerified;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            return addContact(ThreemaApplication.requireServiceManager().getContactService());
        } catch (Exception e) {
            logger.error("Could not add contact", e);
            return null;
        }
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
            Toast.makeText(ThreemaApplication.getAppContext(), R.string.add_contact_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean addContact(@NonNull ContactService contactService) throws InvalidEntryException, PolicyViolationException, EntryAlreadyExistsException {
        if (contactService.getByIdentity(this.threemaId) != null) {
            logger.info("Contact already exists");
            return false;
        }

        boolean force = (ConfigUtils.isOnPremBuild() || ConfigUtils.isWorkBuild()) && markAsWorkVerified;

        ContactModel contactModel = contactService.createContactByIdentity(this.threemaId, force);

        if (this.firstName != null && this.lastName != null) {
            contactModel.setFirstName(this.firstName);
            contactModel.setLastName(this.lastName);
            contactService.save(contactModel);
        }

        if (contactModel.getIdentityType() == IdentityType.WORK || markAsWorkVerified) {
            contactModel.setIsWork(true);

            if(contactModel.verificationLevel != VerificationLevel.FULLY_VERIFIED) {
                contactModel.verificationLevel = VerificationLevel.SERVER_VERIFIED;
            }
            contactService.save(contactModel);
        }
        return true;
    }
}
