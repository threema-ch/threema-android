/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.services;

import android.accounts.Account;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.ContentResolver;
import android.content.Context;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.collections.Functional;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.routines.UpdateBusinessAvatarRoutine;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.ContactUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ModelDeletedException;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.storage.models.ContactModel;
import okhttp3.OkHttpClient;

public class SynchronizeContactsServiceImpl implements SynchronizeContactsService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SynchronizeContactsServiceImpl");

    private final ContentResolver contentResolver;
    private final APIConnector apiConnector;
    private final ContactService contactService;
    private final @NonNull ContactModelRepository contactModelRepository;
    private final UserService userService;
    private final LocaleService localeService;
    private final IdentityStore identityStore;

    private final List<SynchronizeContactsRoutine> pendingRoutines = new ArrayList<>();
    @NonNull
    private final ExcludedSyncIdentitiesService excludedSyncIdentityListService;
    private final PreferenceService preferenceService;
    private final DeviceService deviceService;
    private final Context context;
    private final FileService fileService;
    private final BlockedIdentitiesService blockedIdentitiesService;
    private final ApiService apiService;
    @NonNull
    private final OkHttpClient okHttpClient;

    public SynchronizeContactsServiceImpl(
        Context context, APIConnector apiConnector,
        ContactService contactService,
        @NonNull ContactModelRepository contactModelRepository,
        UserService userService,
        LocaleService localeService,
        @NonNull ExcludedSyncIdentitiesService excludedSyncIdentityListService,
        PreferenceService preferenceService,
        DeviceService deviceService,
        FileService fileService,
        IdentityStore identityStore,
        @NonNull BlockedIdentitiesService blockedIdentitiesService,
        ApiService apiService,
        @NonNull OkHttpClient okHttpClient
    ) {
        this.excludedSyncIdentityListService = excludedSyncIdentityListService;
        this.preferenceService = preferenceService;
        this.deviceService = deviceService;
        this.context = context;
        this.fileService = fileService;
        this.contentResolver = context.getContentResolver();
        this.apiConnector = apiConnector;
        this.contactService = contactService;
        this.contactModelRepository = contactModelRepository;
        this.userService = userService;
        this.localeService = localeService;
        this.identityStore = identityStore;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.apiService = apiService;
        this.okHttpClient = okHttpClient;
    }

    @Override
    public boolean instantiateSynchronizationAndRun() {
        final SynchronizeContactsRoutine sync = this.instantiateSynchronization();

        if (sync != null) {
            if (this.deviceService != null && this.deviceService.isOnline()) {
                sync.addOnFinished((success, modifiedAccounts, createdContacts, deletedAccounts) ->
                    // let user know that contact was added
                    ListenerManager.newSyncedContactListener.handle(listener -> listener.onNew(createdContacts))
                );

                new Thread(() -> {
                    try {
                        sync.run();
                    } catch (SecurityException exception) {
                        logger.error("Could not run contact sync", exception);
                        return;
                    }

                    //get all business accounts
                    //disable contact changed event handler
                    boolean enableState = ListenerManager.contactListeners.isEnabled();
                    try {
                        if (enableState) {
                            ListenerManager.contactListeners.enabled(false);
                        }

                        for (ContactModel contactModel : contactService.getAll()) {
                            if (ContactUtil.isGatewayContact(contactModel)) {
                                ch.threema.data.models.ContactModel model = contactModelRepository.getByIdentity(contactModel.getIdentity());
                                if (model == null) {
                                    logger.error("Could not get contact model with identity {}", contactModel.getIdentity());
                                    continue;
                                }
                                UpdateBusinessAvatarRoutine.start(
                                    okHttpClient,
                                    model,
                                    fileService,
                                    contactService,
                                    apiService,
                                    true
                                );
                            }
                        }
                        //fore update business account avatars
                    } catch (Exception x) {
                        //log exception and ignore
                        logger.error("Ignoring exception", x);
                    } finally {
                        //enable contact listener again
                        ListenerManager.contactListeners.enabled(enableState);
                    }


                }, "SynchronizeContactsRoutine").start();
                return true;
            } else {
                this.finishedRoutine(sync);
            }
        }
        return false;
    }

    @Override
    @Nullable
    public SynchronizeContactsRoutine instantiateSynchronization() {
        return this.instantiateSynchronization(Collections.emptySet());
    }

    @Override
    @Nullable
    public SynchronizeContactsRoutine instantiateSynchronization(@NonNull Set<String> processingIdentities) {
        Account account = this.userService.getAccount();
        if (account == null) {
            logger.error("Not instantiating synchronize contacts routine due to missing account");
            return null;
        }

        logger.info("Running contact sync");

        final SynchronizeContactsRoutine routine =
            new SynchronizeContactsRoutine(
                this.context,
                this.apiConnector,
                this.contactService,
                this.contactModelRepository,
                this.userService,
                this.localeService,
                this.contentResolver,
                this.excludedSyncIdentityListService,
                this.deviceService,
                this.preferenceService,
                this.identityStore,
                this.blockedIdentitiesService,
                processingIdentities
            );

        synchronized (this.pendingRoutines) {
            this.pendingRoutines.add(routine);
        }

        routine.addOnFinished((success, modifiedAccounts, createdContacts, deletedAccounts) -> finishedRoutine(routine));

        ListenerManager.synchronizeContactsListeners.handle(listener -> listener.onStarted(routine));

        return routine;
    }

    @Override
    public boolean isSynchronizationInProgress() {
        return !this.pendingRoutines.isEmpty();
    }

    @Override
    public boolean isFullSyncInProgress() {
        synchronized (this.pendingRoutines) {
            return Functional.select(this.pendingRoutines, routine -> routine.running() && routine.isFullSync()) != null;
        }
    }

    @Override
    public boolean enableSyncFromLocal() {
        boolean success = false;

        if (this.userService != null) {
            Account account = this.userService.getAccount(true);
            success = account != null;
        }

        if (success && this.preferenceService != null) {
            this.preferenceService.getContactSyncPolicySetting().setFromLocal(true);
        }
        return success;
    }

    @Override
    public boolean disableSyncFromLocal(final Runnable runAfterRemovedAccount) {
        if (this.userService != null) {
            //cancel all syncs!
            synchronized (this.pendingRoutines) {
                for (int n = this.pendingRoutines.size() - 1; n >= 0; n--) {
                    this.pendingRoutines.get(n).abort();
                }
            }

            int numDeleted = AndroidContactUtil.getInstance().deleteAllThreemaRawContacts();
            logger.debug("Deleted {} raw contacts", numDeleted);

            if (!this.userService.removeAccount(new AccountManagerCallback<Boolean>() {
                @Override
                public void run(AccountManagerFuture<Boolean> future) {
                    disableSyncFromLocalFinished(runAfterRemovedAccount);
                }
            })) {
                this.disableSyncFromLocalFinished(runAfterRemovedAccount);
            }
        }
        return true;
    }

    private void disableSyncFromLocalFinished(Runnable run) {
        if (this.preferenceService != null) {
            this.preferenceService.getContactSyncPolicySetting().setFromLocal(false);
        }

        if (contactService != null) {
            contactService.removeAllSystemContactLinks();

            // cleanup / degrade remaining identities that are still server verified
            List<String> identities = contactService.getIdentitiesByVerificationLevel(VerificationLevel.SERVER_VERIFIED);
            if (identities != null && !identities.isEmpty()) {
                for (String identity : identities) {
                    ch.threema.data.models.ContactModel model = contactModelRepository.getByIdentity(identity);
                    if (model != null) {
                        try {
                            model.setVerificationLevelFromLocal(VerificationLevel.UNVERIFIED);
                        } catch (ModelDeletedException e) {
                            logger.info("Could not set verification level because contact {} has been deleted", identity, e);
                        }
                    }
                }
            }
        }

        if (run != null) {
            run.run();
        }
    }

    private void finishedRoutine(final SynchronizeContactsRoutine routine) {
        //remove from pending
        synchronized (this.pendingRoutines) {
            this.pendingRoutines.remove(routine);
        }

        logger.info("Contact sync finished");

        //fire on finished
        ListenerManager.synchronizeContactsListeners.handle(new ListenerManager.HandleListener<SynchronizeContactsListener>() {
            @Override
            public void handle(SynchronizeContactsListener listener) {
                listener.onFinished(routine);
            }
        });
    }
}
