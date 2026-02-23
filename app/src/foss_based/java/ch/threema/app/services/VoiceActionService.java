package ch.threema.app.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class VoiceActionService extends Service {
    public VoiceActionService() {
        // stub, no voice assistant api in hms build
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
