/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.managers;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.threema.app.listeners.AppIconListener;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.listeners.ChatListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.DistributionListListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.MessageListener;
import ch.threema.app.listeners.MessagePlayerListener;
import ch.threema.app.listeners.NewSyncedContactsListener;
import ch.threema.app.listeners.PreferenceListener;
import ch.threema.app.listeners.ProfileListener;
import ch.threema.app.listeners.QRCodeScanListener;
import ch.threema.app.listeners.SMSVerificationListener;
import ch.threema.app.listeners.ServerMessageListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.listeners.ThreemaSafeListener;
import ch.threema.app.listeners.VoipCallListener;

public class ListenerManager {
	private static final Logger logger = LoggerFactory.getLogger(ListenerManager.class);

	public interface HandleListener<T> {
		void handle(T listener);
	}

	public static class TypedListenerManager<T> {
		private final List<T> listeners = new ArrayList<>();
		private final Map<String, Integer> tags = new HashMap<>();
		private boolean enabled = true;

		public void add(T l, String tag) {
			synchronized (this.listeners) {
				Integer pos = this.tags.get(tag);
				if (pos != null && pos >= 0) {
					//remove listener first
					this.listeners.remove(this.listeners.get(pos));
				}

				addInternal(this.listeners, l, false);

				//save tagged position
				this.tags.put(tag, this.listeners.size()-1);
			}
		}

		public void add(T l) {
			addInternal(this.listeners, l, false);
		}

		public void add(T l, boolean higherPriority) {
			addInternal(this.listeners, l, higherPriority);
		}

		public void remove(T l) {
			removeInternal(this.listeners, l);
		}

		/**
		 * Remove all listeners.
		 */
		public void clear() {
			synchronized (this.listeners) {
				this.listeners.clear();
			}
		}

		/**
		 * Return whether the specified listener was already added.
		 */
		public boolean contains(T l) {
			return l != null && this.listeners.contains(l);
		}

		public void handle(ListenerManager.HandleListener<T> handleListener) {
			if (handleListener != null && this.enabled) {
				// Since a handler might modify the array of listeners, there's the danger
				// of a ConcurrentModificationException or a deadlock.
				// Therefore we iterate over a copy of the listeners, to avoid that problem.
				final List<T> listenersCopy;
				synchronized (this.listeners) {
					listenersCopy = new ArrayList<>(this.listeners);
				}

				// Run the handle method on every listener
				for (T listener: listenersCopy) {
					if (listener != null) {
						try {
							handleListener.handle(listener);
						} catch (Exception x) {
							logger.error("cannot handle event", x);
						}
					}
				}
			}
		}

		private <T> void addInternal(List<T> holder, T listener, boolean higherPriority) {
			if(holder != null && listener != null) {
				synchronized (holder) {
					if (!holder.contains(listener)) {
						if(higherPriority) {
							//add first!
							holder.add(0, listener);
						}
						else {
							holder.add(listener);
						}
					}
				}
			}
		}

		private <T> void removeInternal(List<T> holder, T listener) {
			if(holder != null && listener != null) {
				synchronized (holder) {
					holder.remove(listener);
				}
			}
		}

		public void enabled(boolean enabled) {
			if(this.enabled != enabled) {
				logger.debug(this.getClass() + " " + (enabled ? "enabled" : "disabled"));
				this.enabled = enabled;
			}
		}

		public boolean isEnabled() {
			return this.enabled;
		}
	}

	public static final TypedListenerManager<ConversationListener> conversationListeners = new TypedListenerManager<ConversationListener>();
	public static final TypedListenerManager<ContactListener> contactListeners = new TypedListenerManager<ContactListener>();
	public static final TypedListenerManager<ContactTypingListener> contactTypingListeners = new TypedListenerManager<ContactTypingListener>();
	public static final TypedListenerManager<DistributionListListener> distributionListListeners = new TypedListenerManager<DistributionListListener>();
	public static final TypedListenerManager<GroupListener> groupListeners = new TypedListenerManager<GroupListener>();
	public static final TypedListenerManager<MessageListener> messageListeners = new TypedListenerManager<MessageListener>();
	public static final TypedListenerManager<PreferenceListener>  preferenceListeners = new TypedListenerManager<PreferenceListener>();
	public static final TypedListenerManager<ServerMessageListener>  serverMessageListeners = new TypedListenerManager<ServerMessageListener>();
	public static final TypedListenerManager<SynchronizeContactsListener>  synchronizeContactsListeners = new TypedListenerManager<SynchronizeContactsListener>();
	public static final TypedListenerManager<ContactSettingsListener>  contactSettingsListeners = new TypedListenerManager<ContactSettingsListener>();
	public static final TypedListenerManager<BallotListener> ballotListeners = new TypedListenerManager<BallotListener>();
	public static final TypedListenerManager<BallotVoteListener> ballotVoteListeners = new TypedListenerManager<BallotVoteListener>();
	public static final TypedListenerManager<SMSVerificationListener> smsVerificationListeners = new TypedListenerManager<SMSVerificationListener>();
	public static final TypedListenerManager<AppIconListener> appIconListeners = new TypedListenerManager<AppIconListener>();
	public static final TypedListenerManager<ProfileListener> profileListeners = new TypedListenerManager<ProfileListener>();
	public static final TypedListenerManager<VoipCallListener> voipCallListeners = new TypedListenerManager<VoipCallListener>();
	public static final TypedListenerManager<ThreemaSafeListener> threemaSafeListeners = new TypedListenerManager<ThreemaSafeListener>();
	public static final TypedListenerManager<ChatListener> chatListener = new TypedListenerManager<>();
	public static final TypedListenerManager<MessagePlayerListener> messagePlayerListener = new TypedListenerManager<>();
	public static final TypedListenerManager<NewSyncedContactsListener> newSyncedContactListener = new TypedListenerManager<>();
	public static final TypedListenerManager<QRCodeScanListener> qrCodeScanListener = new TypedListenerManager<>();
}
