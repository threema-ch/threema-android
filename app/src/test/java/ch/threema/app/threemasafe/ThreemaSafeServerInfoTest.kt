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

package ch.threema.app.threemasafe

import ch.threema.app.ThreemaApplication
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreemaSafeServerInfoTest {

    @BeforeTest
    fun setUp() {
        // TODO(ANDR-4219): We have to mock ServiceManager, as it is sneakily referenced somewhere deep down the stack. This needs to be cleaned up.
        mockkObject(ThreemaApplication)
        every { ThreemaApplication.getServiceManager() } returns null
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(ThreemaApplication)
    }

    @Test
    fun `uses basic auth when username and password are available`() {
        val serverInfo = threemaSafeServerInfo(
            username = "username",
            password = "password",
        )
        assertEquals("Basic dXNlcm5hbWU6cGFzc3dvcmQ=", serverInfo.authorization)
    }

    @Test
    fun `no authorization when username or password is empty`() {
        assertNull(threemaSafeServerInfo(username = "username", password = "").authorization)
        assertNull(threemaSafeServerInfo(username = "", password = "password").authorization)
        assertNull(threemaSafeServerInfo(username = "", password = "").authorization)
    }

    @Test
    fun `uses basic auth when username and password can be extracted from custom server name`() {
        val serverInfo = threemaSafeServerInfo(customServerName = "username:password@my-custom-server.threema.com")
        assertEquals("Basic dXNlcm5hbWU6cGFzc3dvcmQ=", serverInfo.authorization)
    }

    @Test
    fun `empty username or password is ignored in custom server name`() {
        assertNull(threemaSafeServerInfo(customServerName = "username:@my-custom-server.threema.com").authorization)
        assertNull(threemaSafeServerInfo(customServerName = ":password@my-custom-server.threema.com").authorization)
        assertNull(threemaSafeServerInfo(customServerName = ":@my-custom-server.threema.com").authorization)
    }

    private fun threemaSafeServerInfo(customServerName: String? = null, username: String? = null, password: String? = null) =
        ThreemaSafeServerInfo(customServerName, username, password)
}
