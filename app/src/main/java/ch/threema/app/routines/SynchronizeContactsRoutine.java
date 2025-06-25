/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.routines;

import android.Manifest;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import com.google.common.collect.ListMultimap;

import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.threema.app.asynctasks.AddContactBackgroundTask;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.ExcludedSyncIdentitiesService;
import ch.threema.app.services.LocaleService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.stores.MatchTokenStore;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.models.ContactSyncState;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.ReadReceiptPolicy;
import ch.threema.domain.models.TypingIndicatorPolicy;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.models.WorkVerificationLevel;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.stores.IdentityStoreInterface;
import ch.threema.storage.models.ContactModel.AcquaintanceLevel;

public class SynchronizeContactsRoutine implements Runnable {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SynchronizeContactsRoutine");

    private final UserService userService;
    private final Context context;
    private final APIConnector apiConnector;
    private final ContactService contactService;
    private final ContactModelRepository contactModelRepository;
    private final LocaleService localeService;
    private final ContentResolver contentResolver;
    @NonNull
    private final ExcludedSyncIdentitiesService excludedSyncIdentitiesService;
    private final DeviceService deviceService;
    private final PreferenceService preferenceService;
    private final IdentityStoreInterface identityStore;
    private final BlockedIdentitiesService blockedIdentitiesService;

    private OnStatusUpdate onStatusUpdate;
    private final List<OnFinished> onFinished = new ArrayList<>();
    private final List<OnStarted> onStarted = new ArrayList<>();

    private final List<String> processingIdentities = new ArrayList<>();
    private boolean abort = false;
    private boolean running = false;

    public interface OnStatusUpdate {
        void newStatus(final long percent, final String message);

        void error(final Exception x);
    }

    public interface OnFinished {
        void finished(boolean success, long modifiedAccounts, List<ContactModel> createdContacts, long deletedAccounts);
    }

    public interface OnStarted {
        void started(boolean fullSync);
    }

    public SynchronizeContactsRoutine(
        Context context,
        APIConnector apiConnector,
        ContactService contactService,
        @NonNull ContactModelRepository contactModelRepository,
        UserService userService,
        LocaleService localeService,
        ContentResolver contentResolver,
        @NonNull ExcludedSyncIdentitiesService excludedSyncIdentitiesService,
        DeviceService deviceService,
        PreferenceService preferenceService,
        IdentityStoreInterface identityStore,
        BlockedIdentitiesService blockedIdentitiesService
    ) {
        this.context = context;
        this.apiConnector = apiConnector;
        this.userService = userService;
        this.contactService = contactService;
        this.contactModelRepository = contactModelRepository;
        this.localeService = localeService;
        this.contentResolver = contentResolver;
        this.excludedSyncIdentitiesService = excludedSyncIdentitiesService;
        this.deviceService = deviceService;
        this.preferenceService = preferenceService;
        this.identityStore = identityStore;
        this.blockedIdentitiesService = blockedIdentitiesService;
    }

    public SynchronizeContactsRoutine addProcessIdentity(String identity) {
        if (!TestUtil.isEmptyOrNull(identity) && !this.processingIdentities.contains(identity)) {
            this.processingIdentities.add(identity);
        }
        return this;
    }

    public void abort() {
        this.abort = true;
    }

    public boolean running() {
        return this.running;
    }

    public boolean fullSync() {
        return this.processingIdentities.isEmpty();
    }

    @Override
    @RequiresPermission(allOf = {Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS})
    public void run() {
        logger.info("SynchronizeContacts run started.");

        if (!ConfigUtils.isPermissionGranted(context, Manifest.permission.WRITE_CONTACTS)) {
            logger.info("No contacts permission. Aborting.");
            return;
        }

        this.running = true;

        for (OnStarted s : this.onStarted) {
            s.started(this.fullSync());
        }

        boolean success = false;

        long deletedCount = 0;
        long modifiedRawContactCount = 0;
        long newRawContactCount = 0;
        List<ContactModel> insertedContacts = new ArrayList<>();

        try {
            if (!this.preferenceService.isSyncContacts()) {
                throw new ThreemaException("Sync is disabled in preferences, not allowed to call synchronizecontacts routine");
            }

            if (this.deviceService != null && !this.deviceService.isOnline()) {
                throw new ThreemaException("No connection");
            }

            //read emails
            final Map<String, ContactMatchKeyEmail> emails = this.readEmails();
            final int emailsHash = emails.keySet().hashCode();

            //read phone numbers
            final Map<String, ContactMatchKeyPhone> phoneNumbers = this.readPhoneNumbers();
            final int phoneNumbersHash = phoneNumbers.keySet().hashCode();

            if (preferenceService.getEmailSyncHashCode() == emailsHash
                && preferenceService.getPhoneNumberSyncHashCode() == phoneNumbersHash
                && (preferenceService.getTimeOfLastContactSync() + (DateUtils.HOUR_IN_MILLIS * 23)) > System.currentTimeMillis()) {
                logger.info("System contacts are unchanged or grace time not yet reached. Not syncing.");
                success = true;
                return;
            }

            preferenceService.setEmailSyncHashCode(emailsHash);
            preferenceService.setPhoneNumberSyncHashCode(phoneNumbersHash);
            preferenceService.setTimeOfLastContactSync(System.currentTimeMillis());

            logger.info("Attempting to sync contacts {} - {}", emails.size(), phoneNumbers.size());

            //send hashes to server and get result
            MatchTokenStore matchTokenStore = new MatchTokenStore(this.preferenceService);
            Map<String, APIConnector.MatchIdentityResult> foundIds = this.apiConnector.matchIdentities(
                emails, phoneNumbers, this.localeService.getCountryIsoCode(), false, identityStore, matchTokenStore);

            // remove own ID
            if (userService != null) {
                foundIds.remove(userService.getIdentity());
            }

            final List<String> preSynchronizedIdentities = new ArrayList<>();
            if (this.fullSync()) {
                List<String> synchronizedIdentities = this.contactService.getSynchronizedIdentities();
                if (synchronizedIdentities != null) {
                    preSynchronizedIdentities.addAll(synchronizedIdentities);
                }
            }

            ListMultimap<String, AndroidContactUtil.RawContactInfo> existingRawContacts = AndroidContactUtil.getInstance().getAllThreemaRawContacts();
            if (existingRawContacts == null) {
                throw new ThreemaException("No permission or no account");
            }
            logger.info("Number of existing raw contacts {}", existingRawContacts.size());

            //looping result and create/update contacts
            logger.info("Number of IDs matching phone or email {}", foundIds.size());

            ArrayList<ContentProviderOperation> contentProviderOperations = new ArrayList<>();
            for (Map.Entry<String, APIConnector.MatchIdentityResult> id : foundIds.entrySet()) {
                boolean isNewContact = false;

                if (this.abort) {
                    //abort!
                    for (OnFinished f : this.onFinished) {
                        f.finished(false, modifiedRawContactCount, insertedContacts, deletedCount);
                    }
                    return;
                }

                // Do not sync contacts on exclude list
                if (this.excludedSyncIdentitiesService.isExcluded(id.getKey())) {
                    logger.info("Identity {} is on exclude list", id.getKey());
                    continue;
                }

                if (!this.processingIdentities.isEmpty() && !this.processingIdentities.contains(id.getKey())) {
                    continue;
                }

                if (this.fullSync()) {
                    // remove if list contains this key
                    preSynchronizedIdentities.remove(id.getKey());
                }

                final ContactMatchKeyEmail matchKeyEmail = (ContactMatchKeyEmail) id.getValue().refObjectEmail;
                final ContactMatchKeyPhone matchKeyPhone = (ContactMatchKeyPhone) id.getValue().refObjectMobileNo;

                long contactId;
                String lookupKey;
                if (matchKeyEmail != null) {
                    contactId = matchKeyEmail.contactId;
                    lookupKey = matchKeyEmail.lookupKey;
                } else {
                    contactId = matchKeyPhone.contactId;
                    lookupKey = matchKeyPhone.lookupKey;
                }

                //try to get the contact
                ContactModel contact = contactModelRepository.getByIdentity(
                    id.getKey()
                );
                ContactModelData data = contact != null ? contact.getData().getValue() : null;
                //contact does not exist, create a new one
                if (contact == null || data == null) {
                    APIConnector.MatchIdentityResult result = id.getValue();
                    ContactModelData contactModelData = ContactModelData.javaCreate(
                        id.getKey(),
                        result.publicKey,
                        new Date(),
                        "",
                        "",
                        null,
                        ContactModelData.getIdColorIndexInt(id.getKey()),
                        VerificationLevel.SERVER_VERIFIED,
                        WorkVerificationLevel.NONE,
                        IdentityType.NORMAL,   // TODO(ANDR-3044): Fetch identity type
                        AcquaintanceLevel.DIRECT,
                        IdentityState.ACTIVE,  // TODO(ANDR-3044): Fetch identity state
                        BigInteger.valueOf(0), // TODO(ANDR-3044): Fetch feature mask
                        ContactSyncState.IMPORTED,
                        ReadReceiptPolicy.DEFAULT,
                        TypingIndicatorPolicy.DEFAULT,
                        false,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null
                    );
                    contact = new AddContactBackgroundTask(contactModelData, contactModelRepository)
                        .runSynchronously();

                    if (contact == null) {
                        logger.error("Could not create contact with identity {}", id.getKey());
                        continue;
                    }

                    data = contact.getData().getValue();
                    if (data == null) {
                        logger.error("Contact data is null");
                        continue;
                    }

                    insertedContacts.add(contact);

                    isNewContact = true;
                    logger.info("Inserting new Threema contact {}", id.getKey());
                }
                contact.setAndroidLookupKey(lookupKey + "/" + contactId);

                try {
                    boolean createNewRawContact;

                    AndroidContactUtil.getInstance().updateNameByAndroidContact(contact); // throws an exception if no name can be determined
                    AndroidContactUtil.getInstance().updateAvatarByAndroidContact(contact);

                    contact.setAcquaintanceLevelFromLocal(AcquaintanceLevel.DIRECT);
                    if (data.verificationLevel == VerificationLevel.UNVERIFIED) {
                        contact.setVerificationLevelFromLocal(VerificationLevel.SERVER_VERIFIED);
                    }

                    List<AndroidContactUtil.RawContactInfo> rawContactInfos = existingRawContacts.get(contact.getIdentity());

                    if (rawContactInfos.isEmpty()) {
                        // raw contact does not exist yet, create it
                        createNewRawContact = true;
                        newRawContactCount++;
                    } else {
                        // a raw contact exists - check if it points to the correct parent
                        createNewRawContact = true;
                        for (AndroidContactUtil.RawContactInfo rawContactInfo : rawContactInfos) {
                            if (rawContactInfo.contactId > 0L && rawContactInfo.contactId == contactId) {
                                // all good - no change necessary
                                createNewRawContact = false;
                                existingRawContacts.remove(contact.getIdentity(), rawContactInfo);
                                break;
                            }
                        }
                        if (createNewRawContact) {
                            modifiedRawContactCount++;
                        }
                    }

                    if (createNewRawContact) {
                        boolean supportsVoiceCalls = ContactUtil.canReceiveVoipMessages(contact.getIdentity(), this.blockedIdentitiesService)
                            && ConfigUtils.isCallsEnabled();

                        // create a raw contact for our stuff and aggregate it
                        AndroidContactUtil.getInstance().createThreemaRawContact(
                            contentProviderOperations,
                            matchKeyEmail != null ?
                                matchKeyEmail.rawContactId :
                                matchKeyPhone.rawContactId,
                            contact.getIdentity(),
                            supportsVoiceCalls);
                    }
                } catch (ThreemaException e) {
                    if (isNewContact) {
                        // probably not a valid contact
                        insertedContacts.remove(contact);
                        logger.info("Ignore Threema contact {} due to missing name", id.getKey());
                    }
                    logger.error("Contact lookup Exception", e);
                }
            }

            if (!contentProviderOperations.isEmpty()) {
                try {
                    ConfigUtils.applyToContentResolverInBatches(
                        ContactsContract.AUTHORITY,
                        contentProviderOperations);
                } catch (Exception e) {
                    logger.error("Error during raw contact creation! ", e);
                }
                contentProviderOperations.clear();
            }

            if (this.fullSync()) {
                // delete remaining / stray raw contacts
                if (!existingRawContacts.isEmpty()) {
                    AndroidContactUtil.getInstance().deleteThreemaRawContacts(existingRawContacts);
                    logger.info("Deleted {} stray raw contacts", existingRawContacts.size());
                }

                if (!preSynchronizedIdentities.isEmpty()) {
                    logger.info("Found {} synchronized contacts that are no longer synchronized", preSynchronizedIdentities.size());

                    for (String identity : preSynchronizedIdentities) {
                        ContactModel contactModel = contactModelRepository.getByIdentity(identity);
                        if (contactModel != null) {
                            contactModel.removeAndroidContactLink();
                        }
                    }
                }
            }
            success = true;
        } catch (final Exception x) {
            logger.error("Exception", x);
            if (this.onStatusUpdate != null) {
                this.onStatusUpdate.error(x);
            }
        } finally {
            logger.info("Finished [success = {}, inserted = {}, deleted = {}, modifiedRawContacts = {}, newRawContacts = {}] fullSync = {}", success, insertedContacts.size(), deletedCount, modifiedRawContactCount, newRawContactCount, this.fullSync());
            for (OnFinished f : this.onFinished) {
                f.finished(success, modifiedRawContactCount, insertedContacts, deletedCount);
            }
            this.running = false;
        }
    }

    public SynchronizeContactsRoutine setOnStatusUpdate(OnStatusUpdate onStatusUpdate) {
        this.onStatusUpdate = onStatusUpdate;
        return this;
    }

    public SynchronizeContactsRoutine addOnFinished(OnFinished onFinished) {
        this.onFinished.add(onFinished);
        return this;
    }

    public SynchronizeContactsRoutine addOnStarted(OnStarted onStarted) {
        this.onStarted.add(onStarted);
        return this;
    }

    private Map<String, ContactMatchKeyPhone> readPhoneNumbers() {
        Map<String, ContactMatchKeyPhone> phoneNumbers = new HashMap<>();
        String selection;

        selection = ContactsContract.Contacts.HAS_PHONE_NUMBER + " > 0 AND " + ContactsContract.CommonDataKinds.Phone.IN_DEFAULT_DIRECTORY + " = 1";

        try (Cursor phonesCursor = this.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            },
            selection,
            null,
            null)) {

            if (phonesCursor != null && phonesCursor.getCount() > 0) {
                final int rawContactIdIndex = phonesCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID);
                final int idColumnIndex = phonesCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                final int lookupKeyColumnIndex = phonesCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY);
                final int phoneNumberIndex = phonesCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (phonesCursor.moveToNext()) {
                    long rawContactId = phonesCursor.getLong(rawContactIdIndex);
                    long contactId = phonesCursor.getLong(idColumnIndex);
                    String lookupKey = phonesCursor.getString(lookupKeyColumnIndex);
                    String phoneNumber = phonesCursor.getString(phoneNumberIndex);

                    if (rawContactId > 0L && contactId > 0L && lookupKey != null && !TestUtil.isEmptyOrNull(phoneNumber)) {
                        ContactMatchKeyPhone matchKey = new ContactMatchKeyPhone();
                        matchKey.contactId = contactId;
                        matchKey.lookupKey = lookupKey;
                        matchKey.rawContactId = rawContactId;
                        matchKey.phoneNumber = phoneNumber;
                        phoneNumbers.put(phoneNumber, matchKey);
                    }
                }
            }
        }

        return phoneNumbers;
    }

    private Map<String, ContactMatchKeyEmail> readEmails() {
        Map<String, ContactMatchKeyEmail> emails = new HashMap<>();

        String selection = ContactsContract.CommonDataKinds.Email.IN_DEFAULT_DIRECTORY + " = 1";

        try (Cursor emailsCursor = this.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            new String[]{
                ContactsContract.CommonDataKinds.Email.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Email.DATA
            },
            selection,
            null,
            null)) {

            if (emailsCursor != null && emailsCursor.getCount() > 0) {
                final int rawContactIdIndex = emailsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.RAW_CONTACT_ID);
                final int idColumnIndex = emailsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID);
                final int lookupKeyColumnIndex = emailsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.LOOKUP_KEY);
                final int emailIndex = emailsCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);

                while (emailsCursor.moveToNext()) {
                    long rawContactId = emailsCursor.getLong(rawContactIdIndex);
                    long contactId = emailsCursor.getLong(idColumnIndex);
                    String lookupKey = emailsCursor.getString(lookupKeyColumnIndex);
                    String email = emailsCursor.getString(emailIndex);

                    if (rawContactId > 0L && contactId > 0L && lookupKey != null && !TestUtil.isEmptyOrNull(email)) {
                        ContactMatchKeyEmail matchKey = new ContactMatchKeyEmail();
                        matchKey.contactId = contactId;
                        matchKey.lookupKey = lookupKey;
                        matchKey.rawContactId = rawContactId;
                        matchKey.email = email;
                        emails.put(email, matchKey);
                    }
                }
            }
        }

        return emails;
    }

    private static class ContactMatchKey {
        long contactId;
        String lookupKey;
        long rawContactId;
    }

    private static class ContactMatchKeyEmail extends ContactMatchKey {
        String email;
    }

    private static class ContactMatchKeyPhone extends ContactMatchKey {
        String phoneNumber;
    }
}
