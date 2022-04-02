/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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

package ch.threema.app.workers;

import android.content.Context;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PollingHelper;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ContactUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.storage.models.ContactModel;

public class IdentityStatesWorker extends Worker {
	private static final Logger logger = LoggingUtil.getThreemaLogger("IdentityStatesWorker");

	private ContactService contactService;
	private APIConnector apiConnector;
	private PreferenceService preferenceService;
	private NotificationService notificationService;
	private PollingHelper pollingHelper = null;

	public IdentityStatesWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
		super(context, workerParams);

		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			contactService = serviceManager.getContactService();
			apiConnector = serviceManager.getAPIConnector();
			preferenceService = serviceManager.getPreferenceService();
			notificationService = serviceManager.getNotificationService();
		} catch (Exception e) {
			//
		}
	}

	@NonNull
	@Override
	public Result doWork() {
		logger.info("Starting IdentityStatesWorker");

		if (this.contactService == null) {
			logger.info("ContactService not available while updating IdentityStates");
			return Result.failure();
		}

		if (notificationService != null) {
			notificationService.showIdentityStatesSyncProgress();
		}

		//get all identities
		//get ALL, no filter set!
		final List<ContactModel> contactModelList = this.contactService.find(new ContactService.Filter() {
			@Override
			public ContactModel.State[] states() {
				//do not process invalid or deleted ids
				return new ContactModel.State[] {
						ContactModel.State.ACTIVE,
						ContactModel.State.INACTIVE
				};
			}

			@Override
			public Integer requiredFeature() {
				return null;
			}

			@Override
			public Boolean fetchMissingFeatureLevel() {
				return null;
			}

			@Override
			public Boolean includeMyself() {
				return true;
			}

			@Override
			public Boolean includeHidden() {
				return true;
			}

			@Override
			public Boolean onlyWithReceiptSettings() {
				return false;
			}
		});

		if (contactModelList != null && contactModelList.size() > 0) {
			//create identity array
			String[] identities = new String[contactModelList.size()];
			Map<String, Integer> contactMap = new HashMap<>();
			for (int n = 0; n < contactModelList.size(); n++) {
				ContactModel contactModel = contactModelList.get(n);
				contactMap.put(contactModel.getIdentity(), n);
				identities[n] = contactModel.getIdentity();
			}

			try {
				APIConnector.CheckIdentityStatesResult res = this.apiConnector.checkIdentityStates(identities);

				logger.trace("identityStates checkInterval = " + res.checkInterval);

				for (int n = 0; n < res.identities.length; n++) {
					String identity = res.identities[n];
					int state = res.states[n];
					Integer featureMask = res.featureMasks[n];

					if (contactMap.containsKey(identity)) {
						ContactModel contactModel = contactModelList.get(contactMap.get(identity));
						if (contactModel != null) {
							ContactModel.State contactModelState = null;
							switch (state) {
								case IdentityState.ACTIVE:
									contactModelState = ContactModel.State.ACTIVE;
									break;
								case IdentityState.INACTIVE:
									contactModelState = ContactModel.State.INACTIVE;
									break;
								case IdentityState.INVALID:
									contactModelState = ContactModel.State.INVALID;
									break;
							}

							boolean save = false;
							if (contactModel.getIdentityType() != res.types[n]) {
								// Set new type
								contactModel.setIdentityType(res.types[n]);
								save = true;
							}
							if (featureMask != null) {
								if (contactModel.getFeatureMask() != featureMask) {
									contactModel.setFeatureMask(featureMask);
									save = true;
								}
							} else {
								logger.warn("Feature mask for contact {} is null.", contactModel.getIdentity());
								// is this a valid contact?
							}
							if (ContactUtil.allowedChangeToState(contactModel, contactModelState)) {
								logger.debug("update {} with state {}", identity, contactModelState);
								contactModel.setState(contactModelState);
								save = true;
							}

							if (save) {
								this.contactService.save(contactModel);
							}
						}
					}
				}

				if (res.checkInterval > 0) {
					//schedule next interval
					this.preferenceService.setRoutineInterval(getApplicationContext().getString(R.string.preferences__identity_states_check_interval), res.checkInterval);
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}

		// force a quick poll
		if (pollingHelper == null) {
			pollingHelper = new PollingHelper(getApplicationContext(), "identityStatesWorker");
		}

		pollingHelper.poll(false);

		logger.debug("finished");

		if (notificationService != null) {
			notificationService.cancelIdentityStatesSyncProgress();
		}

		return Result.success();
	}

	@Override
	public void onStopped() {
		logger.info("@@@@ Worker has been stopped.");
		super.onStopped();
	}
}
