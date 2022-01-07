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

package ch.threema.app.routines;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.TestUtil;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.storage.models.ContactModel;

public class UpdateFeatureLevelRoutine implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(UpdateFeatureLevelRoutine.class);

	private static final Map<String, Long> checkedIdentities = new HashMap<>();

	public static void removeTimeCache(String identity) {
		synchronized (checkedIdentities) {
			checkedIdentities.remove(identity);
		}
	}

	public static void removeTimeCache(ContactModel contactModel) {
		if (contactModel != null) {
			removeTimeCache(contactModel.getIdentity());
		}
	}
	public interface StatusResult {
		void onFinished(List<ContactModel> handledContacts);
		void onAbort();
		void onError(Exception x);
	}

	public interface Request {
		boolean requestToServer(int featureLevel);
	}

	private final ContactService contactService;
	private final APIConnector apiConnector;
	private String[] identities = null;
	private List<ContactModel> contactModels = null;
	private Request request = null;
	private final List<StatusResult> statusResults = new ArrayList<StatusResult>();
	private boolean abortOnCheckIdentitiesFailed = true;

	public UpdateFeatureLevelRoutine(@NonNull ContactService contactService,
									 @NonNull APIConnector apiConnector,
									 String[] identities,
									 Request request) {
		this.contactService = contactService;
		this.apiConnector = apiConnector;
		this.identities = identities;
		this.request = request;
	}


	public UpdateFeatureLevelRoutine(@NonNull ContactService contactService,
									 @NonNull APIConnector apiConnector,
									 @Nullable List<ContactModel> contactModels) {
		this.contactService = contactService;
		this.apiConnector = apiConnector;
		this.contactModels = contactModels;
	}

	public UpdateFeatureLevelRoutine abortOnCheckIdentitiesFailed(boolean abort) {
		this.abortOnCheckIdentitiesFailed = abort;
		return this;
	}

	public UpdateFeatureLevelRoutine addStatusResult(StatusResult result) {
		this.statusResults.add(result);
		return this;
	}

	@Override
	@WorkerThread
	public void run() {
		logger.info("Running...");

		try {
			//get all identities
			if(this.contactModels == null) {
				if(this.request != null ) {
					this.contactModels = Functional.filter(this.contactService.getByIdentities(this.identities), new IPredicateNonNull<ContactModel>() {
						@Override
						public boolean apply(@NonNull ContactModel type) {
							return request.requestToServer(type.getFeatureMask());
						}
					});
				}
				else {
					this.contactModels = this.contactService.getByIdentities(identities);
				}
			}

			//remove "me" from list
			this.contactModels = Functional.filter(this.contactModels, new IPredicateNonNull<ContactModel>() {
				@Override
				public boolean apply(@NonNull ContactModel c) {
					return !TestUtil.compare(c, contactService.getMe());
				}
			});

			//remove already checked identities
			final Calendar calendar = Calendar.getInstance();
			calendar.setTime(new Date());
			final long nowTimestamp = calendar.getTimeInMillis();
			//leave the identity 1 hour in the cache!
			calendar.add(Calendar.HOUR, -1);
			final long validTimestamp = calendar.getTimeInMillis();

			synchronized (checkedIdentities) {
				//leave only identities that have not been checked in the last hour

				List<ContactModel> filteredList = Functional.filter(this.contactModels, new IPredicateNonNull<ContactModel>() {
					@Override
					public boolean apply(@NonNull ContactModel contactModel) {
						if(checkedIdentities.containsKey(contactModel.getIdentity())
								&& checkedIdentities.get(contactModel.getIdentity()) >= validTimestamp) {
							return false;
						}
						return true;
					}
				});

				logger.info("Running for {} entries", filteredList.size());

				if(filteredList.size() > 0) {
					String[] identities = new String[filteredList.size()];

					for (int n = 0; n < filteredList.size(); n++) {
						identities[n] = filteredList.get(n).getIdentity();
					}

					try {
						Integer[] featureMasks = this.apiConnector.checkFeatureMask(identities);
						for (int n = 0; n < featureMasks.length; n++) {
							final Integer featureMask = featureMasks[n];
							if (featureMask == null) {
								// Skip NULL values
								logger.warn("Feature mask is null!");
								continue;
							}

							ContactModel model = filteredList.get(n);
							if (model != null && model.getFeatureMask() != featureMask) {
								final String identity = model.getIdentity();

								model.setFeatureMask(featureMask);
								this.contactService.save(model);

								//update checked identities cache
								checkedIdentities.put(identity, nowTimestamp);
							}
						}
					} catch (Exception x) {
						//connection error
						if(this.abortOnCheckIdentitiesFailed) {
							for (StatusResult result : statusResults) {
								result.onAbort();
							}
						}
						logger.error("Error while setting feature mask", x);
					}

					for (StatusResult result : statusResults) {
						result.onFinished(this.contactModels);
					}
				}
				else {
					for (StatusResult result : statusResults) {
						result.onFinished(this.contactModels);
					}
				}
			}

		} catch (Exception e) {
			logger.error("Error in run()", e);

			for(StatusResult result: statusResults) {
				result.onError(e);
			}
		}

		logger.info("Done");
	}
}
