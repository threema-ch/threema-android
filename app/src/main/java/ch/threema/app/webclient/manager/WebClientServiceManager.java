/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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

package ch.threema.app.webclient.manager;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.app.webclient.services.ServicesContainer;
import ch.threema.app.webclient.services.SessionService;
import ch.threema.app.webclient.services.SessionServiceImpl;

@AnyThread
public class WebClientServiceManager {
	// Services used by web client services
	@NonNull final private ServicesContainer services;

	// Lazily created Session service instance
	@Nullable private SessionServiceImpl sessionService;

	// Handler on top of the web client's worker thread
	@NonNull private final HandlerExecutor handler;

	public WebClientServiceManager(@NonNull final ServicesContainer services) {
		this.services = services;

		// Start the handler thread for all web client related work
		//
		// Note: We currently don't ever stop this thread. However, a single
		// idle thread should not be a big issue for performance or memory.
		// If we wanted to optimize this, we could stop the thread when Threema
		// Web is disabled, and start it again when it's re-enabled. However, that
		// might be a source of errors if Threema Web is somehow enabled
		// without starting the thread.
		// Worker thread used within most of the web client code
		HandlerThread handlerThread = new HandlerThread("WCWorker");
		handlerThread.start();
		final Looper looper = handlerThread.getLooper();
		final Handler parent;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
			parent = Handler.createAsync(looper);
		} else {
			parent = new Handler(looper);
		}
		this.handler = new HandlerExecutor(parent);
	}

	/**
	 * Return the web client worker thread handler.
	 */
	@NonNull public HandlerExecutor getHandler() {
		return this.handler;
	}

	/**
	 * Return or lazily create a new session service.
	 */
	@NonNull public SessionService getSessionService() {
		if (this.sessionService == null) {
			this.sessionService = new SessionServiceImpl(this.handler, this.services);
		}
		return this.sessionService;
	}
}
