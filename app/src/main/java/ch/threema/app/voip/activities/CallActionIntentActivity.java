/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

package ch.threema.app.voip.activities;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactLookupUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

/**
 * Handle call action intents (sent by calling someone through the Android phonebook),
 * start the call activity.
 */
public class CallActionIntentActivity extends ThreemaActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("CallActionIntentActivity");
    private ServiceManager serviceManager;
    private ContactService contactService;
    private PreferenceService preferenceService;
    private LicenseService licenseService;

    @Override
    protected boolean checkInstances() {
        return TestUtil.required(
            this.serviceManager,
            this.contactService,
            this.preferenceService,
            this.licenseService
        );
    }

    @Override
    protected void instantiate() {
        this.serviceManager = ThreemaApplication.getServiceManager();

        if (this.serviceManager != null) {
            try {
                this.contactService = this.serviceManager.getContactService();
                this.preferenceService = this.serviceManager.getPreferenceService();
                this.licenseService = this.serviceManager.getLicenseService();
            } catch (Exception e) {
                logger.error("Could not instantiate services", e);
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!this.requiredInstances()) {
            this.finish();
            return;
        }

        if (!ConfigUtils.isCallsEnabled() || !licenseService.isLicensed()) {
            Toast.makeText(getApplicationContext(), R.string.voip_disabled, Toast.LENGTH_LONG).show();
            this.finish();
            return;
        }

        //	String contactIdentity = null;
        ContactModel contact = null;

        // Validate intent
        final Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (getString(R.string.call_mime_type).equals(intent.getType())) {
                Uri uri = intent.getData();
                if (uri != null && ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
                    try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToNext()) {
                            String contactIdentity = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.Data.DATA1));
                            contact = contactService.getByIdentity(contactIdentity);
                        }
                    } catch (SecurityException e) {
                        logger.error("SecurityException", e);
                    }
                }
            }
        } else if (Intent.ACTION_CALL.equals(intent.getAction())) {
            final Uri uri = intent.getData();
            if (uri != null && "tel".equals(uri.getScheme())) {
                // Look up contact identity
                contact = ContactLookupUtil.phoneNumberToContact(this, contactService, uri.getSchemeSpecificPart());
            }
        }

        if (contact == null) {
            Toast.makeText(this, R.string.voip_contact_not_found, Toast.LENGTH_LONG).show();
            logger.warn("Invalid call intent: Contact not found");
            finish();
            return;
        }

        logger.info("Calling {} via call intent action", contact.getIdentity());

        if (!VoipUtil.initiateCall(this, contact, false, this::finish)) {
            finish();
        }
    }
}
