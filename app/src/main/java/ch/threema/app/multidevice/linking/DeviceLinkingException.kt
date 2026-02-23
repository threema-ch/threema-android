package ch.threema.app.multidevice.linking

import ch.threema.base.ThreemaException
import ch.threema.domain.types.Identity

open class DeviceLinkingException : ThreemaException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable?) : super(msg, cause)
}

class DeviceLinkingUnsupportedProtocolException(message: String) : DeviceLinkingException(message)

class DeviceLinkingCancelledException(cause: Throwable? = null) : DeviceLinkingException("Linking cancelled", cause)

class DeviceLinkingInvalidQrCodeException(message: String, cause: Throwable? = null) : DeviceLinkingException(message, cause)

class DeviceLinkingScannedWebQrCodeException(message: String, cause: Throwable? = null) : DeviceLinkingException(message, cause)

class DeviceLinkingInvalidContact(val identity: Identity) : DeviceLinkingException("Invalid contact with identity $identity")
