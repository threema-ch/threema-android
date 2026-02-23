package ch.threema.app.utils;

import org.junit.Assert;
import org.junit.Test;

import ch.threema.domain.protocol.ThreemaFeature;

public class ThreemaFeatureTest {

    // TODO(ANDR-2708): Remove
    @Test
    public void testFeatureMaskToLevel() {
        final int level0 = 0x00;
        final int level1 = 0x03;
        final int level2 = 0x07;
        final int level3 = 0x0f;

        Assert.assertEquals(0, ThreemaFeature.featureMaskToLevel(level0));
        Assert.assertEquals(1, ThreemaFeature.featureMaskToLevel(level1));
        Assert.assertEquals(2, ThreemaFeature.featureMaskToLevel(level2));
        Assert.assertEquals(3, ThreemaFeature.featureMaskToLevel(level3));

        Assert.assertEquals(0, ThreemaFeature.featureMaskToLevel(ThreemaFeature.AUDIO));
        Assert.assertEquals(1, ThreemaFeature.featureMaskToLevel(ThreemaFeature.GROUP_CHAT));
        Assert.assertEquals(2, ThreemaFeature.featureMaskToLevel(ThreemaFeature.BALLOT));
        Assert.assertEquals(3, ThreemaFeature.featureMaskToLevel(ThreemaFeature.FILE));
        Assert.assertEquals(0, ThreemaFeature.featureMaskToLevel(ThreemaFeature.VOIP));
    }

    @Test
    public void testFeatureMaskBuilder() {
        final long maskAllFeatures = new ThreemaFeature.Builder()
            .audio(true)
            .group(true)
            .ballot(true)
            .file(true)
            .voip(true)
            .videocalls(true)
            .forwardSecurity(true)
            .groupCalls(true)
            .editMessages(true)
            .deleteMessages(true)
            .emojiReactions(true)
            .build();
        Assert.assertEquals(2047, maskAllFeatures);

        final long maskPartialFeatures = new ThreemaFeature.Builder()
            .audio(true)
            .group(false)
            .ballot(true)
            .file(false)
            .voip(true)
            .videocalls(false)
            .forwardSecurity(true)
            .groupCalls(false)
            .editMessages(true)
            .deleteMessages(false)
            .emojiReactions(true)
            .build();
        Assert.assertEquals(1365, maskPartialFeatures);
    }

}
