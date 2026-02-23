package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface SMSVerificationListener {
    @AnyThread
    void onVerified();

    @AnyThread
    void onVerificationStarted();
}
