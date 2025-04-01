/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import ch.threema.app.R;
import ch.threema.app.ScreenshotTakingRule;
import ch.threema.app.notifications.BackgroundErrorNotification;
import ch.threema.app.testutils.TestHelpers;

import static ch.threema.app.PermissionRuleUtilsKt.getNotificationPermissionRule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BackgroundErrorNotificationTest {
    private UiDevice mDevice;

    @Rule
    public final RuleChain activityRule = ScreenshotTakingRule.getRuleChain().around(
        getNotificationPermissionRule()
    );

    @Before
    public void getDevice() {
        // Get device instance
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    /**
     * Dump the UI state (screenshot + UI XML) to the /sdcard/ directory.
     */
    @SuppressWarnings("unused") // Used for manual debugging
    private static void dumpState(@NonNull UiDevice device) throws IOException {
        device.takeScreenshot(new File("/sdcard/screenshot.png"));
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream("/sdcard/screenshot.uix"))) {
            // Note: Explicitly opening and closing stream since the UiAutomator dumpWindowHierarchy(File)
            // method leaks a file descriptor.
            device.dumpWindowHierarchy(stream);
        }
    }

    /**
     * Ensure that a notification is shown, without a "send to support" action.
     */
    @Test
    public void testNotificationWithoutAction() {
        // Go to home screen
        mDevice.pressHome();

        // Show notification
        final Context context = ApplicationProvider.getApplicationContext();
        BackgroundErrorNotification.showNotification(
            context,
            "T1tl3",
            "The body of the notification",
            "BackgroundErrorNotificationTest",
            false,
            null
        );

        // Get notification area object
        TestHelpers.openNotificationArea(mDevice);

        // Verify notification contents
        final BySelector titleSelector = By.res("android:id/title").text(context.getString(R.string.error) + ": T1tl3");
        final BySelector bodySelector = By.text("The body of the notification");
        assertNotNull("Notification title not found", mDevice.wait(Until.findObject(titleSelector), 1000));
        assertNotNull("Notification text not found", mDevice.wait(Until.findObject(bodySelector), 1000));

        // Ensure that no notifications are visible
        assertNull(
            "Actions found, but they shouldn't be there",
            mDevice.findObject(
                By
                    .pkg("com.android.systemui")
                    .res("com.android.systemui:id/notification_stack_scroller")
                    .hasDescendant(By.text(context.getString(R.string.send_to_support)))
            )
        );
    }

    /**
     * Ensure that a notification with "send to support" action works.
     */
    //@Test TODO danilo: Disabled until we have an empty test database
    public void testNotificationWithAction() {
        // Go to home screen
        mDevice.pressHome();

        // Show notification
        final Context context = ApplicationProvider.getApplicationContext();
        final String scope = "BackgroundErrorNotificationTest";
        final String notificationBody = "The body of the notification";
        BackgroundErrorNotification.showNotification(
            context,
            "T1tl3",
            notificationBody,
            scope,
            true,
            null
        );

        // Find notification
        TestHelpers.openNotificationArea(mDevice);

        // Find action
        final BySelector actionSelector = By
            .res("android:id/action0")
            .text(Pattern.compile(context.getString(R.string.send_to_support), Pattern.CASE_INSENSITIVE));
        final UiObject2 action = mDevice.findObject(actionSelector);
        assertNotNull("Action not found", action);

        // Click action
        action.click();

        // Wait for app to appear
        final BySelector chatPartnerSelector = By
            .pkg("ch.threema.app")
            .res("ch.threema.app:id/title");
        mDevice.wait(Until.findObject(chatPartnerSelector), 3000);
        final UiObject2 chatPartner = mDevice.findObject(chatPartnerSelector);

        // Ensure that we're talking to the support user
        assertEquals("*SUPPORT", chatPartner.getText());

        // Validate message
        final BySelector messageSelector = By
            .pkg("ch.threema.app")
            .res("ch.threema.app:id/embedded_text_editor");
        final String message = mDevice.findObject(
            messageSelector
        ).getText();
        assertTrue(message.contains("An error occurred in " + scope));
        assertTrue(message.contains(notificationBody));
        assertTrue(message.contains("My phone model"));
    }
}
