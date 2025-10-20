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

package ch.threema.data.datatypes

import android.content.Context
import ch.threema.app.utils.ConfigUtils
import ch.threema.data.models.GroupIdentity
import ch.threema.domain.models.GroupId
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupModel
import ch.threema.testhelpers.nonSecureRandomArray
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private data class ColorTuple(val light: String, val dark: String)

class IdColorTest {
    private val echoIdentity = "ECHOECHO"
    private val abcd1234Identity = "ABCD1234"
    private val abcd0123Identity = "ABCD0123"

    private val colors: Array<ColorTuple> = arrayOf(
        // [ 0] Deep Orange
        ColorTuple(light = "#d84315", dark = "#ff7043"),
        // [ 1] Orange
        ColorTuple(light = "#ef6c00", dark = "#ffa726"),
        // [ 2] Amber
        ColorTuple(light = "#ff8f00", dark = "#ffca28"),
        // [ 3] Yellow
        ColorTuple(light = "#eb9c00", dark = "#fff176"),
        // [ 4] Olive
        ColorTuple(light = "#9e9d24", dark = "#a6a626"),
        // [ 5] Light Green
        ColorTuple(light = "#7cb342", dark = "#8bc34a"),
        // [ 6] Green
        ColorTuple(light = "#2e7d32", dark = "#66bb6a"),
        // [ 7] Teal
        ColorTuple(light = "#00796b", dark = "#2ab7a9"),
        // [ 8] Cyan
        ColorTuple(light = "#0097a7", dark = "#26c6da"),
        // [ 9] Light Blue
        ColorTuple(light = "#0288d1", dark = "#4fc3f7"),
        // [10] Blue
        ColorTuple(light = "#1565c0", dark = "#42a5f5"),
        // [11] Indigo
        ColorTuple(light = "#283593", dark = "#8a93ff"),
        // [12] Deep Purple
        ColorTuple(light = "#7b3ab7", dark = "#a88ce3"),
        // [13] Purple
        ColorTuple(light = "#ac24aa", dark = "#c680d1"),
        // [14] Pink
        ColorTuple(light = "#ad1457", dark = "#f16f9a"),
        // [15] Red
        ColorTuple(light = "#c62828", dark = "#f2706e"),
    )

    private val colorIndexOrange = 1
    private val colorIndexYellow = 3
    private val colorIndexGreen = 6
    private val colorIndexLightBlue = 9
    private val colorIndexIndigo = 11
    private val colorIndexDeepPurple = 12
    private val colorIndexRed = 15

    @Test
    fun testThemedColors() {
        val contextLight = mockk<Context>(relaxed = true)
        val contextDark = mockk<Context>(relaxed = true)
        mockkStatic(ConfigUtils::class)
        every { ConfigUtils.isTheDarkSide(contextLight) } answers { false }
        every { ConfigUtils.isTheDarkSide(contextDark) } answers { true }

        colors
            .forEachIndexed { index, colorTuple ->
                assertEquals(colorTuple.light, IdColor.getIdColorLightString(index))
                assertEquals(
                    colorTuple.light,
                    IdColor.getThemedIdColorString(contextLight, index),
                )
                assertEquals(colorTuple.dark, IdColor.getIdColorDarkString(index))
                assertEquals(
                    colorTuple.dark,
                    IdColor.getThemedIdColorString(contextDark, index),
                )
            }

        unmockkStatic(ConfigUtils::class)
    }

    @Test
    fun testEchoColor() {
        assertEquals(colorIndexGreen, IdColor.ofIdentity(echoIdentity).colorIndex)
        val echoContact = ContactModel.create(echoIdentity, nonSecureRandomArray(32))
        assertEquals(colorIndexGreen, echoContact.idColor.colorIndex)
    }

    @Test
    fun testAbcdColor() {
        assertEquals(
            colorIndexOrange,
            IdColor.ofIdentity(abcd1234Identity).colorIndex,
        )
        val abcd1234Contact = ContactModel.create(abcd1234Identity, nonSecureRandomArray(32))
        assertEquals(colorIndexOrange, abcd1234Contact.idColor.colorIndex)
    }

    @Test
    fun testGroupColor() {
        assertGroupColorIndex(
            colorIndexYellow,
            echoIdentity,
            GroupId(
                byteArrayOf(
                    0x16.toByte(),
                    0x09.toByte(),
                    0xb7.toByte(),
                    0x81.toByte(),
                    0xb0.toByte(),
                    0x1f.toByte(),
                    0x72.toByte(),
                    0x85.toByte(),
                ),
            ),
            "#1",
        )

        assertGroupColorIndex(
            colorIndexIndigo,
            echoIdentity,
            GroupId(
                byteArrayOf(
                    0x31.toByte(),
                    0x20.toByte(),
                    0x35.toByte(),
                    0x9c.toByte(),
                    0x39.toByte(),
                    0xa1.toByte(),
                    0x9b.toByte(),
                    0xe7.toByte(),
                ),
            ),
            "#2",
        )

        assertGroupColorIndex(
            colorIndexRed,
            abcd0123Identity,
            GroupId(
                byteArrayOf(
                    0x16.toByte(),
                    0x09.toByte(),
                    0xb7.toByte(),
                    0x81.toByte(),
                    0xb0.toByte(),
                    0x1f.toByte(),
                    0x72.toByte(),
                    0x85.toByte(),
                ),
            ),
            "#3",
        )

        assertGroupColorIndex(
            colorIndexDeepPurple,
            abcd0123Identity,
            GroupId(
                byteArrayOf(
                    0x31.toByte(),
                    0x20.toByte(),
                    0x35.toByte(),
                    0x9c.toByte(),
                    0x39.toByte(),
                    0xa1.toByte(),
                    0x9b.toByte(),
                    0xe7.toByte(),
                ),
            ),
            "#4",
        )
    }

    private fun assertGroupColorIndex(
        expectedIndex: Int,
        creatorIdentity: String,
        groupId: GroupId,
        identifier: String,
    ) {
        val groupIdentity = GroupIdentity(
            creatorIdentity = creatorIdentity,
            groupId = groupId.toLong(),
        )

        assertEquals(
            expectedIndex,
            IdColor.ofGroup(groupIdentity).colorIndex,
            "$identifier, computeGroupIdColor",
        )

        val group = GroupModel().apply {
            this.creatorIdentity = creatorIdentity
            apiGroupId = groupId
        }
        assertEquals(
            expectedIndex,
            group.idColor.colorIndex,
            "$identifier, GroupModel#idColor",
        )
    }

    @Test
    fun testDistributionListColor() {
        assertDistributionListColorIndex(
            colorIndexLightBlue,
            0x1609b781b01f7285,
            "#1",
        )

        assertDistributionListColorIndex(
            colorIndexRed,
            0x3120359c39a19be7,
            "#2",
        )
    }

    @Test
    fun testInvalidIdColors() {
        IdColor.invalid().assertInvalid()
        IdColor(-1).assertInvalid()
        IdColor(Int.MIN_VALUE).assertInvalid()
        IdColor(16).assertInvalid()
        IdColor(Int.MAX_VALUE).assertInvalid()
    }

    private fun IdColor.assertInvalid() {
        assertFalse(isValid)
        assertEquals(-1, colorIndex)
    }

    private fun assertDistributionListColorIndex(
        expectedIndex: Int,
        distributionListId: Long,
        identifier: String,
    ) {
        assertEquals(
            expectedIndex,
            IdColor.ofDistributionList(distributionListId).colorIndex,
            "$identifier, computeDistributionListIdColor",
        )

        val distributionList = DistributionListModel().apply {
            id = distributionListId
        }
        assertEquals(
            expectedIndex,
            distributionList.idColor.colorIndex,
            "$identifier, DistributionListModel#idColor",
        )
    }
}
