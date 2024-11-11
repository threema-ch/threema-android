/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

package ch.threema.app.utils;

import org.junit.Test;

import java.util.Date;

import androidx.annotation.Nullable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestUtilTest {

    @Test
    public void nullable() {
        Object nullObject = null;
        Object notNullObject = new Object();

        assertTrue(TestUtil.compare(nullObject, nullObject));
        assertFalse(TestUtil.compare(nullObject, notNullObject));
        assertTrue(TestUtil.compare(notNullObject, notNullObject));
        assertFalse(TestUtil.compare(notNullObject, nullObject));
    }

    @Test
    public void string() {

        assertTrue(TestUtil.compare("Threema", "Threema"));

        assertFalse(TestUtil.compare("Threema", "threema"));

        assertFalse(TestUtil.compare("Threema", " Threema"));

        assertFalse(TestUtil.compare("Threema", "Threema "));

        assertFalse(TestUtil.compare("Threema", null));
    }

    @Test
    public void integer() {
        assertTrue(TestUtil.compare(100, 100));

        assertFalse(TestUtil.compare(100, 101));

        assertFalse(TestUtil.compare(100, null));
    }

    @Test
    public void byteArray() {
        assertTrue(TestUtil.compare(new byte[]{1, 2, 3, 4}, new byte[]{1, 2, 3, 4}));

        //different size
        assertFalse(TestUtil.compare(new byte[]{1, 2, 3, 4}, new byte[]{1, 2, 3}));
        assertFalse(TestUtil.compare(new byte[]{1, 2, 3, 4}, new byte[]{1, 2, 3, 5}));
    }

    @Test
    public void array() {

        //strings
        assertTrue(TestUtil.compare(
            new Object[]{"string1", "string2", "string3"},
            new Object[]{"string1", "string2", "string3"}));

        assertFalse(TestUtil.compare(
            new Object[]{"string1", "string2", "string3"},
            new Object[]{"string1", "string2"}));

        assertFalse(TestUtil.compare(
            new Object[]{"string1", "string2", "string3"},
            new Object[]{"string1", "string2", "string5"}));

        //int
        assertTrue(TestUtil.compare(
            new Object[]{1, 2, 3, 4},
            new Object[]{1, 2, 3, 4}));

        assertFalse(TestUtil.compare(
            new Object[]{1, 2, 3, 4},
            new Object[]{1, 2, 3}));

        assertFalse(TestUtil.compare(
            new Object[]{1, 2, 3, 4},
            new Object[]{1, 2, 3, 5}));

        //mixed!!
        assertTrue(TestUtil.compare(
            new Object[]{"a", 1, 2.0, new byte[]{3}},
            new Object[]{"a", 1, 2.0, new byte[]{3}}));


        assertFalse(TestUtil.compare(
            new Object[]{"a", 1, 2.0, new byte[]{3}},
            new Object[]{"a", 1, 2.0, new byte[]{4}}));

        assertFalse(TestUtil.compare(
            new Object[]{"a", 1},
            new Object[]{"a", 1, 2.0, new byte[]{3}}));

    }

    @Test
    public void date() {
        Date date1Instance1 = new Date(2000, 2, 2);
        Date date1Instance2 = new Date(2000, 2, 2);
        Date date2Instance1 = new Date(2000, 3, 2);
        Date date2Instance2 = new Date(2000, 3, 2);

        assertTrue(TestUtil.compare(
            date1Instance1,
            date1Instance2));

        assertTrue(TestUtil.compare(
            date2Instance1,
            date2Instance2));

        assertFalse(TestUtil.compare(
            date1Instance1,
            date2Instance1));

        assertFalse(TestUtil.compare(
            date1Instance2,
            date2Instance2));

    }

    @Test
    public void testMatchesConversationSearch() {
        assertTrue(TestUtil.matchesConversationSearch("aaaaaaa", "aàáâãäå"));
        assertTrue(TestUtil.matchesConversationSearch("eeeee", "eèéêë"));
        assertTrue(TestUtil.matchesConversationSearch("iiiii", "iíìîï"));
        assertTrue(TestUtil.matchesConversationSearch("oooooo", "oóòôöõ"));
        assertTrue(TestUtil.matchesConversationSearch("uuuuu", "uüúùû"));
        assertTrue(TestUtil.matchesConversationSearch("n", "ñ"));
        assertTrue(TestUtil.matchesConversationSearch("c", "ç"));

        assertTrue(TestUtil.matchesConversationSearch("A", "ä"));
        assertTrue(TestUtil.matchesConversationSearch("a", "ä"));
        assertTrue(TestUtil.matchesConversationSearch("a", "Ä"));
        assertTrue(TestUtil.matchesConversationSearch("A", "Ä"));
        assertFalse(TestUtil.matchesConversationSearch("Ä", "a"));

        assertFalse(TestUtil.matchesConversationSearch(null, "a"));
        assertFalse(TestUtil.matchesConversationSearch("a", null));
    }

    @Test
    public void isEmptyOrNullShouldReturnTrueWhenStringEmpty() {

        // arrange
        final String input = "";

        // act
        final boolean result = TestUtil.isEmptyOrNull(input);

        // assert
        assertTrue(result);
    }

    /**
     * @noinspection ConstantValue
     */
    @Test
    public void isEmptyOrNullShouldReturnTrueWhenStringNull() {

        // arrange
        final String input = null;

        // act
        final boolean result = TestUtil.isEmptyOrNull(input);

        // assert
        assertTrue(result);
    }

    @Test
    public void isEmptyOrNullShouldReturnFalseWhenStringBlank() {

        // arrange
        final String input = "   ";

        // act
        final boolean result = TestUtil.isEmptyOrNull(input);

        // assert
        assertFalse(result);
    }

    @Test
    public void isEmptyOrNullShouldReturnFalseWhenStringContainsContent() {

        // arrange
        final String input = "abc";

        // act
        final boolean result = TestUtil.isEmptyOrNull(input);

        // assert
        assertFalse(result);
    }

    @Test
    public void isEmptyOrNullShouldReturnFalseWhenStringContainsContentWithSpaces() {

        // arrange
        final String input = "  a  b  c  ";

        // act
        final boolean result = TestUtil.isEmptyOrNull(input);

        // assert
        assertFalse(result);
    }

    @Test
    public void isBlankOrNullShouldReturnTrueWhenCharSequenceBlank() {

        // arrange
        final CharSequence input = "   ";

        // act
        final boolean result = TestUtil.isBlankOrNull(input);

        // assert
        assertTrue(result);
    }

    @Test
    public void isBlankOrNullShouldReturnTrueWhenCharSequenceEmpty() {

        // arrange
        final CharSequence input = "";

        // act
        final boolean result = TestUtil.isBlankOrNull(input);

        // assert
        assertTrue(result);
    }

    /**
     * @noinspection ConstantValue
     */
    @Test
    public void isBlankOrNullShouldReturnTrueWhenCharSequenceNull() {

        // arrange
        final @Nullable CharSequence input = null;

        // act
        final boolean result = TestUtil.isBlankOrNull(input);

        // assert
        assertTrue(result);
    }

    @Test
    public void isBlankOrNullShouldReturnFalseWhenCharSequenceContainsContent() {

        // arrange
        final CharSequence input = "abc";

        // act
        final boolean result = TestUtil.isBlankOrNull(input);

        // assert
        assertFalse(result);
    }

    @Test
    public void isBlankOrNullShouldReturnFalseWhenCharSequenceContainsContentWithSpaces() {

        // arrange
        final CharSequence input = "  a  b  c  ";

        // act
        final boolean result = TestUtil.isBlankOrNull(input);

        // assert
        assertFalse(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnTrueWhenStringEmpty() {
        // arrange
        final String[] inputs = {""};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertTrue(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnFalseWhenStringNotEmpty() {
        // arrange
        final String[] inputs = {"a"};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertFalse(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnFalseWhenStringBlank() {
        // arrange
        final String[] inputs = {"   "};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertFalse(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnTrueWhenStringsAreEmpty() {
        // arrange
        final String[] inputs = {"", "", "", ""};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertTrue(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnFalseWhenStringsAreNotEmpty1() {
        // arrange
        final String[] inputs = {"", "", "a", ""};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertFalse(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnFalseWhenStringsAreNotEmpty2() {
        // arrange
        final String[] inputs = {"", "", " ", ""};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertFalse(result);
    }

    /**
     * @noinspection ConstantValue
     */
    @Test
    public void vararg_isEmptyOrNullShouldReturnTrueWhenStringsNull() {
        // arrange
        final String[] inputs = null;

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertTrue(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnTrueWhenAllStringsNull() {
        // arrange
        final String[] inputs = {null, null, null};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertTrue(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnTrueWhenAllStringsNullOrEmpty() {
        // arrange
        final String[] inputs = {null, "", null, ""};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertTrue(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnFalseInputMixed() {
        // arrange
        final String[] inputs = {null, "", null, "", "abc", "   ", null};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertFalse(result);
    }

    @Test
    public void vararg_isEmptyOrNullShouldReturnTrueNoInput() {
        // arrange
        final String[] inputs = {};

        // act
        final boolean result = TestUtil.isEmptyOrNull(inputs);

        // assert
        assertTrue(result);
    }
}
