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

package ch.threema.app.voip.groupcall.sfu.webrtc

import androidx.annotation.WorkerThread
import ch.threema.app.webrtc.WrappedDataChannelObserver
import ch.threema.base.utils.getThreemaLogger
import org.webrtc.DataChannel

private val logger = getThreemaLogger("DataChannelCtx")

internal class DataChannelCtx(
    val dc: DataChannel,
    val observer: WrappedDataChannelObserver,
) {
    /**
     * IMPORTANT: Make sure this is executed in the ConnectionCtx-Worker
     */
    @WorkerThread
    fun teardown() {
        logger.trace("Teardown: DataChannelCtx")

        // DataChannel
        logger.trace("Teardown: DataChannel")
        dc.unregisterObserver()
        dc.close()
        dc.dispose()

        logger.trace("Teardown: /DataChannelCtx")
    }
}
