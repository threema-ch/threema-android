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

package ch.threema.app.utils

import java.util.*

/**
 * Provides unicode script functionality that is not available before SDK version 24.
 * From SDK version 24 the implementation of the Character class can be used.
 */
object UnicodeUtil {
    fun getScript(c: Char): UnicodeScript = getScriptForCodepoint(c.code)

    private fun getScriptForCodepoint(codepoint: Int): UnicodeScript {
        if (!Character.isValidCodePoint(codepoint)) {
            throw IllegalArgumentException("Invalid code point $codepoint")
        }
        if (Character.getType(codepoint) == Character.UNASSIGNED.toInt()) {
            return UnicodeScript.UNKNOWN
        }
        val position = Arrays.binarySearch(scriptStartOffsets, codepoint)
        return scripts[if (position < 0) -position - 2 else position]
    }

    enum class UnicodeScript {
        COMMON,
        LATIN,
        GREEK,
        CYRILLIC,
        ARMENIAN,
        HEBREW,
        ARABIC,
        SYRIAC,
        THAANA,
        DEVANAGARI,
        BENGALI,
        GURMUKHI,
        GUJARATI,
        ORIYA,
        TAMIL,
        TELUGU,
        KANNADA,
        MALAYALAM,
        SINHALA,
        THAI,
        LAO,
        TIBETAN,
        MYANMAR,
        GEORGIAN,
        HANGUL,
        ETHIOPIC,
        CHEROKEE,
        CANADIAN_ABORIGINAL,
        OGHAM,
        RUNIC,
        KHMER,
        MONGOLIAN,
        HIRAGANA,
        KATAKANA,
        BOPOMOFO,
        HAN,
        YI,
        OLD_ITALIC,
        GOTHIC,
        DESERET,
        INHERITED,
        TAGALOG,
        HANUNOO,
        BUHID,
        TAGBANWA,
        LIMBU,
        TAI_LE,
        LINEAR_B,
        UGARITIC,
        SHAVIAN,
        OSMANYA,
        CYPRIOT,
        BRAILLE,
        BUGINESE,
        COPTIC,
        NEW_TAI_LUE,
        GLAGOLITIC,
        TIFINAGH,
        SYLOTI_NAGRI,
        OLD_PERSIAN,
        KHAROSHTHI,
        BALINESE,
        CUNEIFORM,
        PHOENICIAN,
        PHAGS_PA,
        NKO,
        SUNDANESE,
        BATAK,
        LEPCHA,
        OL_CHIKI,
        VAI,
        SAURASHTRA,
        KAYAH_LI,
        REJANG,
        LYCIAN,
        CARIAN,
        LYDIAN,
        CHAM,
        TAI_THAM,
        TAI_VIET,
        AVESTAN,
        EGYPTIAN_HIEROGLYPHS,
        SAMARITAN,
        MANDAIC,
        LISU,
        BAMUM,
        JAVANESE,
        MEETEI_MAYEK,
        IMPERIAL_ARAMAIC,
        OLD_SOUTH_ARABIAN,
        INSCRIPTIONAL_PARTHIAN,
        INSCRIPTIONAL_PAHLAVI,
        OLD_TURKIC,
        BRAHMI,
        KAITHI,
        MEROITIC_HIEROGLYPHS,
        MEROITIC_CURSIVE,
        SORA_SOMPENG,
        CHAKMA,
        SHARADA,
        TAKRI,
        MIAO,
        UNKNOWN,
    }

    private val scriptStartOffsets = intArrayOf(
        // COMMON
        0x0000,
        // LATIN
        0x0041,
        // COMMON
        0x005B,
        // LATIN
        0x0061,
        // COMMON
        0x007B,
        // LATIN
        0x00AA,
        // COMMON
        0x00AB,
        // LATIN
        0x00BA,
        // COMMON
        0x00BB,
        // LATIN
        0x00C0,
        // COMMON
        0x00D7,
        // LATIN
        0x00D8,
        // COMMON
        0x00F7,
        // LATIN
        0x00F8,
        // COMMON
        0x02B9,
        // LATIN
        0x02E0,
        // COMMON
        0x02E5,
        // BOPOMOFO
        0x02EA,
        // COMMON
        0x02EC,
        // INHERITED
        0x0300,
        // GREEK
        0x0370,
        // COMMON
        0x0374,
        // GREEK
        0x0375,
        // COMMON
        0x037E,
        // GREEK
        0x0384,
        // COMMON
        0x0385,
        // GREEK
        0x0386,
        // COMMON
        0x0387,
        // GREEK
        0x0388,
        // COPTIC
        0x03E2,
        // GREEK
        0x03F0,
        // CYRILLIC
        0x0400,
        // INHERITED
        0x0485,
        // CYRILLIC
        0x0487,
        // ARMENIAN
        0x0531,
        // COMMON
        0x0589,
        // ARMENIAN
        0x058A,
        // HEBREW
        0x0591,
        // ARABIC
        0x0600,
        // COMMON
        0x060C,
        // ARABIC
        0x060D,
        // COMMON
        0x061B,
        // ARABIC
        0x061E,
        // COMMON
        0x061F,
        // ARABIC
        0x0620,
        // COMMON
        0x0640,
        // ARABIC
        0x0641,
        // INHERITED
        0x064B,
        // ARABIC
        0x0656,
        // COMMON
        0x0660,
        // ARABIC
        0x066A,
        // INHERITED
        0x0670,
        // ARABIC
        0x0671,
        // COMMON
        0x06DD,
        // ARABIC
        0x06DE,
        // SYRIAC
        0x0700,
        // ARABIC
        0x0750,
        // THAANA
        0x0780,
        // NKO
        0x07C0,
        // SAMARITAN
        0x0800,
        // MANDAIC
        0x0840,
        // ARABIC
        0x08A0,
        // DEVANAGARI
        0x0900,
        // INHERITED
        0x0951,
        // DEVANAGARI
        0x0953,
        // COMMON
        0x0964,
        // DEVANAGARI
        0x0966,
        // BENGALI
        0x0981,
        // GURMUKHI
        0x0A01,
        // GUJARATI
        0x0A81,
        // ORIYA
        0x0B01,
        // TAMIL
        0x0B82,
        // TELUGU
        0x0C01,
        // KANNADA
        0x0C82,
        // MALAYALAM
        0x0D02,
        // SINHALA
        0x0D82,
        // THAI
        0x0E01,
        // COMMON
        0x0E3F,
        // THAI
        0x0E40,
        // LAO
        0x0E81,
        // TIBETAN
        0x0F00,
        // COMMON
        0x0FD5,
        // TIBETAN
        0x0FD9,
        // MYANMAR
        0x1000,
        // GEORGIAN
        0x10A0,
        // COMMON
        0x10FB,
        // GEORGIAN
        0x10FC,
        // HANGUL
        0x1100,
        // ETHIOPIC
        0x1200,
        // CHEROKEE
        0x13A0,
        // CANADIAN_ABORIGINAL
        0x1400,
        // OGHAM
        0x1680,
        // RUNIC
        0x16A0,
        // COMMON
        0x16EB,
        // RUNIC
        0x16EE,
        // TAGALOG
        0x1700,
        // HANUNOO
        0x1720,
        // COMMON
        0x1735,
        // BUHID
        0x1740,
        // TAGBANWA
        0x1760,
        // KHMER
        0x1780,
        // MONGOLIAN
        0x1800,
        // COMMON
        0x1802,
        // MONGOLIAN
        0x1804,
        // COMMON
        0x1805,
        // MONGOLIAN
        0x1806,
        // CANADIAN_ABORIGINAL
        0x18B0,
        // LIMBU
        0x1900,
        // TAI_LE
        0x1950,
        // NEW_TAI_LUE
        0x1980,
        // KHMER
        0x19E0,
        // BUGINESE
        0x1A00,
        // TAI_THAM
        0x1A20,
        // BALINESE
        0x1B00,
        // SUNDANESE
        0x1B80,
        // BATAK
        0x1BC0,
        // LEPCHA
        0x1C00,
        // OL_CHIKI
        0x1C50,
        // SUNDANESE
        0x1CC0,
        // INHERITED
        0x1CD0,
        // COMMON
        0x1CD3,
        // INHERITED
        0x1CD4,
        // COMMON
        0x1CE1,
        // INHERITED
        0x1CE2,
        // COMMON
        0x1CE9,
        // INHERITED
        0x1CED,
        // COMMON
        0x1CEE,
        // INHERITED
        0x1CF4,
        // COMMON
        0x1CF5,
        // LATIN
        0x1D00,
        // GREEK
        0x1D26,
        // CYRILLIC
        0x1D2B,
        // LATIN
        0x1D2C,
        // GREEK
        0x1D5D,
        // LATIN
        0x1D62,
        // GREEK
        0x1D66,
        // LATIN
        0x1D6B,
        // CYRILLIC
        0x1D78,
        // LATIN
        0x1D79,
        // GREEK
        0x1DBF,
        // INHERITED
        0x1DC0,
        // LATIN
        0x1E00,
        // GREEK
        0x1F00,
        // COMMON
        0x2000,
        // INHERITED
        0x200C,
        // COMMON
        0x200E,
        // LATIN
        0x2071,
        // COMMON
        0x2074,
        // LATIN
        0x207F,
        // COMMON
        0x2080,
        // LATIN
        0x2090,
        // COMMON
        0x20A0,
        // INHERITED
        0x20D0,
        // COMMON
        0x2100,
        // GREEK
        0x2126,
        // COMMON
        0x2127,
        // LATIN
        0x212A,
        // COMMON
        0x212C,
        // LATIN
        0x2132,
        // COMMON
        0x2133,
        // LATIN
        0x214E,
        // COMMON
        0x214F,
        // LATIN
        0x2160,
        // COMMON
        0x2189,
        // BRAILLE
        0x2800,
        // COMMON
        0x2900,
        // GLAGOLITIC
        0x2C00,
        // LATIN
        0x2C60,
        // COPTIC
        0x2C80,
        // GEORGIAN
        0x2D00,
        // TIFINAGH
        0x2D30,
        // ETHIOPIC
        0x2D80,
        // CYRILLIC
        0x2DE0,
        // COMMON
        0x2E00,
        // HAN
        0x2E80,
        // COMMON
        0x2FF0,
        // HAN
        0x3005,
        // COMMON
        0x3006,
        // HAN
        0x3007,
        // COMMON
        0x3008,
        // HAN
        0x3021,
        // INHERITED
        0x302A,
        // HANGUL
        0x302E,
        // COMMON
        0x3030,
        // HAN
        0x3038,
        // COMMON
        0x303C,
        // HIRAGANA
        0x3041,
        // INHERITED
        0x3099,
        // COMMON
        0x309B,
        // HIRAGANA
        0x309D,
        // COMMON
        0x30A0,
        // KATAKANA
        0x30A1,
        // COMMON
        0x30FB,
        // KATAKANA
        0x30FD,
        // BOPOMOFO
        0x3105,
        // HANGUL
        0x3131,
        // COMMON
        0x3190,
        // BOPOMOFO
        0x31A0,
        // COMMON
        0x31C0,
        // KATAKANA
        0x31F0,
        // HANGUL
        0x3200,
        // COMMON
        0x3220,
        // HANGUL
        0x3260,
        // COMMON
        0x327F,
        // KATAKANA
        0x32D0,
        // COMMON
        0x3358,
        // HAN
        0x3400,
        // COMMON
        0x4DC0,
        // HAN
        0x4E00,
        // YI
        0xA000,
        // LISU
        0xA4D0,
        // VAI
        0xA500,
        // CYRILLIC
        0xA640,
        // BAMUM
        0xA6A0,
        // COMMON
        0xA700,
        // LATIN
        0xA722,
        // COMMON
        0xA788,
        // LATIN
        0xA78B,
        // SYLOTI_NAGRI
        0xA800,
        // COMMON
        0xA830,
        // PHAGS_PA
        0xA840,
        // SAURASHTRA
        0xA880,
        // DEVANAGARI
        0xA8E0,
        // KAYAH_LI
        0xA900,
        // REJANG
        0xA930,
        // HANGUL
        0xA960,
        // JAVANESE
        0xA980,
        // CHAM
        0xAA00,
        // MYANMAR
        0xAA60,
        // TAI_VIET
        0xAA80,
        // MEETEI_MAYEK
        0xAAE0,
        // ETHIOPIC
        0xAB01,
        // MEETEI_MAYEK
        0xABC0,
        // HANGUL
        0xAC00,
        // UNKNOWN
        0xD7FC,
        // HAN
        0xF900,
        // LATIN
        0xFB00,
        // ARMENIAN
        0xFB13,
        // HEBREW
        0xFB1D,
        // ARABIC
        0xFB50,
        // COMMON
        0xFD3E,
        // ARABIC
        0xFD50,
        // COMMON
        0xFDFD,
        // INHERITED
        0xFE00,
        // COMMON
        0xFE10,
        // INHERITED
        0xFE20,
        // COMMON
        0xFE30,
        // ARABIC
        0xFE70,
        // COMMON
        0xFEFF,
        // LATIN
        0xFF21,
        // COMMON
        0xFF3B,
        // LATIN
        0xFF41,
        // COMMON
        0xFF5B,
        // KATAKANA
        0xFF66,
        // COMMON
        0xFF70,
        // KATAKANA
        0xFF71,
        // COMMON
        0xFF9E,
        // HANGUL
        0xFFA0,
        // COMMON
        0xFFE0,
        // LINEAR_B
        0x10000,
        // COMMON
        0x10100,
        // GREEK
        0x10140,
        // COMMON
        0x10190,
        // INHERITED
        0x101FD,
        // LYCIAN
        0x10280,
        // CARIAN
        0x102A0,
        // OLD_ITALIC
        0x10300,
        // GOTHIC
        0x10330,
        // UGARITIC
        0x10380,
        // OLD_PERSIAN
        0x103A0,
        // DESERET
        0x10400,
        // SHAVIAN
        0x10450,
        // OSMANYA
        0x10480,
        // CYPRIOT
        0x10800,
        // IMPERIAL_ARAMAIC
        0x10840,
        // PHOENICIAN
        0x10900,
        // LYDIAN
        0x10920,
        // MEROITIC_HIEROGLYPHS
        0x10980,
        // MEROITIC_CURSIVE
        0x109A0,
        // KHAROSHTHI
        0x10A00,
        // OLD_SOUTH_ARABIAN
        0x10A60,
        // AVESTAN
        0x10B00,
        // INSCRIPTIONAL_PARTHIAN
        0x10B40,
        // INSCRIPTIONAL_PAHLAVI
        0x10B60,
        // OLD_TURKIC
        0x10C00,
        // ARABIC
        0x10E60,
        // BRAHMI
        0x11000,
        // KAITHI
        0x11080,
        // SORA_SOMPENG
        0x110D0,
        // CHAKMA
        0x11100,
        // SHARADA
        0x11180,
        // TAKRI
        0x11680,
        // CUNEIFORM
        0x12000,
        // EGYPTIAN_HIEROGLYPHS
        0x13000,
        // BAMUM
        0x16800,
        // MIAO
        0x16F00,
        // KATAKANA
        0x1B000,
        // HIRAGANA
        0x1B001,
        // COMMON
        0x1D000,
        // INHERITED
        0x1D167,
        // COMMON
        0x1D16A,
        // INHERITED
        0x1D17B,
        // COMMON
        0x1D183,
        // INHERITED
        0x1D185,
        // COMMON
        0x1D18C,
        // INHERITED
        0x1D1AA,
        // COMMON
        0x1D1AE,
        // GREEK
        0x1D200,
        // COMMON
        0x1D300,
        // ARABIC
        0x1EE00,
        // COMMON
        0x1F000,
        // HIRAGANA
        0x1F200,
        // COMMON
        0x1F201,
        // HAN
        0x20000,
        // COMMON
        0xE0001,
        // INHERITED
        0xE0100,
        // E01F0..10FFFF; UNKNOWN
        0xE01F0,
    )

    private val scripts = arrayOf(
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.BOPOMOFO,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.GREEK,
        UnicodeScript.COMMON,
        UnicodeScript.GREEK,
        UnicodeScript.COMMON,
        UnicodeScript.GREEK,
        UnicodeScript.COMMON,
        UnicodeScript.GREEK,
        UnicodeScript.COMMON,
        UnicodeScript.GREEK,
        UnicodeScript.COPTIC,
        UnicodeScript.GREEK,
        UnicodeScript.CYRILLIC,
        UnicodeScript.INHERITED,
        UnicodeScript.CYRILLIC,
        UnicodeScript.ARMENIAN,
        UnicodeScript.COMMON,
        UnicodeScript.ARMENIAN,
        UnicodeScript.HEBREW,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.INHERITED,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.INHERITED,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.SYRIAC,
        UnicodeScript.ARABIC,
        UnicodeScript.THAANA,
        UnicodeScript.NKO,
        UnicodeScript.SAMARITAN,
        UnicodeScript.MANDAIC,
        UnicodeScript.ARABIC,
        UnicodeScript.DEVANAGARI,
        UnicodeScript.INHERITED,
        UnicodeScript.DEVANAGARI,
        UnicodeScript.COMMON,
        UnicodeScript.DEVANAGARI,
        UnicodeScript.BENGALI,
        UnicodeScript.GURMUKHI,
        UnicodeScript.GUJARATI,
        UnicodeScript.ORIYA,
        UnicodeScript.TAMIL,
        UnicodeScript.TELUGU,
        UnicodeScript.KANNADA,
        UnicodeScript.MALAYALAM,
        UnicodeScript.SINHALA,
        UnicodeScript.THAI,
        UnicodeScript.COMMON,
        UnicodeScript.THAI,
        UnicodeScript.LAO,
        UnicodeScript.TIBETAN,
        UnicodeScript.COMMON,
        UnicodeScript.TIBETAN,
        UnicodeScript.MYANMAR,
        UnicodeScript.GEORGIAN,
        UnicodeScript.COMMON,
        UnicodeScript.GEORGIAN,
        UnicodeScript.HANGUL,
        UnicodeScript.ETHIOPIC,
        UnicodeScript.CHEROKEE,
        UnicodeScript.CANADIAN_ABORIGINAL,
        UnicodeScript.OGHAM,
        UnicodeScript.RUNIC,
        UnicodeScript.COMMON,
        UnicodeScript.RUNIC,
        UnicodeScript.TAGALOG,
        UnicodeScript.HANUNOO,
        UnicodeScript.COMMON,
        UnicodeScript.BUHID,
        UnicodeScript.TAGBANWA,
        UnicodeScript.KHMER,
        UnicodeScript.MONGOLIAN,
        UnicodeScript.COMMON,
        UnicodeScript.MONGOLIAN,
        UnicodeScript.COMMON,
        UnicodeScript.MONGOLIAN,
        UnicodeScript.CANADIAN_ABORIGINAL,
        UnicodeScript.LIMBU,
        UnicodeScript.TAI_LE,
        UnicodeScript.NEW_TAI_LUE,
        UnicodeScript.KHMER,
        UnicodeScript.BUGINESE,
        UnicodeScript.TAI_THAM,
        UnicodeScript.BALINESE,
        UnicodeScript.SUNDANESE,
        UnicodeScript.BATAK,
        UnicodeScript.LEPCHA,
        UnicodeScript.OL_CHIKI,
        UnicodeScript.SUNDANESE,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.GREEK,
        UnicodeScript.CYRILLIC,
        UnicodeScript.LATIN,
        UnicodeScript.GREEK,
        UnicodeScript.LATIN,
        UnicodeScript.GREEK,
        UnicodeScript.LATIN,
        UnicodeScript.CYRILLIC,
        UnicodeScript.LATIN,
        UnicodeScript.GREEK,
        UnicodeScript.INHERITED,
        UnicodeScript.LATIN,
        UnicodeScript.GREEK,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.GREEK,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.BRAILLE,
        UnicodeScript.COMMON,
        UnicodeScript.GLAGOLITIC,
        UnicodeScript.LATIN,
        UnicodeScript.COPTIC,
        UnicodeScript.GEORGIAN,
        UnicodeScript.TIFINAGH,
        UnicodeScript.ETHIOPIC,
        UnicodeScript.CYRILLIC,
        UnicodeScript.COMMON,
        UnicodeScript.HAN,
        UnicodeScript.COMMON,
        UnicodeScript.HAN,
        UnicodeScript.COMMON,
        UnicodeScript.HAN,
        UnicodeScript.COMMON,
        UnicodeScript.HAN,
        UnicodeScript.INHERITED,
        UnicodeScript.HANGUL,
        UnicodeScript.COMMON,
        UnicodeScript.HAN,
        UnicodeScript.COMMON,
        UnicodeScript.HIRAGANA,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.HIRAGANA,
        UnicodeScript.COMMON,
        UnicodeScript.KATAKANA,
        UnicodeScript.COMMON,
        UnicodeScript.KATAKANA,
        UnicodeScript.BOPOMOFO,
        UnicodeScript.HANGUL,
        UnicodeScript.COMMON,
        UnicodeScript.BOPOMOFO,
        UnicodeScript.COMMON,
        UnicodeScript.KATAKANA,
        UnicodeScript.HANGUL,
        UnicodeScript.COMMON,
        UnicodeScript.HANGUL,
        UnicodeScript.COMMON,
        UnicodeScript.KATAKANA,
        UnicodeScript.COMMON,
        UnicodeScript.HAN,
        UnicodeScript.COMMON,
        UnicodeScript.HAN,
        UnicodeScript.YI,
        UnicodeScript.LISU,
        UnicodeScript.VAI,
        UnicodeScript.CYRILLIC,
        UnicodeScript.BAMUM,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.SYLOTI_NAGRI,
        UnicodeScript.COMMON,
        UnicodeScript.PHAGS_PA,
        UnicodeScript.SAURASHTRA,
        UnicodeScript.DEVANAGARI,
        UnicodeScript.KAYAH_LI,
        UnicodeScript.REJANG,
        UnicodeScript.HANGUL,
        UnicodeScript.JAVANESE,
        UnicodeScript.CHAM,
        UnicodeScript.MYANMAR,
        UnicodeScript.TAI_VIET,
        UnicodeScript.MEETEI_MAYEK,
        UnicodeScript.ETHIOPIC,
        UnicodeScript.MEETEI_MAYEK,
        UnicodeScript.HANGUL,
        UnicodeScript.UNKNOWN,
        UnicodeScript.HAN,
        UnicodeScript.LATIN,
        UnicodeScript.ARMENIAN,
        UnicodeScript.HEBREW,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.LATIN,
        UnicodeScript.COMMON,
        UnicodeScript.KATAKANA,
        UnicodeScript.COMMON,
        UnicodeScript.KATAKANA,
        UnicodeScript.COMMON,
        UnicodeScript.HANGUL,
        UnicodeScript.COMMON,
        UnicodeScript.LINEAR_B,
        UnicodeScript.COMMON,
        UnicodeScript.GREEK,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.LYCIAN,
        UnicodeScript.CARIAN,
        UnicodeScript.OLD_ITALIC,
        UnicodeScript.GOTHIC,
        UnicodeScript.UGARITIC,
        UnicodeScript.OLD_PERSIAN,
        UnicodeScript.DESERET,
        UnicodeScript.SHAVIAN,
        UnicodeScript.OSMANYA,
        UnicodeScript.CYPRIOT,
        UnicodeScript.IMPERIAL_ARAMAIC,
        UnicodeScript.PHOENICIAN,
        UnicodeScript.LYDIAN,
        UnicodeScript.MEROITIC_HIEROGLYPHS,
        UnicodeScript.MEROITIC_CURSIVE,
        UnicodeScript.KHAROSHTHI,
        UnicodeScript.OLD_SOUTH_ARABIAN,
        UnicodeScript.AVESTAN,
        UnicodeScript.INSCRIPTIONAL_PARTHIAN,
        UnicodeScript.INSCRIPTIONAL_PAHLAVI,
        UnicodeScript.OLD_TURKIC,
        UnicodeScript.ARABIC,
        UnicodeScript.BRAHMI,
        UnicodeScript.KAITHI,
        UnicodeScript.SORA_SOMPENG,
        UnicodeScript.CHAKMA,
        UnicodeScript.SHARADA,
        UnicodeScript.TAKRI,
        UnicodeScript.CUNEIFORM,
        UnicodeScript.EGYPTIAN_HIEROGLYPHS,
        UnicodeScript.BAMUM,
        UnicodeScript.MIAO,
        UnicodeScript.KATAKANA,
        UnicodeScript.HIRAGANA,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.COMMON,
        UnicodeScript.GREEK,
        UnicodeScript.COMMON,
        UnicodeScript.ARABIC,
        UnicodeScript.COMMON,
        UnicodeScript.HIRAGANA,
        UnicodeScript.COMMON,
        UnicodeScript.HAN,
        UnicodeScript.COMMON,
        UnicodeScript.INHERITED,
        UnicodeScript.UNKNOWN,
    )
}
