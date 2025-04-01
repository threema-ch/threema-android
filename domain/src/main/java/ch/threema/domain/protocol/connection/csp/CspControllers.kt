/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.domain.protocol.connection.csp

import ch.threema.domain.protocol.connection.util.Layer3Controller
import ch.threema.domain.protocol.connection.util.Layer4Controller
import ch.threema.domain.protocol.connection.util.MainConnectionController
import ch.threema.domain.protocol.connection.util.ServerConnectionController
import ch.threema.domain.protocol.connection.util.ServerConnectionControllers

internal class CspControllers(cspConnectionConfiguration: CspConnectionConfiguration) :
    ServerConnectionControllers {
    private val cspController = CspController(cspConnectionConfiguration)

    override val serverConnectionController: ServerConnectionController = cspController
    override val mainController: MainConnectionController = cspController
    override val layer3Controller: Layer3Controller = cspController
    override val layer4Controller: Layer4Controller = cspController
}
