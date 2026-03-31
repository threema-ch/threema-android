package ch.threema.app.utils;

import org.junit.Test;

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
}
