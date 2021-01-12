/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2021 Threema GmbH
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

package ch.threema.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.datatheorem.android.trustkit.pinning.PinningValidationResult;
import com.datatheorem.android.trustkit.reporting.BackgroundReporter;
import com.datatheorem.android.trustkit.reporting.PinningFailureReport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.R;

public class PinningFailureReportBroadcastReceiver extends BroadcastReceiver {
	private static final Logger logger = LoggerFactory.getLogger(PinningFailureReportBroadcastReceiver.class);

	@Override
	public void onReceive(Context context, Intent intent) {
		PinningFailureReport report = (PinningFailureReport) intent.getSerializableExtra(BackgroundReporter.EXTRA_REPORT);

		logger.info("Certificate pinning failure");
		logger.info("Server Hostname  : " + report.getServerHostname());
		logger.info("Noted Hostname   : " + report.getNotedHostname());
		logger.info("Validation Result: " + report.getValidationResult());

		if (report.getValidationResult() == PinningValidationResult.FAILED_CERTIFICATE_CHAIN_NOT_TRUSTED) {
			Toast.makeText(context, R.string.pinning_not_trusted, Toast.LENGTH_LONG).show();
		} else if (report.getValidationResult() == PinningValidationResult.FAILED) {
			Toast.makeText(context, R.string.pinning_failed, Toast.LENGTH_LONG).show();
		}
	}
}
