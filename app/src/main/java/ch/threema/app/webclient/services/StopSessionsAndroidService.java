package ch.threema.app.webclient.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.slf4j.Logger;

import ch.threema.app.ThreemaApplication;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

/**
 * Simple service to stop all webclient sessions - to be used from the persistent notification
 */
public class StopSessionsAndroidService extends Service {
    private static final Logger logger = getThreemaLogger("StopSessionsAndroidService");

    private SessionService sessionService;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            sessionService = ThreemaApplication.getServiceManager().getWebClientServiceManager().getSessionService();
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (sessionService != null) {
            sessionService.stopAll(DisconnectContext.byUs(DisconnectContext.REASON_SESSION_STOPPED));
        }

        stopSelf();

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
