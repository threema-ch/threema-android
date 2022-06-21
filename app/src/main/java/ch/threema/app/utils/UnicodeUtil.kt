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
        UNKNOWN
    }

    private val scriptStartOffsets = intArrayOf(
        0x0000,  // COMMON
        0x0041,  // LATIN
        0x005B,  // COMMON
        0x0061,  // LATIN
        0x007B,  // COMMON
        0x00AA,  // LATIN
        0x00AB,  // COMMON
        0x00BA,  // LATIN
        0x00BB,  // COMMON
        0x00C0,  // LATIN
        0x00D7,  // COMMON
        0x00D8,  // LATIN
        0x00F7,  // COMMON
        0x00F8,  // LATIN
        0x02B9,  // COMMON
        0x02E0,  // LATIN
        0x02E5,  // COMMON
        0x02EA,  // BOPOMOFO
        0x02EC,  // COMMON
        0x0300,  // INHERITED
        0x0370,  // GREEK
        0x0374,  // COMMON
        0x0375,  // GREEK
        0x037E,  // COMMON
        0x0384,  // GREEK
        0x0385,  // COMMON
        0x0386,  // GREEK
        0x0387,  // COMMON
        0x0388,  // GREEK
        0x03E2,  // COPTIC
        0x03F0,  // GREEK
        0x0400,  // CYRILLIC
        0x0485,  // INHERITED
        0x0487,  // CYRILLIC
        0x0531,  // ARMENIAN
        0x0589,  // COMMON
        0x058A,  // ARMENIAN
        0x0591,  // HEBREW
        0x0600,  // ARABIC
        0x060C,  // COMMON
        0x060D,  // ARABIC
        0x061B,  // COMMON
        0x061E,  // ARABIC
        0x061F,  // COMMON
        0x0620,  // ARABIC
        0x0640,  // COMMON
        0x0641,  // ARABIC
        0x064B,  // INHERITED
        0x0656,  // ARABIC
        0x0660,  // COMMON
        0x066A,  // ARABIC
        0x0670,  // INHERITED
        0x0671,  // ARABIC
        0x06DD,  // COMMON
        0x06DE,  // ARABIC
        0x0700,  // SYRIAC
        0x0750,  // ARABIC
        0x0780,  // THAANA
        0x07C0,  // NKO
        0x0800,  // SAMARITAN
        0x0840,  // MANDAIC
        0x08A0,  // ARABIC
        0x0900,  // DEVANAGARI
        0x0951,  // INHERITED
        0x0953,  // DEVANAGARI
        0x0964,  // COMMON
        0x0966,  // DEVANAGARI
        0x0981,  // BENGALI
        0x0A01,  // GURMUKHI
        0x0A81,  // GUJARATI
        0x0B01,  // ORIYA
        0x0B82,  // TAMIL
        0x0C01,  // TELUGU
        0x0C82,  // KANNADA
        0x0D02,  // MALAYALAM
        0x0D82,  // SINHALA
        0x0E01,  // THAI
        0x0E3F,  // COMMON
        0x0E40,  // THAI
        0x0E81,  // LAO
        0x0F00,  // TIBETAN
        0x0FD5,  // COMMON
        0x0FD9,  // TIBETAN
        0x1000,  // MYANMAR
        0x10A0,  // GEORGIAN
        0x10FB,  // COMMON
        0x10FC,  // GEORGIAN
        0x1100,  // HANGUL
        0x1200,  // ETHIOPIC
        0x13A0,  // CHEROKEE
        0x1400,  // CANADIAN_ABORIGINAL
        0x1680,  // OGHAM
        0x16A0,  // RUNIC
        0x16EB,  // COMMON
        0x16EE,  // RUNIC
        0x1700,  // TAGALOG
        0x1720,  // HANUNOO
        0x1735,  // COMMON
        0x1740,  // BUHID
        0x1760,  // TAGBANWA
        0x1780,  // KHMER
        0x1800,  // MONGOLIAN
        0x1802,  // COMMON
        0x1804,  // MONGOLIAN
        0x1805,  // COMMON
        0x1806,  // MONGOLIAN
        0x18B0,  // CANADIAN_ABORIGINAL
        0x1900,  // LIMBU
        0x1950,  // TAI_LE
        0x1980,  // NEW_TAI_LUE
        0x19E0,  // KHMER
        0x1A00,  // BUGINESE
        0x1A20,  // TAI_THAM
        0x1B00,  // BALINESE
        0x1B80,  // SUNDANESE
        0x1BC0,  // BATAK
        0x1C00,  // LEPCHA
        0x1C50,  // OL_CHIKI
        0x1CC0,  // SUNDANESE
        0x1CD0,  // INHERITED
        0x1CD3,  // COMMON
        0x1CD4,  // INHERITED
        0x1CE1,  // COMMON
        0x1CE2,  // INHERITED
        0x1CE9,  // COMMON
        0x1CED,  // INHERITED
        0x1CEE,  // COMMON
        0x1CF4,  // INHERITED
        0x1CF5,  // COMMON
        0x1D00,  // LATIN
        0x1D26,  // GREEK
        0x1D2B,  // CYRILLIC
        0x1D2C,  // LATIN
        0x1D5D,  // GREEK
        0x1D62,  // LATIN
        0x1D66,  // GREEK
        0x1D6B,  // LATIN
        0x1D78,  // CYRILLIC
        0x1D79,  // LATIN
        0x1DBF,  // GREEK
        0x1DC0,  // INHERITED
        0x1E00,  // LATIN
        0x1F00,  // GREEK
        0x2000,  // COMMON
        0x200C,  // INHERITED
        0x200E,  // COMMON
        0x2071,  // LATIN
        0x2074,  // COMMON
        0x207F,  // LATIN
        0x2080,  // COMMON
        0x2090,  // LATIN
        0x20A0,  // COMMON
        0x20D0,  // INHERITED
        0x2100,  // COMMON
        0x2126,  // GREEK
        0x2127,  // COMMON
        0x212A,  // LATIN
        0x212C,  // COMMON
        0x2132,  // LATIN
        0x2133,  // COMMON
        0x214E,  // LATIN
        0x214F,  // COMMON
        0x2160,  // LATIN
        0x2189,  // COMMON
        0x2800,  // BRAILLE
        0x2900,  // COMMON
        0x2C00,  // GLAGOLITIC
        0x2C60,  // LATIN
        0x2C80,  // COPTIC
        0x2D00,  // GEORGIAN
        0x2D30,  // TIFINAGH
        0x2D80,  // ETHIOPIC
        0x2DE0,  // CYRILLIC
        0x2E00,  // COMMON
        0x2E80,  // HAN
        0x2FF0,  // COMMON
        0x3005,  // HAN
        0x3006,  // COMMON
        0x3007,  // HAN
        0x3008,  // COMMON
        0x3021,  // HAN
        0x302A,  // INHERITED
        0x302E,  // HANGUL
        0x3030,  // COMMON
        0x3038,  // HAN
        0x303C,  // COMMON
        0x3041,  // HIRAGANA
        0x3099,  // INHERITED
        0x309B,  // COMMON
        0x309D,  // HIRAGANA
        0x30A0,  // COMMON
        0x30A1,  // KATAKANA
        0x30FB,  // COMMON
        0x30FD,  // KATAKANA
        0x3105,  // BOPOMOFO
        0x3131,  // HANGUL
        0x3190,  // COMMON
        0x31A0,  // BOPOMOFO
        0x31C0,  // COMMON
        0x31F0,  // KATAKANA
        0x3200,  // HANGUL
        0x3220,  // COMMON
        0x3260,  // HANGUL
        0x327F,  // COMMON
        0x32D0,  // KATAKANA
        0x3358,  // COMMON
        0x3400,  // HAN
        0x4DC0,  // COMMON
        0x4E00,  // HAN
        0xA000,  // YI
        0xA4D0,  // LISU
        0xA500,  // VAI
        0xA640,  // CYRILLIC
        0xA6A0,  // BAMUM
        0xA700,  // COMMON
        0xA722,  // LATIN
        0xA788,  // COMMON
        0xA78B,  // LATIN
        0xA800,  // SYLOTI_NAGRI
        0xA830,  // COMMON
        0xA840,  // PHAGS_PA
        0xA880,  // SAURASHTRA
        0xA8E0,  // DEVANAGARI
        0xA900,  // KAYAH_LI
        0xA930,  // REJANG
        0xA960,  // HANGUL
        0xA980,  // JAVANESE
        0xAA00,  // CHAM
        0xAA60,  // MYANMAR
        0xAA80,  // TAI_VIET
        0xAAE0,  // MEETEI_MAYEK
        0xAB01,  // ETHIOPIC
        0xABC0,  // MEETEI_MAYEK
        0xAC00,  // HANGUL
        0xD7FC,  // UNKNOWN
        0xF900,  // HAN
        0xFB00,  // LATIN
        0xFB13,  // ARMENIAN
        0xFB1D,  // HEBREW
        0xFB50,  // ARABIC
        0xFD3E,  // COMMON
        0xFD50,  // ARABIC
        0xFDFD,  // COMMON
        0xFE00,  // INHERITED
        0xFE10,  // COMMON
        0xFE20,  // INHERITED
        0xFE30,  // COMMON
        0xFE70,  // ARABIC
        0xFEFF,  // COMMON
        0xFF21,  // LATIN
        0xFF3B,  // COMMON
        0xFF41,  // LATIN
        0xFF5B,  // COMMON
        0xFF66,  // KATAKANA
        0xFF70,  // COMMON
        0xFF71,  // KATAKANA
        0xFF9E,  // COMMON
        0xFFA0,  // HANGUL
        0xFFE0,  // COMMON
        0x10000,  // LINEAR_B
        0x10100,  // COMMON
        0x10140,  // GREEK
        0x10190,  // COMMON
        0x101FD,  // INHERITED
        0x10280,  // LYCIAN
        0x102A0,  // CARIAN
        0x10300,  // OLD_ITALIC
        0x10330,  // GOTHIC
        0x10380,  // UGARITIC
        0x103A0,  // OLD_PERSIAN
        0x10400,  // DESERET
        0x10450,  // SHAVIAN
        0x10480,  // OSMANYA
        0x10800,  // CYPRIOT
        0x10840,  // IMPERIAL_ARAMAIC
        0x10900,  // PHOENICIAN
        0x10920,  // LYDIAN
        0x10980,  // MEROITIC_HIEROGLYPHS
        0x109A0,  // MEROITIC_CURSIVE
        0x10A00,  // KHAROSHTHI
        0x10A60,  // OLD_SOUTH_ARABIAN
        0x10B00,  // AVESTAN
        0x10B40,  // INSCRIPTIONAL_PARTHIAN
        0x10B60,  // INSCRIPTIONAL_PAHLAVI
        0x10C00,  // OLD_TURKIC
        0x10E60,  // ARABIC
        0x11000,  // BRAHMI
        0x11080,  // KAITHI
        0x110D0,  // SORA_SOMPENG
        0x11100,  // CHAKMA
        0x11180,  // SHARADA
        0x11680,  // TAKRI
        0x12000,  // CUNEIFORM
        0x13000,  // EGYPTIAN_HIEROGLYPHS
        0x16800,  // BAMUM
        0x16F00,  // MIAO
        0x1B000,  // KATAKANA
        0x1B001,  // HIRAGANA
        0x1D000,  // COMMON
        0x1D167,  // INHERITED
        0x1D16A,  // COMMON
        0x1D17B,  // INHERITED
        0x1D183,  // COMMON
        0x1D185,  // INHERITED
        0x1D18C,  // COMMON
        0x1D1AA,  // INHERITED
        0x1D1AE,  // COMMON
        0x1D200,  // GREEK
        0x1D300,  // COMMON
        0x1EE00,  // ARABIC
        0x1F000,  // COMMON
        0x1F200,  // HIRAGANA
        0x1F201,  // COMMON
        0x20000,  // HAN
        0xE0001,  // COMMON
        0xE0100,  // INHERITED
        0xE01F0 // E01F0..10FFFF; UNKNOWN
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
        UnicodeScript.UNKNOWN
    )
}
