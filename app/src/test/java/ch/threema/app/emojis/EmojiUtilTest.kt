/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.emojis

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("ktlint:standard:discouraged-comment-location")
class EmojiUtilTest {
    @Test
    fun isFullyQualifiedEmoji_fullyQualifiedEmoji() {
        val fullyQualifiedEmoji = listOf(
            "\uD83D\uDE00", // 😀
            "\uD83D\uDE00", // 😀
            "☺\uFE0F", // ☺️
            "\uD83E\uDEE5", // 🫥
            "\uD83D\uDE35\u200D\uD83D\uDCAB", // 😵‍💫
            "\uD83D\uDC80", // 💀
            "\uD83D\uDC9A", // 💚
            "\uD83D\uDD73\uFE0F", // 🕳️
            "\uD83D\uDC4B\uD83C\uDFFD", // 👋🏽
            "\uD83E\uDD1D\uD83C\uDFFE", // 🤝🏾
            "\uD83E\uDEF1\uD83C\uDFFB\u200D\uD83E\uDEF2\uD83C\uDFFD", // 🫱🏻‍🫲🏽
            "\uD83D\uDC71\uD83C\uDFFF", // 👱🏿
            "\uD83D\uDEB6\u200D♂\uFE0F\u200D➡\uFE0F", // 🚶‍♂️‍➡️
            "\uD83D\uDC68\uD83C\uDFFD\u200D\uD83E\uDDBD", // 👨🏽‍🦽
            "\uD83C\uDFC3\u200D♂\uFE0F\u200D➡\uFE0F", // 🏃‍♂️‍➡️
            "\uD83E\uDDD7\uD83C\uDFFB", // 🧗🏻
            "\uD83E\uDD38\uD83C\uDFFE\u200D♀\uFE0F", // 🤸🏾‍♀️
            "\uD83E\uDDD1\uD83C\uDFFD\u200D\uD83E\uDD1D\u200D\uD83E\uDDD1\uD83C\uDFFB", // 🧑🏽‍🤝‍🧑🏻
            "\uD83D\uDC69\uD83C\uDFFE\u200D\uD83E\uDD1D\u200D\uD83D\uDC69\uD83C\uDFFC", // 👩🏾‍🤝‍👩🏼
            "\uD83D\uDC68\uD83C\uDFFB\u200D\uD83E\uDD1D\u200D\uD83D\uDC68\uD83C\uDFFD", // 👨🏻‍🤝‍👨🏽
            "\uD83E\uDDD1\uD83C\uDFFB\u200D❤\uFE0F\u200D\uD83D\uDC8B\u200D\uD83E\uDDD1\uD83C\uDFFF", // 🧑🏻‍❤️‍💋‍🧑🏿
            "\uD83D\uDC69\uD83C\uDFFF\u200D❤\uFE0F\u200D\uD83D\uDC8B\u200D\uD83D\uDC68\uD83C\uDFFE", // 👩🏿‍❤️‍💋‍👨🏾
            "\uD83D\uDC91\uD83C\uDFFC", // 💑🏼
            "\uD83D\uDC68\uD83C\uDFFD\u200D❤\uFE0F\u200D\uD83D\uDC68\uD83C\uDFFE", // 👨🏽‍❤️‍👨🏾
            "\uD83D\uDC69\uD83C\uDFFD\u200D❤\uFE0F\u200D\uD83D\uDC69\uD83C\uDFFC", // 👩🏽‍❤️‍👩🏼
            "\uD83E\uDDD1\u200D\uD83E\uDDD1\u200D\uD83E\uDDD2\u200D\uD83E\uDDD2", // 🧑‍🧑‍🧒‍🧒
            "\uD83D\uDC29", // 🐩
            "\uD83E\uDD9B", // 🦛
            "\uD83D\uDC1F", // 🐟
            "\uD83E\uDD52", // 🥒
            "\uD83E\uDED5", // 🫕
            "⛺",
            "\uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08", // 🏳️‍🌈
            "\uD83C\uDFF3\uFE0F\u200D⚧\uFE0F", // 🏳️‍⚧️
            "\uD83C\uDFF4\u200D☠\uFE0F", // 🏴‍☠️
            "\uD83C\uDDE8\uD83C\uDDED", // 🇨🇭
        )

        fullyQualifiedEmoji.forEach { emojiSequence ->
            assertTrue(EmojiUtil.isFullyQualifiedEmoji(emojiSequence))
        }
    }

    @Test
    fun isFullyQualifiedEmoji_unqualifiedEmoji() {
        val unqualifiedEmoji = listOf(
            "☺",
            "☠",
            "❤",
            "\uD83D\uDD73", // 🕳
            "\uD83D\uDC41\u200D\uD83D\uDDE8\uFE0F", // 👁‍🗨️
            "\uD83D\uDC41\u200D\uD83D\uDDE8", // 👁‍🗨
            "\uD83D\uDDE8", // 🗨
            "\uD83D\uDDEF", // 🗯
            "\uD83D\uDD75\u200D♀\uFE0F", // 🕵‍♀️
            "\uD83D\uDD74", // 🕴
            "\uD83C\uDFCC", // 🏌
            "\uD83D\uDD78", // 🕸
            "\uD83C\uDF36", // 🌶
            "\uD83C\uDF7D", // 🍽
            "\uD83D\uDDFA", // 🗺
            "\uD83C\uDFD4", // 🏔
            "\uD83C\uDF29", // 🌩
            "\uD83C\uDF2B", // 🌫
            "\uD83C\uDF2C", // 🌬
            "♣",
            "\uD83D\uDD8C", // 🖌
            "\uD83D\uDDD2", // 🗒
            "\uD83D\uDD87", // 🖇
            "\uD83D\uDDDD", // 🗝
            "⛏",
            "✖",
            "♾",
            "⁉",
            "♻",
            "❇",
            "®",
            "™",
            "0⃣",
            "Ⓜ",
            "\uD83C\uDFF3\u200D\uD83C\uDF08", // 🏳‍🌈
            "\uD83C\uDFF3\u200D⚧", // 🏳‍⚧
        )

        unqualifiedEmoji.forEach { emojiSequence ->
            assertFalse(EmojiUtil.isFullyQualifiedEmoji(emojiSequence))
        }
    }

    @Test
    fun isFullyQualifiedEmoji_minimallyQualifiedEmoji() {
        val minimallyQualified = listOf(
            "\uD83D\uDE36\u200D\uD83C\uDF2B", // 😶‍🌫
            "\uD83D\uDE42\u200D↔", // 🙂‍↔
            "\uD83D\uDE42\u200D↕", // 🙂‍↕
            "\uD83D\uDC41\uFE0F\u200D\uD83D\uDDE8", // 👁️‍🗨
            "\uD83E\uDDDE\u200D♀", // 🧞‍♀
            "\uD83C\uDFC3\uD83C\uDFFE\u200D♀\u200D➡\uFE0F", // 🏃🏾‍♀‍➡️
            "\uD83C\uDFC3\uD83C\uDFFF\u200D♂\uFE0F\u200D➡", // 🏃🏿‍♂️‍➡
            "\uD83D\uDC6F\u200D♂", // 👯‍♂
            "\uD83C\uDFCA\u200D♀", // 🏊‍♀
            "\uD83E\uDD3D\uD83C\uDFFC\u200D♂", // 🤽🏼‍♂
            "\uD83E\uDD3D\uD83C\uDFFD\u200D♀", // 🤽🏽‍♀
            "\uD83D\uDC69\u200D❤\u200D\uD83D\uDC8B\u200D\uD83D\uDC68", // 👩‍❤‍💋‍👨
            "\uD83D\uDC69\uD83C\uDFFC\u200D❤\u200D\uD83D\uDC8B\u200D\uD83D\uDC68\uD83C\uDFFB", // 👩🏼‍❤‍💋‍👨🏻
            "\uD83D\uDC68\uD83C\uDFFB\u200D❤\u200D\uD83D\uDC68\uD83C\uDFFD", // 👨🏻‍❤‍👨🏽
            "\uD83D\uDC68\uD83C\uDFFD\u200D❤\u200D\uD83D\uDC68\uD83C\uDFFE", // 👨🏽‍❤‍👨🏾
            "\uD83D\uDC69\u200D❤\u200D\uD83D\uDC69", // 👩‍❤‍👩
            "\uD83D\uDC69\uD83C\uDFFE\u200D❤\u200D\uD83D\uDC69\uD83C\uDFFC", // 👩🏾‍❤‍👩🏼
            "\uD83C\uDFF4\u200D☠", // 🏴‍☠
        )

        minimallyQualified.forEach { emojiSequence ->
            assertFalse(EmojiUtil.isFullyQualifiedEmoji(emojiSequence))
        }
    }

    @Test
    fun isThumbsUpEmoji_thumbsUp() {
        assertTrue(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4D")) // 👍
        assertTrue(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4D\uD83C\uDFFB")) // 👍🏻
        assertTrue(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4D\uD83C\uDFFC")) // 👍🏼
        assertTrue(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4D\uD83C\uDFFD")) // 👍🏽
        assertTrue(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4D\uD83C\uDFFE")) // 👍🏾
        assertTrue(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4D\uD83C\uDFFF")) // 👍🏿
    }

    @Test
    fun isThumbsUpEmoji_thumbsDown() {
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4E")) // 👎
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4E\uD83C\uDFFB")) // 👎🏻
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4E\uD83C\uDFFC")) // 👎🏼
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4E\uD83C\uDFFD")) // 👎🏽
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4E\uD83C\uDFFE")) // 👎🏾
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC4E\uD83C\uDFFF")) // 👎🏿
    }

    @Test
    fun isThumbsUpEmoji_otherEmoji() {
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83E\uDD70")) // 🥰
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83D\uDC7E")) // 👾
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83D\uDCAA")) // 💪
        assertFalse(EmojiUtil.isThumbsUpEmoji("\uD83E\uDEF1\uD83C\uDFFF\u200D\uD83E\uDEF2\uD83C\uDFFB")) // 🫱🏿‍🫲🏻
        assertFalse(EmojiUtil.isThumbsUpEmoji("✊"))
        assertFalse(EmojiUtil.isThumbsUpEmoji("✅"))
        assertFalse(EmojiUtil.isThumbsUpEmoji("❌"))
    }

    @Test
    fun isThumbsDownEmoji_thumbsDown() {
        assertTrue(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4E")) // 👎
        assertTrue(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4E\uD83C\uDFFB")) // 👎🏻
        assertTrue(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4E\uD83C\uDFFC")) // 👎🏼
        assertTrue(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4E\uD83C\uDFFD")) // 👎🏽
        assertTrue(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4E\uD83C\uDFFE")) // 👎🏾
        assertTrue(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4E\uD83C\uDFFF")) // 👎🏿
    }

    @Test
    fun isThumbsDownEmoji_thumbsUp() {
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4D")) // 👍
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4D\uD83C\uDFFB")) // 👍🏻
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4D\uD83C\uDFFC")) // 👍🏼
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4D\uD83C\uDFFD")) // 👍🏽
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4D\uD83C\uDFFE")) // 👍🏾
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC4D\uD83C\uDFFF")) // 👍🏿
    }

    @Test
    fun isThumbsDownEmoji_otherEmoji() {
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83E\uDD70")) // 🥰
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83D\uDC7E")) // 👾
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83D\uDCAA")) // 💪
        assertFalse(EmojiUtil.isThumbsDownEmoji("\uD83E\uDEF1\uD83C\uDFFF\u200D\uD83E\uDEF2\uD83C\uDFFB")) // 🫱🏿‍🫲🏻
        assertFalse(EmojiUtil.isThumbsDownEmoji("✊"))
        assertFalse(EmojiUtil.isThumbsDownEmoji("✅"))
        assertFalse(EmojiUtil.isThumbsDownEmoji("❌"))
    }

    @Test
    fun isThumbsUpOrDownEmoji_thumbsUpDown() {
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4D")) // 👍
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4D\uD83C\uDFFB")) // 👍🏻
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4D\uD83C\uDFFC")) // 👍🏼
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4D\uD83C\uDFFD")) // 👍🏽
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4D\uD83C\uDFFE")) // 👍🏾
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4D\uD83C\uDFFF")) // 👍🏿
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4E")) // 👎
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4E\uD83C\uDFFB")) // 👎🏻
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4E\uD83C\uDFFC")) // 👎🏼
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4E\uD83C\uDFFD")) // 👎🏽
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4E\uD83C\uDFFE")) // 👎🏾
        assertTrue(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC4E\uD83C\uDFFF")) // 👎🏿
    }

    @Test
    fun isThumbsUpOrDownEmoji_otherEmoji() {
        assertFalse(EmojiUtil.isThumbsUpOrDownEmoji("\uD83E\uDD70")) // 🥰
        assertFalse(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDC7E")) // 👾
        assertFalse(EmojiUtil.isThumbsUpOrDownEmoji("\uD83D\uDCAA")) // 💪
        assertFalse(EmojiUtil.isThumbsUpOrDownEmoji("\uD83E\uDEF1\uD83C\uDFFF\u200D\uD83E\uDEF2\uD83C\uDFFB")) // 🫱🏿‍🫲🏻
        assertFalse(EmojiUtil.isThumbsUpOrDownEmoji("✊"))
        assertFalse(EmojiUtil.isThumbsUpOrDownEmoji("✅"))
        assertFalse(EmojiUtil.isThumbsUpOrDownEmoji("❌"))
    }
}
