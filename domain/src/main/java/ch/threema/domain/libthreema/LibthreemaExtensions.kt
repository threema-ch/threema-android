package ch.threema.domain.libthreema

import ch.threema.domain.models.ClientInfo
import ch.threema.libthreema.ClientInfo as LibthreemaClientInfo

fun ClientInfo.toLibthreemaClientInfo(): LibthreemaClientInfo = LibthreemaClientInfo.Android(
    version = appVersion,
    locale = appLocale,
    deviceModel = deviceModel,
    osVersion = osVersion,
)
