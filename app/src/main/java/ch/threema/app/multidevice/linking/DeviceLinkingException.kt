/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.multidevice.linking

import ch.threema.base.ThreemaException

open class DeviceLinkingException : ThreemaException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, cause: Throwable?) : super(msg, cause)
}

class DeviceLinkingUnsupportedProtocolException(message: String) : DeviceLinkingException(message)

class DeviceLinkingCancelledException(cause: Throwable? = null) : DeviceLinkingException("Linking cancelled", cause)

class DeviceLinkingInvalidQrCodeException(message: String, cause: Throwable? = null) : DeviceLinkingException(message, cause)

class DeviceLinkingScannedWebQrCodeException(message: String, cause: Throwable? = null) : DeviceLinkingException(message, cause)
