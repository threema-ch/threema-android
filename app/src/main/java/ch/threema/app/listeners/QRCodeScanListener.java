package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface QRCodeScanListener {
    @AnyThread
    void onScanCompleted(String scanResult);
}
