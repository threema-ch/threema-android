/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
@RunWith(PowerMockRunner.class)
@PrepareForTest(AppRestrictionUtil.class)
public class AppRestrictionUtilTest {

    private void assertWebHosts(@Nullable String stringVal, @Nullable List<String> expected) {
        // Mock getStringRestriction method
        PowerMockito.stub(
            PowerMockito.method(AppRestrictionUtil.class, "getStringRestriction", String.class)
        ).toReturn(stringVal);

        // Mock context
        final Context context = Mockito.mock(Context.class);

        // Parse web hosts
        final List<String> result = AppRestrictionUtil.getWebHosts(context);
        if (expected == null) {
            Assert.assertNull(result);
        } else if (result == null) {
            Assert.fail("Result is null, but expected list of strings");
        } else {
            final Object[] expectedArray = expected.toArray();
            final Object[] resultArray = result.toArray();
            Assert.assertArrayEquals(expectedArray, resultArray);
        }
    }

    @Test
    public void getWebHostsNull() {
        this.assertWebHosts(null, null);
    }

    @Test
    public void getWebHostsEmpty() {
        this.assertWebHosts("", null);
    }

    @Test
    public void getWebHostsSingle() {
        this.assertWebHosts("saltyrtc.threema.ch", Arrays.asList("saltyrtc.threema.ch"));
    }

    @Test
    public void getWebHostsEmptyList() {
        this.assertWebHosts(",", null);
    }

    @Test
    public void getWebHostsMultiple() {
        this.assertWebHosts(
            "saltyrtc.threema.ch,test.threema.ch,*.example.com",
            Arrays.asList("saltyrtc.threema.ch", "test.threema.ch", "*.example.com")
        );
    }

    @Test
    public void getWebHostsFilterEmpty() {
        this.assertWebHosts(
            "saltyrtc.threema.ch,,foo.bar",
            Arrays.asList("saltyrtc.threema.ch", "foo.bar")
        );
    }

    @Test
    public void getWebHostsTrim() {
        this.assertWebHosts("  saltyrtc.threema.ch  ,, ", Arrays.asList("saltyrtc.threema.ch"));
    }

    private void assertWebHostAllowed(@Nullable List<String> whitelist, @NonNull String hostname, boolean allowed) {
        // Mock getWebHosts method
        PowerMockito.stub(
            PowerMockito.method(AppRestrictionUtil.class, "getWebHosts", Context.class)
        ).toReturn(whitelist);

        // Mock context
        final Context context = Mockito.mock(Context.class);

        // Validate hostname
        Assert.assertEquals(allowed, AppRestrictionUtil.isWebHostAllowed(context, hostname));
    }

    @Test
    public void isWebHostAllowedNo() {
        assertWebHostAllowed(Arrays.asList("example.com"), "threema.ch", false);
        assertWebHostAllowed(Arrays.asList("example.com"), "x.example.com", false);
        assertWebHostAllowed(Arrays.asList("*.example.com"), "x.example", false);
    }

    @Test
    public void isWebHostAllowedNoEmptyList() {
        assertWebHostAllowed(new ArrayList<>(), "example.com", false);
    }

    @Test
    public void isWebHostAllowedYesExact() {
        assertWebHostAllowed(Arrays.asList("example.com"), "example.com", true);
        assertWebHostAllowed(Arrays.asList("example.com", "x.example.com"), "x.example.com", true);
    }

    @Test
    public void isWebHostAllowedYesNullList() {
        assertWebHostAllowed(null, "example.com", true);
    }

    @Test
    public void isWebHostAllowedYesPrefixMatch() {
        assertWebHostAllowed(Arrays.asList("example.com", "*.example.com"), "x.example.com", true);
        assertWebHostAllowed(Arrays.asList("example.com", "*.example.com"), "xyz.example.com", true);
        assertWebHostAllowed(Arrays.asList("example.com", "*.example.com"), "x.y.example.com", true);
    }

}
