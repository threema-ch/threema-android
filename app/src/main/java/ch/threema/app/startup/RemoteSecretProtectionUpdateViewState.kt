package ch.threema.app.startup

import androidx.compose.runtime.Immutable
import ch.threema.app.startup.models.RemoteSecretUpdateStatus
import ch.threema.app.startup.models.RemoteSecretUpdateType

@Immutable
data class RemoteSecretProtectionUpdateViewState(
    val updateType: RemoteSecretUpdateType,
    val status: RemoteSecretUpdateStatus,
)
