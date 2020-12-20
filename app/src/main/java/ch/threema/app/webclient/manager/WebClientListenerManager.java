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


import androidx.annotation.AnyThread;

import ch.threema.app.managers.ListenerManager;
import ch.threema.app.webclient.listeners.BatteryStatusListener;
import ch.threema.app.webclient.listeners.WebClientMessageListener;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.listeners.WebClientSessionListener;
import ch.threema.app.webclient.listeners.WebClientWakeUpListener;

@AnyThread
public class WebClientListenerManager {
	public static final ListenerManager.TypedListenerManager<WebClientSessionListener> sessionListener = new ListenerManager.TypedListenerManager<>();
	public static final ListenerManager.TypedListenerManager<WebClientServiceListener> serviceListener = new ListenerManager.TypedListenerManager<>();
	public static final ListenerManager.TypedListenerManager<WebClientWakeUpListener> wakeUpListener = new ListenerManager.TypedListenerManager<>();
	public static final ListenerManager.TypedListenerManager<BatteryStatusListener> batteryStatusListener = new ListenerManager.TypedListenerManager<>();
	public static final ListenerManager.TypedListenerManager<WebClientMessageListener> messageListener = new ListenerManager.TypedListenerManager<>();
}
