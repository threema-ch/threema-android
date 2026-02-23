package ch.threema.app.emojis;

import org.junit.Assert;
import org.junit.Test;

public class EmojiMarkupUtilTest {
    private static void assertMatches(String string, String regex) {
        Assert.assertTrue(
            "String '" + string + "' does not match regex '" + regex + "'",
            string.matches(regex)
        );
    }

    private static void assertNoMatch(String string, String regex) {
        Assert.assertFalse(
            "String '" + string + "' should not match regex '" + regex + "'",
            string.matches(regex)
        );
    }

    @Test
    public void testRegexPositive() {
        // Gateway ID
        assertMatches("@[*THREEMA]", EmojiMarkupUtil.MENTION_REGEX);
        // Regular ID
        assertMatches("@[BNKTHA3X]", EmojiMarkupUtil.MENTION_REGEX);
        // Sandbox ID
        assertMatches("@[0PFX5ZXF]", EmojiMarkupUtil.MENTION_REGEX);
    }

    @Test
    public void testRegexNegative() {
        // Too long
        assertNoMatch("@[*THREEMAA]", EmojiMarkupUtil.MENTION_REGEX);
        // Disallowed characters
        assertNoMatch("@[BNK%HA3X]", EmojiMarkupUtil.MENTION_REGEX);
    }
}
