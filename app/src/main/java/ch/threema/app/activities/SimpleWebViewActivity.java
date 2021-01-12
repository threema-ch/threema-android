/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.utils.ConfigUtils;

public abstract class SimpleWebViewActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener {
	private static final String DIALOG_TAG_NO_CONNECTION = "nc";
	private ProgressBar progressBar;
	private WebView webView;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(getWebViewTitle());
		}

		progressBar = findViewById(R.id.progress);
		webView = findViewById(R.id.simple_webview);
		webView.getSettings().setJavaScriptEnabled(false);
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
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		boolean result = super.initActivity(savedInstanceState);

		if (getConnectionIndicator() != null) {
			getConnectionIndicator().setVisibility(View.INVISIBLE);
		}
		return result;
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
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				break;
		}
		return false;
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		ConfigUtils.adjustToolbar(this, getToolbar());
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
}
