/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

import android.text.Spannable
import android.text.SpannableString
import android.text.style.URLSpan
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkifyUtilTest {

    /**
     * Get the spannable and a list of the URL spans as a pair. If there is no spannable, a pair
     * containing of null and an empty list is returned.
     */
    private fun getSpanPair(text: String, includePhoneNumbers: Boolean = true): Pair<Spannable?, List<URLSpan>> {
        val textView = TextView(InstrumentationRegistry.getInstrumentation().context)
        textView.text = text
        InstrumentationRegistry.getInstrumentation().runOnMainSync{
            LinkifyUtil.getInstance().linkifyText(textView, includePhoneNumbers)
        }
        val spannableText = textView.text
        if (spannableText !is SpannableString) {
            return null to listOf()
        }
        val spans = spannableText.getSpans(0, text.length + 1, URLSpan::class.java).toList()
        return spannableText to spans
    }

    /**
     * Expects that there are the spans as defined by the given set of span starts and ends.
     */
    private fun assertSpans(text: String, spanPoints: Set<Pair<Int, Int>>, includePhoneNumbers: Boolean = true) {
        val (spannable, spans) = getSpanPair(text, includePhoneNumbers)
        assert(spannable != null || spans.isEmpty())
        val actualSpanPoints = spans.map { spannable!!.getSpanStart(it) to spannable.getSpanEnd(it) }.toSet()
        assertEquals(spanPoints, actualSpanPoints)
    }

    /**
     * Expects that there is one single span over the entire string.
     */
    private fun assertSingleSpan(text: String, includePhoneNumbers: Boolean = true) {
        assertSpans(text, setOf(0 to text.length), includePhoneNumbers)
    }

    /**
     * Expects that there is one single span over the entire string except the first and last
     * 'boundary' character.
     */
    private fun assertSingleBoundSpan(text: String, includePhoneNumbers: Boolean = true) {
        assertSpans(text, setOf(1 to text.length - 1), includePhoneNumbers)
    }

    /**
     * Expects that there are no spans in the given string.
     */
    private fun assertNoSpan(text: String, includePhoneNumbers: Boolean = true) {
        assertSpans(text, setOf(), includePhoneNumbers)
    }

    @Test
    fun testSimpleUrls() {
        assertSingleSpan("www.threema.ch")
        assertSingleSpan("a.b.c.d.e.f.threema.ch")
        assertSingleSpan("https://www.threema.ch")
    }

    @Test
    fun testInvalidUrls() {
        assertNoSpan("www. threema .ch")
        assertNoSpan("www.threema .ch")
        assertNoSpan("www,threema,ch")
    }

    @Test
    fun testSimpleGeoUris() {
        assertSingleSpan("geo:12.21334534521,19.50")
        assertSingleSpan("geo:15.4,-19.50")
        assertSingleSpan("geo:-1.4,19.50")
        assertSingleSpan("geo:-30.4,-22.5057;l=hallo!;b=23.1")
        assertSingleSpan("geo:12.2,12")
        assertSingleSpan("geo:1,2")
        assertSingleSpan("geo:-3,4")
        assertSingleSpan("geo:5,-6")
        assertSingleSpan("geo:-7,-8")
        assertSingleSpan("geo:-7,-8,-9")
        assertSingleSpan("geo:-7,-8,-9;parameter=12+2")
        assertSingleSpan("geo:12.2,12;u=23.234;label=1234;otherLabel=5678.9")
    }

    @Test
    fun testInvalidGeoUris() {
        assertNoSpan("geo:198.05,190.1")
        assertNoSpan("geo:181.01,30.5")
        assertNoSpan("geom:10.01,30.5")
        assertNoSpan("geo:10.01")
        assertNoSpan("geo:10.01,")
        assertNoSpan("geo:10.01,;")
        assertNoSpan("geo:10.01,.;")
        assertNoSpan("geo:10.01,a")
        assertNoSpan("geo:10.01,-a")
        assertNoSpan("ge:10.01;30.5")
        assertNoSpan("geo:10.01.1,30.5")
        assertNoSpan("geo:10.01,30e.5")
        assertNoSpan("geo:a10.01,30.5")
    }

    @Test
    fun testBoundaryGeoUris() {
        assertSingleBoundSpan(",geo:12.21334534521,19.50,")
        assertSingleBoundSpan("(geo:15.4,-19.50)")
        assertSingleBoundSpan(":geo:-1.4,19.50:")
        assertSingleBoundSpan(" geo:-30.4,-22.5057;l=hallo!;b=23.1 ")
    }

    @Test
    fun testMixedGeoUris() {
        assertSpans("geo:1,2 geo:1,2", setOf(0 to 7, 8 to 15))
        assertSpans("geo:1,2\ngeo:1,2", setOf(0 to 7, 8 to 15))
        assertSpans("geo:1,2\nthreema.ch", setOf(0 to 7, 8 to 18))
    }

    @Test
    fun testAndroidGeoUris() {
        assertSingleSpan("geo:37.786971,-122.399677?q=37.786971,-122.399677(This+is+the+geo-label)")
        assertSingleSpan("geo:37.786971,-122.399677?q=37.786971,-122.399677")
        assertSingleSpan("geo:0,0?q=37.786971,-122.399677")
        assertSingleSpan("geo:0,0?q=37.786971,-122.399677(With+label)")

        assertSingleSpan("geo:0,0?z=21")
        assertSingleSpan("geo:0,0?z=1")

        // label should not count to the geo uri (because there is a query needed for labels)
        assertSpans("geo:37.786971,-122.399677(This+is+the+label+without+query)", setOf(0 to 25))
        // label should not count to the geo uri (because it is incomplete)
        assertSpans("geo:0,0?q=37.786971,-122.399677(With+non-closing-label", setOf(0 to 31))

        assertSpans("geo:0,0?z=3.1", setOf(0 to 11))
        assertSpans("geo:0,0?z=12(Label+not+allowed+here)", setOf(0 to 12))
    }

}
