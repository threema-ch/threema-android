/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.listeners.NewSyncedContactsListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.routines.UpdateBusinessAvatarRoutine;
import ch.threema.app.utils.ContactUtil;
import ch.threema.client.APIConnector;
import ch.threema.client.IdentityStoreInterface;
import ch.threema.storage.models.ContactModel;

public class SynchronizeContactsServiceImpl implements SynchronizeContactsService {
	private static final Logger logger = LoggerFactory.getLogger(SynchronizeContactsServiceImpl.class);

	private final ContentResolver contentResolver;
	private final APIConnector apiConnector;
	private final ContactService contactService;
	private final UserService userService;
	private final LocaleService localeService;
	private final IdentityStoreInterface identityStore;

	private final List<SynchronizeContactsRoutine> pendingRoutines = new ArrayList<SynchronizeContactsRoutine>();
	private final IdListService excludedIdentityListService;
	private final PreferenceService preferenceService;
	private final DeviceService deviceService;
	private final Context context;
	private FileService fileService;

	private Date latestFullSync;

	public SynchronizeContactsServiceImpl(Context context, APIConnector apiConnector,
										  ContactService contactService,
										  UserService userService,
										  LocaleService localeService,
										  IdListService excludedIdentityListService,
										  PreferenceService preferenceService,
										  DeviceService deviceService,
										  FileService fileService,
	                                      IdentityStoreInterface identityStore) {
		this.excludedIdentityListService = excludedIdentityListService;
		this.preferenceService = preferenceService;
		this.deviceService = deviceService;
		this.context = context;
		this.fileService = fileService;
		this.contentResolver = context.getContentResolver();
		this.apiConnector = apiConnector;
		this.contactService = contactService;
		this.userService = userService;
		this.localeService = localeService;
		this.identityStore = identityStore;
	}

	@Override
	public boolean instantiateSynchronizationAndRun() {
		final SynchronizeContactsRoutine sync = this.instantiateSynchronization();

		if (sync != null) {
			if(this.deviceService != null && this.deviceService.isOnline()) {
				sync.addOnFinished(new SynchronizeContactsRoutine.OnFinished() {
					@Override
					public void finished(boolean success, long modifiedAccounts, List<ContactModel> createdContacts, long deletedAccounts) {
						// let user know that contact was added
						ListenerManager.newSyncedContactListener.handle(new ListenerManager.HandleListener<NewSyncedContactsListener>() {
							@Override
							public void handle(NewSyncedContactsListener listener) {
								listener.onNew(createdContacts);
							}
						});
					}
				});

				new Thread(new Runnable() {
					@Override
					public void run() {
						sync.run();

						//get all business accounts
						//disable contact changed event handler
						boolean enableState = ListenerManager.contactListeners.isEnabled();
						try {
							if(enableState) {
								ListenerManager.contactListeners.enabled(false);
							}

							for (ContactModel contactModel : contactService.getAll(true, true)) {
								if (ContactUtil.isChannelContact(contactModel)) {
									UpdateBusinessAvatarRoutine.start(contactModel, fileService, contactService, true);
								}
							}
							//fore update business account avatars
						}
						catch (Exception x) {
							//log exception and ignore
							logger.error("Ignoring exception", x);
						}
						finally {
							//enable contact listener again
							ListenerManager.contactListeners.enabled(enableState);
						}


					}
				}, "SynchronizeContactsRoutine").start();
				return true;
			}
			else {
				this.finishedRoutine(sync);
			}
		}
		return false;
	}

	@Override
	public SynchronizeContactsRoutine instantiateSynchronization() {
		Account account = this.userService.getAccount();
		if(account != null) {
			return this.instantiateSynchronization(account);
		}
		return null;
	}


	@Override
	public SynchronizeContactsRoutine instantiateSynchronization(Account account) {
		logger.info("Running contact sync");
		logger.debug("instantiateSynchronization with account {}", account);
		final SynchronizeContactsRoutine routine =
				new SynchronizeContactsRoutine(
						this.context,
						this.apiConnector,
						this.contactService,
						this.userService,
						this.localeService,
						this.contentResolver,
						this.excludedIdentityListService,
						this.deviceService,
						this.preferenceService,
						this.identityStore);

		synchronized (this.pendingRoutines) {
			this.pendingRoutines.add(routine);
		}

		routine.addOnFinished(new SynchronizeContactsRoutine.OnFinished() {
			@Override
			public void finished(boolean success, long modifiedAccounts, List<ContactModel> createdContacts, long deletedAccounts) {
				finishedRoutine(routine);
			}
		});

		routine.addOnStarted(new SynchronizeContactsRoutine.OnStarted() {
			@Override
			public void started(boolean fullSync) {
				if(fullSync) {
					latestFullSync = new Date();
				}
			}
		});

		ListenerManager.synchronizeContactsListeners.handle(new ListenerManager.HandleListener<SynchronizeContactsListener>() {
			@Override
			public void handle(SynchronizeContactsListener listener) {
				listener.onStarted(routine);
			}
		});

		return routine;
	}

	@Override
	public boolean isSynchronizationInProgress() {
		return this.pendingRoutines.size() > 0;
	}

	@Override
	public Date getLatestFullSyncTime() {
		return this.latestFullSync;
	}

	@Override
	public boolean isFullSyncInProgress() {
		synchronized (this.pendingRoutines) {
			return Functional.select(this.pendingRoutines, new IPredicateNonNull<SynchronizeContactsRoutine>() {
				@Override
				public boolean apply(@NonNull SynchronizeContactsRoutine routine) {
					return routine != null && routine.running() && routine.fullSync();
				}
			}) != null;
		}
	}

	@Override
	public boolean enableSync() {
		boolean success = false;

		if(this.userService != null) {
			Account account = this.userService.getAccount(true);
			success = account != null;
		}

		if(success && this.preferenceService != null) {
			this.preferenceService.setSyncContacts(true);
		}
		return success;
	}

	@Override
	public boolean disableSync(final Runnable runAfterRemovedAccount) {
		if(this.userService != null) {
			//cancel all syncs!
			synchronized (this.pendingRoutines) {
				for(int n = this.pendingRoutines.size()-1; n >= 0 ; n--) {
					this.pendingRoutines.get(n).abort();
				}
			}
			if(!this.userService.removeAccount(new AccountManagerCallback<Boolean>() {
				@Override
				public void run(AccountManagerFuture<Boolean> future) {
					disableSyncFinished(runAfterRemovedAccount);
				}
			})) {
				this.disableSyncFinished(runAfterRemovedAccount);
			}
		}
		return true;
	}

	private void disableSyncFinished(Runnable run) {
		if(this.preferenceService != null) {
			this.preferenceService.setSyncContacts(false);
		}

		if(contactService != null) {
			contactService.removeAllThreemaContactIds();
		}

		if(run != null) {
			run.run();
		}
	}

	@Override
	public boolean disableSync() {
		return this.disableSync(null);
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
