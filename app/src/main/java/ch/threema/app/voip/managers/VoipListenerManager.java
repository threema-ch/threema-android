/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

package ch.threema.app.voip.managers;


import ch.threema.app.managers.ListenerManager;
import ch.threema.app.voip.listeners.VoipAudioManagerListener;
import ch.threema.app.voip.listeners.VoipCallEventListener;
import ch.threema.app.voip.listeners.VoipMessageListener;

public class VoipListenerManager {
	public static final ListenerManager.TypedListenerManager<VoipMessageListener> messageListener = new ListenerManager.TypedListenerManager<>();
	public static final ListenerManager.TypedListenerManager<VoipCallEventListener> callEventListener = new ListenerManager.TypedListenerManager<>();
	public static final ListenerManager.TypedListenerManager<VoipAudioManagerListener> audioManagerListener = new ListenerManager.TypedListenerManager<>();
}
