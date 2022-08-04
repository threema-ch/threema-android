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

package ch.threema.app.utils

import android.graphics.Color
import ch.threema.domain.models.GroupId
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(Color::class)
class IDColorTest {

    private val echo = ContactModel("ECHOECHO", null)
    private val abcd1234 = ContactModel("ABCD1234", null)
    private val abcd0123 = ContactModel("ABCD0123", null)

    private val greenLight = 1
    private val greenDark = 2
    private val orangeLight = 3
    private val orangeDark = 4
    private val yellowLight = 5
    private val yellowDark = 6
    private val indigoLight = 7
    private val indigoDark = 8
    private val redLight = 9
    private val redDark = 10
    private val deepPurpleLight = 11
    private val deepPurpleDark = 12
    private val lightBlueLight = 13
    private val lightBlueDark = 14

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        PowerMockito.mockStatic(Color::class.java)
        PowerMockito.`when`(Color.parseColor("#2e7d32")).thenReturn(greenLight)
        PowerMockito.`when`(Color.parseColor("#66bb6a")).thenReturn(greenDark)
        PowerMockito.`when`(Color.parseColor("#ef6c00")).thenReturn(orangeLight)
        PowerMockito.`when`(Color.parseColor("#ffa726")).thenReturn(orangeDark)
        PowerMockito.`when`(Color.parseColor("#eb9c00")).thenReturn(yellowLight)
        PowerMockito.`when`(Color.parseColor("#fff176")).thenReturn(yellowDark)
        PowerMockito.`when`(Color.parseColor("#283593")).thenReturn(indigoLight)
        PowerMockito.`when`(Color.parseColor("#8a93ff")).thenReturn(indigoDark)
        PowerMockito.`when`(Color.parseColor("#c62828")).thenReturn(redLight)
        PowerMockito.`when`(Color.parseColor("#f2706e")).thenReturn(redDark)
        PowerMockito.`when`(Color.parseColor("#7b3ab7")).thenReturn(deepPurpleLight)
        PowerMockito.`when`(Color.parseColor("#a88ce3")).thenReturn(deepPurpleDark)
        PowerMockito.`when`(Color.parseColor("#0288d1")).thenReturn(lightBlueLight)
        PowerMockito.`when`(Color.parseColor("#4fc3f7")).thenReturn(lightBlueDark)
    }

    @Test
    fun testEchoColor() {
        assertEquals(greenLight, echo.colorLight)
        assertEquals(greenDark, echo.colorDark)
    }

    @Test
    fun testAbcdColor() {
        assertEquals(orangeLight, abcd1234.colorLight)
        assertEquals(orangeDark, abcd1234.colorDark)
    }

    @Test
    fun testGroupColor() {
        val echoGroup1 = GroupModel().apply {
            creatorIdentity = echo.identity
            apiGroupId = GroupId(
                byteArrayOf(
                    0x16.toByte(),
                    0x09.toByte(),
                    0xb7.toByte(),
                    0x81.toByte(),
                    0xb0.toByte(),
                    0x1f.toByte(),
                    0x72.toByte(),
                    0x85.toByte()
                )
            )
        }
        assertEquals(yellowLight, echoGroup1.colorLight)
        assertEquals(yellowDark, echoGroup1.colorDark)

        val echoGroup2 = GroupModel().apply {
            creatorIdentity = echo.identity
            apiGroupId = GroupId(
                byteArrayOf(
                    0x31.toByte(),
                    0x20.toByte(),
                    0x35.toByte(),
                    0x9c.toByte(),
                    0x39.toByte(),
                    0xa1.toByte(),
                    0x9b.toByte(),
                    0xe7.toByte()
                )
            )
        }
        assertEquals(indigoLight, echoGroup2.colorLight)
        assertEquals(indigoDark, echoGroup2.colorDark)

        val abcdGroup1 = GroupModel().apply {
            creatorIdentity = abcd0123.identity
            apiGroupId = GroupId(
                byteArrayOf(
                    0x16.toByte(),
                    0x09.toByte(),
                    0xb7.toByte(),
                    0x81.toByte(),
                    0xb0.toByte(),
                    0x1f.toByte(),
                    0x72.toByte(),
                    0x85.toByte()
                )
            )
        }
        assertEquals(redLight, abcdGroup1.colorLight)
        assertEquals(redDark, abcdGroup1.colorDark)

        val abcdGroup2 = GroupModel().apply {
            creatorIdentity = abcd0123.identity
            apiGroupId = GroupId(
                byteArrayOf(
                    0x31.toByte(),
                    0x20.toByte(),
                    0x35.toByte(),
                    0x9c.toByte(),
                    0x39.toByte(),
                    0xa1.toByte(),
                    0x9b.toByte(),
                    0xe7.toByte()
                )
            )
        }
        assertEquals(deepPurpleLight, abcdGroup2.colorLight)
        assertEquals(deepPurpleDark, abcdGroup2.colorDark)
    }

    @Test
    fun testDistributionListColor() {
        val distributionList1 = DistributionListModel().apply {
            id = 0x1609b781b01f7285
        }
        assertEquals(lightBlueLight, distributionList1.colorLight)
        assertEquals(lightBlueDark, distributionList1.colorDark)

        val distributionList2 = DistributionListModel().apply {
            id = 0x3120359c39a19be7
        }
        assertEquals(redLight, distributionList2.colorLight)
        assertEquals(redDark, distributionList2.colorDark)
    }
}
