package ch.threema.app.services;

import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.base.SessionScoped;

@SessionScoped
public interface SynchronizeContactsService {
    @Nullable
    SynchronizeContactsRoutine instantiateSynchronization();

    boolean instantiateSynchronizationAndRun();

    @Nullable
    SynchronizeContactsRoutine instantiateSynchronization(@NonNull Set<String> processingIdentities);

    boolean isSynchronizationInProgress();

    boolean isFullSyncInProgress();

    boolean enableSyncFromLocal();

    boolean disableSyncFromLocal(Runnable runAfterRemovedAccount);
}
