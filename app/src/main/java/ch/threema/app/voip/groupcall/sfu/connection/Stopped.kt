/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu.connection

import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.GroupCall
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("GroupCallConnectionState.Stopped")

class Stopped internal constructor(call: GroupCall) : GroupCallConnectionState(StateName.STOPPED, call) {
    @WorkerThread
    override fun getStateProviders() = listOf(suspend {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.info("Call stopped, tearing down")
        call.teardown()
        null
    })
}
