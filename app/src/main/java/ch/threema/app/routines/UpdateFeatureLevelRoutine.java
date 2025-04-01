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

package ch.threema.app.routines;

import org.slf4j.Logger;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.UserService;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ModelDeletedException;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.data.models.ContactModel;

public class UpdateFeatureLevelRoutine implements Runnable {
    private static final Logger logger = LoggingUtil.getThreemaLogger("UpdateFeatureLevelRoutine");

    private static final Map<String, Long> checkedIdentities = new HashMap<>();

    public static void removeTimeCache(@Nullable String identity) {
        synchronized (checkedIdentities) {
            checkedIdentities.remove(identity);
        }
    }

    @NonNull
    private final UserService userService;
    @NonNull
    private final APIConnector apiConnector;
    @NonNull
    private List<ContactModel> contactModels;

    public UpdateFeatureLevelRoutine(
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull UserService userService,
        @NonNull APIConnector apiConnector,
        @Nullable List<String> identities
    ) {
        this.userService = userService;
        this.apiConnector = apiConnector;
        if (identities != null) {
            this.contactModels = identities
                .stream()
                .map(contactModelRepository::getByIdentity)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } else {
            contactModels = Collections.emptyList();
        }
    }

    @Override
    @WorkerThread
    public void run() {
        logger.info("Running...");

        try {
            //remove "me" from list
            this.contactModels = this.contactModels.stream()
                .filter(c -> !userService.getIdentity().equals(c.getIdentity()))
                .collect(Collectors.toList());

            //remove already checked identities
            final Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            final long nowTimestamp = calendar.getTimeInMillis();
            //leave the identity 1 hour in the cache!
            calendar.add(Calendar.HOUR, -1);
            final long validTimestamp = calendar.getTimeInMillis();

            synchronized (checkedIdentities) {
                // Leave only identities that have not been checked in the last hour
                List<ContactModel> filteredList = this.contactModels.stream()
                    .filter(contactModel -> {
                            Long checkedAt = checkedIdentities.get(contactModel.getIdentity());
                            return checkedAt == null || checkedAt < validTimestamp;
                        }
                    ).collect(Collectors.toList());

                logger.info("Running for {} entries", filteredList.size());

                if (!filteredList.isEmpty()) {
                    String[] identities = new String[filteredList.size()];

                    for (int n = 0; n < filteredList.size(); n++) {
                        identities[n] = filteredList.get(n).getIdentity();
                    }

                    try {
                        Long[] featureMasks = this.apiConnector.checkFeatureMask(identities);
                        for (int n = 0; n < featureMasks.length; n++) {
                            final Long featureMask = featureMasks[n];
                            if (featureMask == null) {
                                // Skip NULL values
                                logger.warn("Feature mask is null!");
                                continue;
                            }

                            ContactModel model = filteredList.get(n);
                            model.setFeatureMaskFromLocal(featureMask);
                            // Update checked identities cache
                            checkedIdentities.put(model.getIdentity(), nowTimestamp);
                        }
                    } catch (ModelDeletedException e) {
                        logger.warn("Model has been deleted", e);
                    } catch (Exception x) {
                        // Connection error
                        logger.error("Error while setting feature mask", x);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error in run()", e);
        }

        logger.info("Done");
    }
}
