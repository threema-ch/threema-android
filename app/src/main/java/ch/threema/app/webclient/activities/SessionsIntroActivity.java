/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

@UiThread
public class SessionsIntroActivity extends ThreemaToolbarActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SessionsIntroActivity");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.webclient);
        }

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        final Button launchButton = findViewById(R.id.launch_button);
        final TextView linkText = findViewById(R.id.webclient_link);

        if (sharedPreferences.getBoolean(getString(R.string.preferences__web_client_welcome_shown), false)) {
            launchButton.setText(R.string.ok);
            linkText.setVisibility(View.VISIBLE);
            linkText.setText(Html.fromHtml("<a href=\"" + getString(R.string.webclient_info_url) + "\">" + getString(R.string.new_wizard_more_information) + "</a>"));
            linkText.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            linkText.setVisibility(View.GONE);
        }

        launchButton.setOnClickListener(v -> {
            v.setEnabled(false);
            sharedPreferences.edit().putBoolean(getString(R.string.preferences__web_client_welcome_shown), true).apply();
            setResult(RESULT_OK);
            finish();
        });
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.scroll_container),
            InsetSides.lbr()
        );
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_sessions_intro;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}
