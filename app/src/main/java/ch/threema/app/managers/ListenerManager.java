package ch.threema.app.managers;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.listeners.AppIconListener;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.listeners.ChatListener;
import ch.threema.app.listeners.ContactCountListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.DistributionListListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.MessageDeletedForAllListener;
import ch.threema.app.listeners.EditMessageListener;
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

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class ListenerManager {
    private static final Logger logger = getThreemaLogger("ListenerManager");

    public interface HandleListener<T> {
        void handle(@NonNull T listener);
    }

    public static class TypedListenerManager<T> {
        private final List<T> listeners = new ArrayList<>();
        private boolean enabled = true;

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
                // Therefore, we iterate over a copy of the listeners, to avoid that problem.
                final List<T> listenersCopy;
                synchronized (this.listeners) {
                    listenersCopy = new ArrayList<>(this.listeners);
                }

                // Run the handle method on every listener
                for (final @Nullable T listener : listenersCopy) {
                    if (listener != null) {
                        try {
                            handleListener.handle(listener);
                        } catch (Exception e) {
                            logger.error("Failed to handle listener event", e);
                        }
                    }
                }
            }
        }

        private <T> void addInternal(List<T> holder, T listener, boolean higherPriority) {
            if (holder != null && listener != null) {
                synchronized (holder) {
                    if (!holder.contains(listener)) {
                        if (higherPriority) {
                            //add first!
                            holder.add(0, listener);
                        } else {
                            holder.add(listener);
                        }
                    }
                }
            }
        }

        private <T> void removeInternal(List<T> holder, T listener) {
            if (holder != null && listener != null) {
                synchronized (holder) {
                    holder.remove(listener);
                }
            }
        }

        public void enabled(boolean enabled) {
            if (this.enabled != enabled) {
                logger.debug("{} {}", this.getClass(), (enabled ? "enabled" : "disabled"));
                this.enabled = enabled;
            }
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public int size() {
            return listeners.size();
        }
    }

    public static final TypedListenerManager<ConversationListener> conversationListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<ContactListener> contactListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<ContactTypingListener> contactTypingListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<DistributionListListener> distributionListListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<GroupListener> groupListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<MessageListener> messageListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<MessageDeletedForAllListener> messageDeletedForAllListener = new TypedListenerManager<>();
    public static final TypedListenerManager<PreferenceListener> preferenceListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<ServerMessageListener> serverMessageListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<SynchronizeContactsListener> synchronizeContactsListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<ContactSettingsListener> contactSettingsListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<BallotListener> ballotListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<BallotVoteListener> ballotVoteListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<SMSVerificationListener> smsVerificationListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<AppIconListener> appIconListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<ProfileListener> profileListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<VoipCallListener> voipCallListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<ThreemaSafeListener> threemaSafeListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<ChatListener> chatListeners = new TypedListenerManager<>();
    public static final TypedListenerManager<MessagePlayerListener> messagePlayerListener = new TypedListenerManager<>();
    public static final TypedListenerManager<NewSyncedContactsListener> newSyncedContactListener = new TypedListenerManager<>();
    public static final TypedListenerManager<QRCodeScanListener> qrCodeScanListener = new TypedListenerManager<>();
    public static final TypedListenerManager<ContactCountListener> contactCountListener = new TypedListenerManager<>();
    public static final TypedListenerManager<EditMessageListener> editMessageListener = new TypedListenerManager<>();
}
