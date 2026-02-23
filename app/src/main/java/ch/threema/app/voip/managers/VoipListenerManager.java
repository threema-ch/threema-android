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
