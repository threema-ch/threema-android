/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.GroupCallException

class SfuException : GroupCallException {
    val statusCode: Int?

    constructor(
        msg: String,
        callDescription: GroupCallDescription? = null
    ) : super(msg, callDescription) {
        statusCode = null
    }

    constructor(
        msg: String,
        statusCode: Int,
        callDescription: GroupCallDescription? = null
    ) : super(msg, callDescription) {
        this.statusCode = statusCode
    }

    constructor(
        msg: String,
        cause: Throwable,
        callDescription: GroupCallDescription? = null
    ) : super(msg, cause, callDescription) {
        statusCode = null
    }
}
