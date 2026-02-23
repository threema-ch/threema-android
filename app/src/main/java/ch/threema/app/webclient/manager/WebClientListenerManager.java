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
