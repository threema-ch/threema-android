/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

package ch.threema.app.emojis;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import static junit.framework.Assert.assertEquals;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MarkupParserTest {

	static class Utils {
		/**
		 * Parse a String, return a SpannableStringBuilder.
		 */
		static SpannableStringBuilder parse(String input) {
			final MarkupParser parser = MarkupParser.getInstance();
			SpannableStringBuilder builder = new SpannableStringBuilder(input);
			parser.markify(builder);
			return builder;
		}

		/**
		 * Expect a certain number of spans.
		 */
		static void expectSpanCount(SpannableStringBuilder parsed, int expectedCount) {
			int actualCount = parsed.getSpans(0, 9999, CharacterStyle.class).length;
			assertEquals(expectedCount, actualCount);
		}

		private static void expectBoldOrItalicAt(SpannableStringBuilder parsed, int style, int start, int end) {
			final StyleSpan[] spans = parsed.getSpans(start, end, StyleSpan.class);
			assertEquals(1, spans.length);
			assertEquals("Wrong span style", style, spans[0].getStyle());
			assertEquals("Wrong span start", start, parsed.getSpanStart(spans[0]));
			assertEquals("Wrong span end", end, parsed.getSpanEnd(spans[0]));
		}

		/**
		 * Expect a bold span at the specified range.
		 */
		static void expectBoldAt(SpannableStringBuilder parsed, int start, int end) {
			Utils.expectBoldOrItalicAt(parsed, Typeface.BOLD, start, end);
		}

		/**
		 * Expect an italic span at the specified range.
		 */
		static void expectItalicAt(SpannableStringBuilder parsed, int start, int end) {
			Utils.expectBoldOrItalicAt(parsed, Typeface.ITALIC, start, end);
		}

		/**
		 * Expect a strikethrough span at the specified range.
		 */
		static void expectStrikethroughAt(SpannableStringBuilder parsed, int start, int end) {
			final StrikethroughSpan[] spans = parsed.getSpans(start, end, StrikethroughSpan.class);
			assertEquals(1, spans.length);
			assertEquals(start, parsed.getSpanStart(spans[0]));
			assertEquals(end, parsed.getSpanEnd(spans[0]));
		}

		/**
		 * Input and output are identical, no spans.
		 */
		static void expectNoSpan(String input) {
			final SpannableStringBuilder parsed = Utils.parse(input);
			assertEquals(input, parsed.toString());
			Utils.expectSpanCount(parsed, 0);
		}
	}

	@Test
	public void parseBold() {
		final SpannableStringBuilder parsed = Utils.parse("hello *there*");
		assertEquals("hello there", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectBoldAt(parsed, 6, 11);
	}

	@Test
	public void parseItalic() {
		final SpannableStringBuilder parsed = Utils.parse("hello _there_");
		assertEquals("hello there", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectItalicAt(parsed, 6, 11);
	}

	@Test
	public void parseStrikethrough() {
		final SpannableStringBuilder parsed = Utils.parse("hello ~there~");
		assertEquals("hello there", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectStrikethroughAt(parsed, 6, 11);
	}

	@Test
	public void parseTwoBold() {
		final SpannableStringBuilder parsed = Utils.parse("two *bold* *parts*");
		assertEquals("two bold parts", parsed.toString());
		Utils.expectSpanCount(parsed, 2);
		Utils.expectBoldAt(parsed, 4, 8);
		Utils.expectBoldAt(parsed, 9, 14);
	}

	@Test
	public void parseTwoItalic() {
		final SpannableStringBuilder parsed = Utils.parse("two _italic_ _bits_");
		assertEquals("two italic bits", parsed.toString());
		Utils.expectSpanCount(parsed, 2);
		Utils.expectItalicAt(parsed, 4, 10);
		Utils.expectItalicAt(parsed, 11, 15);
	}

	@Test
	public void parseTwoStrikethrough() {
		final SpannableStringBuilder parsed = Utils.parse("two ~striked~ ~through~");
		assertEquals("two striked through", parsed.toString());
		Utils.expectSpanCount(parsed, 2);
		Utils.expectStrikethroughAt(parsed, 4, 11);
		Utils.expectStrikethroughAt(parsed, 12, 19);
	}

	@Test
	public void parseMixedMarkup() {
		final SpannableStringBuilder parsed = Utils.parse("*bold* and _italic_");
		assertEquals("bold and italic", parsed.toString());
		Utils.expectSpanCount(parsed, 2);
		Utils.expectBoldAt(parsed, 0, 4);
		Utils.expectItalicAt(parsed, 9, 15);
	}

	@Test
	public void parseMixedMarkupNested() {
		final SpannableStringBuilder parsed = Utils.parse("*bold with _italic_*");
		assertEquals("bold with italic", parsed.toString());

		final StyleSpan[] spans = parsed.getSpans(0, 9999, StyleSpan.class);
		assertEquals(2, spans.length);
		assertEquals("Wrong span start (1)", 0, parsed.getSpanStart(spans[0]));
		assertEquals("Wrong span end (1)", 16, parsed.getSpanEnd(spans[0]));
		assertEquals("Wrong span style (1)", Typeface.BOLD, spans[0].getStyle());
		assertEquals("Wrong span start (2)", 10, parsed.getSpanStart(spans[1]));
		assertEquals("Wrong span end (2)", 16, parsed.getSpanEnd(spans[1]));
		assertEquals("Wrong span style (2)", Typeface.ITALIC, spans[1].getStyle());
	}

	@Test
	public void atWordBoundaries1() {
		final SpannableStringBuilder parsed = Utils.parse("(*bold*)");
		assertEquals("(bold)", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectBoldAt(parsed, 1, 5);
	}

	@Test
	public void atWordBoundaries2() {
		final SpannableStringBuilder parsed = Utils.parse("¡*Threema* es fantástico!");
		assertEquals("¡Threema es fantástico!", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectBoldAt(parsed, 1, 8);
	}

	@Test
	public void atWordBoundaries3() {
		final SpannableStringBuilder parsed = Utils.parse("«_great_ service»");
		assertEquals("«great service»", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectItalicAt(parsed, 1, 6);
	}

	@Test
	public void atWordBoundaries4() {
		final SpannableStringBuilder parsed = Utils.parse("\"_great_ service\"");
		assertEquals("\"great service\"", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectItalicAt(parsed, 1, 6);
	}

	@Test
	public void atWordBoundaries5() {
		final SpannableStringBuilder parsed = Utils.parse("*bold*…");
		assertEquals("bold…", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectBoldAt(parsed, 0, 4);
	}

	@Test
	public void atWordBoundaries6() {
		final SpannableStringBuilder parsed = Utils.parse("_<a href=\"https://threema.ch\">Threema</a>_");
		assertEquals("<a href=\"https://threema.ch\">Threema</a>", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectItalicAt(parsed, 0, 40);
	}

	@Test
	public void onlyWordBoundaries1() {
		Utils.expectNoSpan("so not_really_italic");
	}

	@Test
	public void onlyWordBoundaries2() {
		Utils.expectNoSpan("invalid*bold*stuff");
	}

	@Test
	public void onlyWordBoundaries3() {
		Utils.expectNoSpan("no~strike~through");
	}

	@Test
	public void onlyWordBoundaries4() {
		Utils.expectNoSpan("<_< >_>");
	}

	@Test
	public void onlyWordBoundaries5() {
		Utils.expectNoSpan("<a href=\"https://threema.ch\">_Threema_</a>");
	}

	@Test
	public void onlyWordBoundaries6() {
		final SpannableStringBuilder parsed = Utils.parse("*bold_but_no~strike~through*");
		assertEquals("bold_but_no~strike~through", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectBoldAt(parsed, 0, 26);
	}

	@Test
	public void avoidBreakingUrls1() {
		Utils.expectNoSpan("https://example.com/_output_/");
	}

	@Test
	public void avoidBreakingUrls2() {
		Utils.expectNoSpan("https://example.com/*output*/");
	}

	@Test
	public void avoidBreakingUrls3() {
		Utils.expectNoSpan("https://example.com?__twitter_impression=true");
	}

	@Test
	public void avoidBreakingUrls4() {
		Utils.expectNoSpan("https://example.com?_twitter_impression=true");
	}

	@Test
	public void avoidBreakingUrls5() {
		final SpannableStringBuilder parsed = Utils.parse("https://en.wikipedia.org/wiki/Java_class_file *nice*");
		assertEquals("https://en.wikipedia.org/wiki/Java_class_file nice", parsed.toString());
		Utils.expectSpanCount(parsed, 1);
		Utils.expectBoldAt(parsed, 46, 50);
	}

	@Test
	public void avoidBreakingUrls6() {
		Utils.expectNoSpan("https://example.com/image_-_1.jpg");
	}

	@Test
	public void ignoreInvalidMarkup1() {
		Utils.expectNoSpan("*invalid markup (do not parse)_");
	}

	@Test
	public void ignoreInvalidMarkup2() {
		Utils.expectNoSpan("random *asterisk");
	}

	@Test
	public void notAcrossNewlines1() {
		Utils.expectNoSpan("*First line\n and a new one. (do not parse)*");
	}

	@Test
	public void notAcrossNewlines2() {
		Utils.expectNoSpan("*\nbegins with linebreak. (do not parse)*");
	}

	@Test
	public void notAcrossNewlines3() {
		Utils.expectNoSpan("*Just some text. But it ends with newline (do not parse)\n*");
	}

	@Test
	public void notAcrossNewlines4() {
		final SpannableStringBuilder parsed = Utils.parse("_*first line*\n*second* line_");
		assertEquals("_first line\nsecond line_", parsed.toString());
		Utils.expectSpanCount(parsed, 2);
		Utils.expectBoldAt(parsed, 1, 11);
		Utils.expectBoldAt(parsed, 12, 18);
	}
}
