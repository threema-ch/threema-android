/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.actions;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;

public abstract class SendAction {
	public interface ActionHandler {
		default void onError(String errorMessage) { }
		default void onWarning(String warning, boolean continueAction) { }
		default void onProgress(int progress, int total) { }
		default void onCompleted() { }
	}

	private final ServiceManager serviceManager;
	public SendAction() {
		this.serviceManager = ThreemaApplication.getServiceManager();
	}

	protected ServiceManager getServiceManager() {
		return this.serviceManager;
	}

}