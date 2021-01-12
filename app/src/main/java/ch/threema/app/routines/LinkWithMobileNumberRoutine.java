/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import ch.threema.app.R;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.LogUtil;

// TODO: Remove unused class?
public class LinkWithMobileNumberRoutine implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(LinkWithMobileNumberRoutine.class);
	final ScheduledExecutorService scheduleTaskExecutor = Executors.newScheduledThreadPool(5);

	public interface ExceptionHandler {
		void handle(Exception x);
	}

	public interface OnFinished {
		void finished(boolean success);
	}

	public interface OnStateUpdate {
		void update(float percent, String text);
	}

	private static int CHECK_SMS_EFFORT = 20;

	private final Context context;
	private final UserService userService;
	private final String mobileNumber;

	private ExceptionHandler exceptionHandler;
	private OnFinished onFinished;
	private OnStateUpdate onStateUpdate;
	private int steps = CHECK_SMS_EFFORT + 1;
	private int currentStep = 0;

	volatile int a = 0;

	public LinkWithMobileNumberRoutine(Context context, UserService userService, String mobileNumber) {
		this.context = context;
		this.userService = userService;
		this.mobileNumber = mobileNumber;
	}

	public void setExceptionHandler(ExceptionHandler h) {
		this.exceptionHandler = h;
	}

	public void setOnFinished(OnFinished f) {
		this.onFinished = f;
	}

	public void setOnStateUpdate(OnStateUpdate onStateUpdate) {
		this.onStateUpdate = onStateUpdate;
	}

	private void handleException(Exception x) {
		if (this.exceptionHandler != null) {
			this.exceptionHandler.handle(x);
		} else {
			logger.error("Exception", x);
		}
	}

	private void stepUp(String text) {
		if (this.onStateUpdate != null) {
			this.onStateUpdate.update(100 / this.steps * (this.currentStep + 1), text);
		}

		this.currentStep++;
	}

	@Override
	public void run() {
		//linkBallot
		this.currentStep = 0;

		try {
			this.stepUp(context.getString(R.string.menu_mobile_linking));
			userService.linkWithMobileNumber(mobileNumber);

			if (onFinished != null) {

				//check every 5 seconds for a auto verification
				scheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {
					public void run() {
						stepUp(context.getString(R.string.check_incoming_sms) + " " + String.valueOf(a) + "/" + String.valueOf(CHECK_SMS_EFFORT));
						if (a > CHECK_SMS_EFFORT || Thread.currentThread().isInterrupted()) {
							onFinished.finished(false);
							scheduleTaskExecutor.shutdown();
						}

						if (userService.getMobileLinkingState() == UserService.LinkingState_LINKED) {
							onFinished.finished(true);
							scheduleTaskExecutor.shutdown();
						}
						a++;
					}
				}, 0, 3, TimeUnit.SECONDS);
			}
		} catch (Exception e) {
			this.handleException(e);
			if (this.onFinished != null) {
				this.onFinished.finished(false);
			}
		}
	}

	public void abort() {
		if (!scheduleTaskExecutor.isShutdown()) {
			scheduleTaskExecutor.shutdown();
		}

		if (this.onFinished != null) {
			this.onFinished.finished(false);
		}
	}
}
