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

package ch.threema.app.activities;

import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.slf4j.Logger;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WhatsNewActivity extends ThreemaAppCompatActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WhatsNewActivity");

    public static final String EXTRA_NO_ANIMATION = "noanim";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        ConfigUtils.configureSystemBars(this);

        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        setContentView(R.layout.activity_whatsnew);

        String title = getString(R.string.whatsnew_title, BuildConfig.VERSION_NAME);
        CharSequence body = Html.fromHtml(getString(R.string.whatsnew_headline));

        ((TextView) findViewById(R.id.whatsnew_title)).setText(title);
        ((TextView) findViewById(R.id.whatsnew_body)).setText(body);

        findViewById(R.id.next_text).setOnClickListener(v -> finish());

        if (!getIntent().getBooleanExtra(EXTRA_NO_ANIMATION, false)) {
            LinearLayout buttonLayout = findViewById(R.id.button_layout);
            if (savedInstanceState == null) {
                buttonLayout.setVisibility(View.GONE);
                buttonLayout.postDelayed(() -> AnimationUtil.slideInFromBottomOvershoot(buttonLayout), 200);
            }
        }
    }
}
