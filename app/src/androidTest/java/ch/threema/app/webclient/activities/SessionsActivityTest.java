/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

package ch.threema.app.webclient.activities;


import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.Date;

import androidx.preference.PreferenceManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import ch.threema.app.DangerousTest;
import ch.threema.app.R;
import ch.threema.app.ScreenshotTakingRule;
import ch.threema.app.ThreemaApplication;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.WebClientSessionModel;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static ch.threema.app.services.BrowserDetectionService.Browser;

/**
 * Sessions activity UI tests.
 * <p>
 * Prerequisites:
 * <p>
 * - Device with English locale and animations turned off (developer settings)
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
@DangerousTest // Modifies webclient sessions
public class SessionsActivityTest {
    @Rule
    public ActivityTestRule<SessionsActivity> activityTestRule
        = new ActivityTestRule<>(SessionsActivity.class, false, false);

    @Rule
    public final RuleChain activityRule = ScreenshotTakingRule.getRuleChain();

    /**
     * Mark the welcome screen as already shown.
     */
    private static void showWelcomeScreen(Context context, boolean show) {
        final SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context);
        final String welcome_shown = context.getString(R.string.preferences__web_client_welcome_shown);
        sharedPreferences.edit().putBoolean(welcome_shown, !show).commit();
    }

    /**
     * Enable or disable webclient.
     */
    private static void enableWebclient(Context context, boolean enable) {
        final SharedPreferences sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context);
        final String enabled = context.getString(R.string.preferences__web_client_enabled);
        sharedPreferences.edit().putBoolean(enabled, enable).commit();
    }

    /**
     * Clear all sessions.
     */
    private static void clearSessions() {
        final DatabaseServiceNew databaseService = ThreemaApplication
            .getServiceManager()
            .getDatabaseServiceNew();
        databaseService.getWebClientSessionModelFactory().deleteAll();
    }

    /**
     * Create a new database session.
     */
    private static void createSession(
        String label,
        WebClientSessionModel.State state,
        boolean persistent,
        Date created,
        Date lastConnection,
        Browser browser
    ) {
        final DatabaseServiceNew databaseService = ThreemaApplication
            .getServiceManager()
            .getDatabaseServiceNew();

        final WebClientSessionModel model = new WebClientSessionModel();

        model.setLabel(label);
        model.setState(state);
        model.setPersistent(persistent);
        model.setCreated(created);
        model.setLastConnection(lastConnection);
        switch (browser) {
            case FIREFOX:
                model.setClientDescription("Mozilla/5.0 (X11; Linux i686; rv:10.0) Gecko/20100101 Firefox/10.0");
                break;
            case CHROME:
                model.setClientDescription("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36");
                break;
            case SAFARI:
                model.setClientDescription("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Safari/604.1.38");
                break;
            case EDGE:
                model.setClientDescription("edge");
                break;
            case OPERA:
                model.setClientDescription("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.87 Safari/537.36 OPR/54.0.2952.46\n");
                break;
        }
        model.setSaltyRtcHost("saltyrtc.threema.example");
        model.setSaltyRtcPort(8080);

        databaseService.getWebClientSessionModelFactory().createOrUpdate(model);
    }

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getTargetContext();

        // By default, don't show welcome screen
        showWelcomeScreen(context, false);

        // Clear all sessions
        clearSessions();

        // Disable webclient
        enableWebclient(context, false);
    }

    /**
     * Ensure that the welcome screen is shown
     * the first time the activity is started.
     */
    @Test
    public void testWelcomeScreen() {
        showWelcomeScreen(InstrumentationRegistry.getTargetContext(), true);

        final Instrumentation.ActivityMonitor monitor = getInstrumentation()
            .addMonitor(SessionsIntroActivity.class.getName(), null, false);

        activityTestRule.launchActivity(null);

        final Activity activity = getInstrumentation()
            .waitForMonitorWithTimeout(monitor, 500);
        Assert.assertNotNull(activity);
    }

    /**
     * Test the session list. Show two sessions, and delete one of them.
     */
    @Test
    public void testSessionList() {
        // Create two sessions
        createSession("Feuerfuchs", WebClientSessionModel.State.AUTHORIZED,
            true, new Date(), new Date(), Browser.FIREFOX);
        createSession("Googlebrowser", WebClientSessionModel.State.ERROR,
            true, new Date(System.currentTimeMillis() - 3600),
            new Date(System.currentTimeMillis() - 3500), Browser.CHROME);

        // Start activty
        activityTestRule.launchActivity(null);

        // Assert that the two sessions are listed
        onView(withText("Feuerfuchs"))
            .check(matches(isDisplayed()));
        onView(withText("Googlebrowser"))
            .check(matches(isDisplayed()));

        // Delete Chrome session
        onView(withText("Googlebrowser"))
            .perform(click());
        onView(withText(R.string.webclient_session_remove))
            .perform(click());
        onView(withText(R.string.ok))
            .inRoot(isDialog())
            .perform(click());

        // Assert that the session is gone
        onView(withText("Googlebrowser"))
            .check(doesNotExist());
    }

    /**
     * Test the cleaning of non persistent sessions on start.
     */
    @Test
    public void testCleanOnStart() throws Exception {
        final long hours = 3600000;

        final Date now = new Date();
        final Date hours23ago = new Date(System.currentTimeMillis() - hours * 23);
        final Date hours25ago = new Date(System.currentTimeMillis() - hours * 25);

        createSession("Persistent now", WebClientSessionModel.State.AUTHORIZED,
            true, now, now, Browser.FIREFOX);
        createSession("Persistent old", WebClientSessionModel.State.AUTHORIZED,
            true, hours25ago, hours25ago, Browser.CHROME);
        createSession("Disposable now", WebClientSessionModel.State.AUTHORIZED,
            false, now, now, Browser.SAFARI);
        createSession("Disposable fresh", WebClientSessionModel.State.AUTHORIZED,
            false, now, null, Browser.SAFARI);
        createSession("Disposable still valid", WebClientSessionModel.State.AUTHORIZED,
            false, hours23ago, hours23ago, Browser.OPERA);
        createSession("Disposable expired", WebClientSessionModel.State.AUTHORIZED,
            false, hours25ago, hours25ago, Browser.EDGE);

        activityTestRule.launchActivity(null);

        onView(withText("Persistent now"))
            .check(matches(isDisplayed()));
        onView(withText("Persistent old"))
            .check(matches(isDisplayed()));
        onView(withText("Disposable now"))
            .check(matches(isDisplayed()));
        onView(withText("Disposable fresh"))
            .check(matches(isDisplayed()));
        onView(withText("Disposable still valid"))
            .check(matches(isDisplayed()));
        onView(withText("Disposable expired"))
            .check(doesNotExist());
    }

}
