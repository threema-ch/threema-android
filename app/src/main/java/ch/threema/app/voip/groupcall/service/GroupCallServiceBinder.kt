package ch.threema.app.voip.groupcall.service

import android.os.Binder
import ch.threema.app.voip.CallAudioManager
import ch.threema.app.voip.groupcall.sfu.GroupCallController
import kotlinx.coroutines.CompletableDeferred

class GroupCallServiceBinder(
    private val controllerDeferred: CompletableDeferred<GroupCallController>,
    private val audioManagerDeferred: CompletableDeferred<CallAudioManager>,
) :
    Binder() {
    suspend fun getGroupCallController(): GroupCallController {
        return controllerDeferred.await()
    }

    suspend fun getCallAudioManager(): CallAudioManager {
        return audioManagerDeferred.await()
    }
}
