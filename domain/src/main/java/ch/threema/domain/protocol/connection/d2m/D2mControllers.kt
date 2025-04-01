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

package ch.threema.domain.protocol.connection.d2m

import ch.threema.domain.protocol.connection.util.MainConnectionController
import ch.threema.domain.protocol.connection.util.MdLayer3Controller
import ch.threema.domain.protocol.connection.util.MdLayer4Controller
import ch.threema.domain.protocol.connection.util.ServerConnectionController
import ch.threema.domain.protocol.connection.util.ServerConnectionControllers

internal class D2mControllers(d2mConnectionConfiguration: D2mConnectionConfiguration) :
    ServerConnectionControllers {
    private val d2mController = D2mController(d2mConnectionConfiguration)

    override val serverConnectionController: ServerConnectionController = d2mController
    override val mainController: MainConnectionController = d2mController
    override val layer3Controller: MdLayer3Controller = d2mController
    override val layer4Controller: MdLayer4Controller = d2mController
}
