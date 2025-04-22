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

import androidx.annotation.AnyThread
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.domain.protocol.api.SfuToken

interface SfuConnection {
    /**
     * Obtain a sfu token from the directory api.
     *
     * A token should be cached as long as it is valid according to it's expiration date.
     *
     * @param forceRefresh Force a reload of the token, even if the token is cached and still valid
     *
     * @throws {@link SfuException} When the sfu cannot be reached, a timeout or another exception occurred
     *
     * TODO(ANDR-2090): add an option for a timeout
     */
    @AnyThread
    suspend fun obtainSfuToken(forceRefresh: Boolean = false): SfuToken

    /**
     * @throws {@link SfuException} When the sfu cannot be reached, a timeout or another exception occurred
     */
    @AnyThread
    suspend fun peek(token: SfuToken, sfuBaseUrl: String, callId: CallId): PeekResponse

    /**
     * @throws {@link SfuException} When the sfu cannot be reached, a timeout or another exception occurred
     */
    @AnyThread
    suspend fun join(
        token: SfuToken,
        sfuBaseUrl: String,
        callDescription: GroupCallDescription,
        dtlsFingerprint: ByteArray,
    ): JoinResponse
}
