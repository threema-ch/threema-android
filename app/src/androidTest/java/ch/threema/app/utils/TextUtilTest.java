package ch.threema.app.utils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextUtilTest {

    @Test
    public void testMatchesQueryDiacriticInsensitive() {
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("aàáâãäå", "aaaaaaa"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("eèéêë", "eeeee"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("iíìîï", "iiiii"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("oóòôöõ", "oooooo"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("uüúùû", "uuuuu"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("ñ", "n"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("ç", "c"));

        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("ä", "A"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("ä", "a"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("Ä", "a"));
        Assert.assertTrue(TextUtil.matchesQueryDiacriticInsensitive("Ä", "A"));
        Assert.assertFalse(TextUtil.matchesQueryDiacriticInsensitive("a", "Ä"));

        Assert.assertFalse(TextUtil.matchesQueryDiacriticInsensitive("a", null));
        Assert.assertFalse(TextUtil.matchesQueryDiacriticInsensitive(null, "a"));
    }
}
