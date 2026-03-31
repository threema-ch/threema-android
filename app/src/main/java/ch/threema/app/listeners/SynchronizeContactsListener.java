package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.app.routines.SynchronizeContactsRoutine;

public interface SynchronizeContactsListener {
    @AnyThread
    default void onStarted(SynchronizeContactsRoutine startedRoutine) {}

    @AnyThread
    default void onFinished(SynchronizeContactsRoutine finishedRoutine) {}

    @AnyThread
    default void onError(SynchronizeContactsRoutine finishedRoutine) {}
}
