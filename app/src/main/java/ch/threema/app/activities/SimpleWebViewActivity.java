/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import ch.threema.app.R;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.utils.ConfigUtils;

/**
 * Warning! Do not start an Activity extending this class from an application context!
 */
public abstract class SimpleWebViewActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener {

    public static final String FORCE_DARK_THEME = "darkTheme";
    private static final String DIALOG_TAG_NO_CONNECTION = "nc";
    private LinearProgressIndicator progressBar;
    private WebView webView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        MaterialToolbar toolbar = findViewById(R.id.material_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        toolbar.setTitle(getWebViewTitle());

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        final boolean darkThemeForced;

        if (extras != null && extras.getBoolean(FORCE_DARK_THEME, false)) {
            darkThemeForced = true;
            if (getConnectionIndicator() != null) {
                // hide connection indicator when launched from wizard
                getConnectionIndicator().setVisibility(View.INVISIBLE);
            }
        } else {
            darkThemeForced = false;
        }

        if (!ConfigUtils.isTheDarkSide(this)) {
            if (darkThemeForced) {
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        }

        progressBar = findViewById(R.id.progress);
        webView = findViewById(R.id.simple_webview);
        webView.getSettings().setJavaScriptEnabled(requiresJavaScript());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.webview_scroller), (v, insets) -> {
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (requiresConnection()) {
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    if (newProgress >= 99) {
                        progressBar.setVisibility(View.INVISIBLE);
                    } else {
                        progressBar.setProgress(newProgress);
                    }
                }
            });
            checkConnection();
        } else {
            progressBar.setVisibility(View.GONE);

            loadWebView();
        }
    }

    private void loadWebView() {
        webView.loadUrl(getWebViewUrl());
    }

    private void checkConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null && connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected()) {
            loadWebView();
        } else {
            GenericAlertDialog.newInstance(getWebViewTitle(), R.string.internet_connection_required, R.string.retry, R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_NO_CONNECTION);
        }
    }

    public int getLayoutResource() {
        return R.layout.activity_simple_webview;
    }

    @Override
    public void onYes(String tag, Object data) {
        checkConnection();
    }

    @Override
    public void onNo(String tag, Object data) {
        finish();
    }

    protected abstract @StringRes int getWebViewTitle();

    protected abstract String getWebViewUrl();

    protected boolean requiresConnection() {
        return true;
    }

    protected boolean requiresJavaScript() {
        return false;
    }
}

