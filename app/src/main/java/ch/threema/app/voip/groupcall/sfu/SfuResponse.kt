/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

const val HTTP_STATUS_OK = 200
const val HTTP_STATUS_DATA_INVALID = 400
const val HTTP_STATUS_TOKEN_INVALID = 401
const val HTTP_STATUS_NO_SUCH_CALL = 404
const val HTTP_STATUS_UNSUPPORTED_PROTOCOL_VERSION = 419
const val HTTP_STATUS_SFU_NOT_AVAILABLE = 502
const val HTTP_STATUS_CALL_FULL = 503

data class SfuResponse<T>(
    val statusCode: Int,
    val body: T?,
) {
    val isHttpOk: Boolean
        get() = statusCode == HTTP_STATUS_OK

    val isHttpNotFound: Boolean
        get() = statusCode == HTTP_STATUS_NO_SUCH_CALL
}
