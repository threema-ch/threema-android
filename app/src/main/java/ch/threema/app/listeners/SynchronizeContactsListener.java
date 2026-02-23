package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.app.routines.SynchronizeContactsRoutine;

public interface SynchronizeContactsListener {
    @AnyThread
    void onStarted(SynchronizeContactsRoutine startedRoutine);

    @AnyThread
    void onFinished(SynchronizeContactsRoutine finishedRoutine);

    @AnyThread
    void onError(SynchronizeContactsRoutine finishedRoutine);
}
