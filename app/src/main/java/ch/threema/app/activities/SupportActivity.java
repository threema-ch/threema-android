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


import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ProgressBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.UrlUtil;

public class SupportActivity extends ThreemaToolbarActivity {
	private static final Logger logger = LoggerFactory.getLogger(SupportActivity.class);
	private ProgressBar progressBar;

	@SuppressLint("SetJavaScriptEnabled")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.support);
		}

		progressBar = findViewById(R.id.progress);

		WebView wv = findViewById(R.id.simple_webview);
		wv.getSettings().setJavaScriptEnabled(true);
		wv.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int newProgress) {
				if (newProgress >= 99) {
					progressBar.setVisibility(View.INVISIBLE);
				} else {
					progressBar.setProgress(newProgress);
			}
		}
	});

		wv.loadUrl(getURL());
	}

	public int getLayoutResource() {
		return R.layout.activity_simple_webview;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
//				ActivityCompat.finishAfterTransition(this);
				finish();
				break;
		}
		return false;
	}

	private String getIdentity() {
		try {
			return URLEncoder.encode(serviceManager.getUserService().getIdentity(), LocaleUtil.UTF8_ENCODING);
		} catch (UnsupportedEncodingException e) {
			logger.error("Encoding exception", e);
		}
		return "";
	}

	private String getURL() {
		//try to load the custom url!
		String baseURL = null;

		if(ConfigUtils.isWorkBuild()) {
			baseURL = preferenceService.getCustomSupportUrl();
		}

		if(TestUtil.empty(baseURL)) {
			baseURL = getString(R.string.support_url);
		}

		return baseURL + "?lang=" + LocaleUtil.getAppLanguage()
			+ "&version=" + UrlUtil.urlencode(ConfigUtils.getDeviceInfo(this, true))
			+ "&identity=" + getIdentity();
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		ConfigUtils.adjustToolbar(this, getToolbar());
	}
}
