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

package ch.threema.app.voip.services;

import android.app.IntentService;
import android.content.Intent;

import org.slf4j.Logger;

import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;

import static ch.threema.app.voip.services.VoipCallService.EXTRA_CALL_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CONTACT_IDENTITY;

/**
 * A small intent service that rejects an incoming call.
 */
public class CallRejectService extends IntentService {
    private static final String name = "CallRejectService";
    private static final Logger logger = LoggingUtil.getThreemaLogger(name);

    public static final String EXTRA_REJECT_REASON = "REJECT_REASON";

    public CallRejectService() {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        logger.info("onHandleIntent");

        // Ignore null intents
        if (intent == null) {
            logger.debug("Empty Intent");
            return;
        }

        // Intent parameters
        final String contactIdentity = intent.getStringExtra(EXTRA_CONTACT_IDENTITY);
        final long callId = intent.getLongExtra(EXTRA_CALL_ID, 0L);
        final byte rejectReason = intent.getByteExtra(EXTRA_REJECT_REASON, VoipCallAnswerData.RejectReason.UNKNOWN);

        // Reject the call
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            CallRejectWorkerKt.rejectCall(serviceManager, callId, contactIdentity, rejectReason);
        }
    }

}
