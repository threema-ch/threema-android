/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu.messages

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class P2PMessagesTest {

    @Test
    internal fun `screen sharing must be active if startedAt is provided`() {
        val screenShareCaptureState = P2PMessageContent.CaptureState.Screen(Date())
        assertTrue(screenShareCaptureState.active)
    }

    @Test
    internal fun `screen sharing must be inactive if startedAt is not provided`() {
        val screenShareCaptureState = P2PMessageContent.CaptureState.Screen(null)
        assertFalse(screenShareCaptureState.active)
    }

    @Test
    internal fun `screen share convenience factory methods yield correct state`() {
        val screenShareOn = P2PMessageContent.CaptureState.Screen.on(Date())
        assertTrue(screenShareOn.active)

        val screenShareOff = P2PMessageContent.CaptureState.Screen.off()
        assertFalse(screenShareOff.active)
    }
}
