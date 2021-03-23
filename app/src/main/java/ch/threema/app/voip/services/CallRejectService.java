/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import android.content.Context;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.core.app.FixedJobIntentService;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.voip.VoipCallAnswerData;
import ch.threema.storage.models.ContactModel;

import static ch.threema.app.voip.services.VoipCallService.EXTRA_CALL_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CONTACT_IDENTITY;

/**
 * A small intent service that rejects an incoming call.
 */
public class CallRejectService extends FixedJobIntentService {
	private static final Logger logger = LoggerFactory.getLogger(CallRejectService.class);
	public static final String EXTRA_REJECT_REASON = "REJECT_REASON";

	private VoipStateService voipStateService = null;
	private ContactService contactService = null;

	public static final int JOB_ID = 344339;

	public static void enqueueWork(Context context, Intent work) {
		logger.debug("enqueWork entered");
		enqueueWork(context, CallRejectService.class, JOB_ID, work);
	}

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		logger.debug("CallRejectService onHandle work");

		// Intent parameters
		final String contactIdentity = intent.getStringExtra(EXTRA_CONTACT_IDENTITY);
		final long callId = intent.getLongExtra(EXTRA_CALL_ID, 0L);
		final byte rejectReason = intent.getByteExtra(EXTRA_REJECT_REASON, VoipCallAnswerData.RejectReason.UNKNOWN);

		// Set logging prefix
		VoipUtil.setLoggerPrefix(logger, callId);

		// Services
		try {
			final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (this.voipStateService == null) {
				this.voipStateService = serviceManager.getVoipStateService();
			}
			if (this.contactService == null) {
				this.contactService = serviceManager.getContactService();
			}
		} catch (Exception e) {
			logger.error("Could not initialize services", e);
			return;
		}

		// Cancel current notification
		this.voipStateService.cancelCallNotification(contactIdentity);

		// Get contact
		final ContactModel contact = this.contactService.getByIdentity(contactIdentity);
		if (contact == null) {
			logger.error("Could not get contact model for \"{}\"", contactIdentity);
			return;
		}

		try {
			// Reject call
			logger.debug("Rejecting call from {} (reason {})", contactIdentity, rejectReason);
			voipStateService.sendRejectCallAnswerMessage(contact, callId, rejectReason);
		} catch (ThreemaException e) {
			logger.error("Could not send reject answer message", e);
		}

		// Reset state
		this.voipStateService.setStateIdle();

		// Clear the candidates cache
		voipStateService.clearCandidatesCache(contactIdentity);
	}
}
