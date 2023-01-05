/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;
import androidx.lifecycle.LifecycleOwner;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.ui.QRCodePopup;
import ch.threema.app.ui.TooltipPopup;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;

public class ExportIDResultActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener, LifecycleOwner {
	private static final String DIALOG_TAG_QUIT_CONFIRM = "qconf";
	private static final int QRCODE_SMALL_DIMENSION_PIXEL = 200;

	private Bitmap qrcodeBitmap;
	private WebView printWebView;
	private Toolbar toolbar;
	private TooltipPopup tooltipPopup;

	private String identity, backupData;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(this.toolbar);
		ActionBar actionBar = getSupportActionBar();

		if (actionBar == null) {
			finish();
			return;
		}

		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setTitle("");
		if (ConfigUtils.getAppTheme(this) != ConfigUtils.THEME_DARK) {
			actionBar.setHomeAsUpIndicator(R.drawable.ic_check);
		}

		this.backupData = this.getIntent().getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP);
		this.identity = this.getIntent().getStringExtra(ThreemaApplication.INTENT_DATA_CONTACT);

		if (TestUtil.empty(this.backupData)) {
			finish();
			return;
		}

		displayIDBackup();

		if (savedInstanceState == null) {
			showTooltip();
		}
	}

	private void displayIDBackup() {
		ScrollView layoutContainer = findViewById(R.id.qr_container_backup);
		layoutContainer.setVisibility(View.VISIBLE);

		TextView textView = findViewById(R.id.threemaid);
		textView.setText(backupData);

		final ImageView imageView = findViewById(R.id.qrcode_backup);
		this.qrcodeBitmap = serviceManager.getQRCodeService().getRawQR(backupData, false, QRCodeServiceImpl.QR_TYPE_ID_EXPORT);

		final int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, QRCODE_SMALL_DIMENSION_PIXEL, getResources().getDisplayMetrics());
		Bitmap bmpScaled = Bitmap.createScaledBitmap(qrcodeBitmap, px, px, false);
		bmpScaled.setDensity(Bitmap.DENSITY_NONE);
		imageView.setImageBitmap(bmpScaled);
		imageView.setOnClickListener(v -> new QRCodePopup(ExportIDResultActivity.this, getWindow().getDecorView(), ExportIDResultActivity.this).show(v, backupData, QRCodeServiceImpl.QR_TYPE_ID_EXPORT));
	}

	private void showTooltip() {
		if (!preferenceService.getIsExportIdTooltipShown()) {
			getToolbar().postDelayed(() -> {
				tooltipPopup = new TooltipPopup(this, R.string.preferences__tooltip_export_id_shown, R.layout.popup_tooltip_top_right, this);
				tooltipPopup.show(this, getToolbar(), getString(R.string.tooltip_export_id), TooltipPopup.ALIGN_BELOW_ANCHOR_ARROW_RIGHT, 5000);
			}, 1000);
		}
	}

	private void done() {
		GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
					R.string.backup_id,
					R.string.really_leave_id_export,
					R.string.ok,
					R.string.back);
		dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_QUIT_CONFIRM);
	}

	@Override
	public int getLayoutResource() {
		return R.layout.activity_export_id;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_export_id, menu);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem printMenu = menu.findItem(R.id.menu_print);
		printMenu.setVisible(true);

		return super.onPrepareOptionsMenu(menu);
	}

	private void createWebPrintJob(WebView webView) {

		PrintManager printManager = (PrintManager) this
				.getSystemService(Context.PRINT_SERVICE);

		PrintDocumentAdapter printAdapter;
		printAdapter = webView.createPrintDocumentAdapter("Threema_ID_" + identity);
		String jobName = getString(R.string.app_name) + " " + getString(R.string.backup_id_title);

		printManager.print(jobName, printAdapter,
				new PrintAttributes.Builder().build());
	}

	private void printBitmap(Bitmap bitmap) {
		String html="<html><body><center><h1>" + getString(R.string.backup_share_subject) +
				"</h1><h2>" + identity +
				"</h2><br><br><img src='{IMAGE_URL}' width='350px' height='350px'/>" +
				"<font face='monospace' size='8pt'><br><br>" +
				backupData +
				"</font></center></body></html>";

		WebView webView = new WebView(this);
		webView.setWebViewClient(new WebViewClient() {

			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				return false;
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				createWebPrintJob(view);
				printWebView = null;
			}
		});

		// Convert bitmap to Base64 encoded image
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
		byte[] byteArray = byteArrayOutputStream.toByteArray();
		String imgageBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT);
		String image = "data:image/png;base64," + imgageBase64;

		html = html.replace("{IMAGE_URL}", image);
		webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);

		// Keep a reference to WebView object until you pass the PrintDocumentAdapter
    	// to the PrintManager
		printWebView = webView;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				done();
				return true;
			case R.id.menu_print:
				printBitmap(qrcodeBitmap);
				break;
			case R.id.menu_backup_share:
				shareId();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void shareId() {
		String shareText = getString(R.string.backup_share_content) + "\n\n" + backupData;
		String shareSubject = getString(R.string.backup_share_subject) + " " + this.identity;

		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("text/plain");
		shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
		shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);

		startActivity(shareIntent);
	}


	@Override
	public void onBackPressed() {
		done();
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		ConfigUtils.adjustToolbar(this, getToolbar());
	}

	@Override
	public void onYes(String tag, Object data) {
		Intent upIntent = new Intent(ExportIDResultActivity.this, HomeActivity.class);
		NavUtils.navigateUpTo(ExportIDResultActivity.this, upIntent);

		finish();
	}

	@Override
	public void onNo(String tag, Object data) {

	}
}
