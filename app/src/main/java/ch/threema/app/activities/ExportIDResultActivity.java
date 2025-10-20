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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.NavUtils;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.appbar.MaterialToolbar;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.home.HomeActivity;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.QRCodePopup;
import ch.threema.app.ui.TooltipPopup;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class ExportIDResultActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener, LifecycleOwner {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ExportIDResultActivity");

    private static final String DIALOG_TAG_QUIT_CONFIRM = "qconf";
    private static final int QRCODE_SMALL_DIMENSION_PIXEL = 200;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private Bitmap qrcodeBitmap;
    private TooltipPopup tooltipPopup;

    // Keeping this reference on purpose
    @SuppressWarnings("unused")
    private WebView printWebView;

    private String identity, backupData;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!dependencies.isAvailable()) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();

        if (actionBar == null) {
            finish();
            return;
        }

        Drawable checkDrawable = AppCompatResources.getDrawable(this, R.drawable.ic_check);
        Objects.requireNonNull(checkDrawable).setColorFilter(
            ConfigUtils.getColorFromAttribute(this, R.attr.colorOnSurface),
            PorterDuff.Mode.SRC_IN
        );
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(checkDrawable);
        actionBar.setTitle("");

        this.backupData = this.getIntent().getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP);
        this.identity = this.getIntent().getStringExtra(AppConstants.INTENT_DATA_CONTACT);

        if (TestUtil.isEmptyOrNull(this.backupData)) {
            finish();
            return;
        }

        displayIDBackup();

        if (savedInstanceState == null) {
            showTooltip();
        }
    }

    @Override
    protected void handleDeviceInsets() {
        super.handleDeviceInsets();
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.qr_container_backup),
            InsetSides.lbr()
        );
    }

    private void displayIDBackup() {
        ScrollView layoutContainer = findViewById(R.id.qr_container_backup);
        layoutContainer.setVisibility(View.VISIBLE);

        TextView textView = findViewById(R.id.threemaid);
        textView.setText(backupData);

        final ImageView imageView = findViewById(R.id.qrcode_backup);
        this.qrcodeBitmap = dependencies.getQrCodeService().getRawQR(backupData, false);

        final int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, QRCODE_SMALL_DIMENSION_PIXEL, getResources().getDisplayMetrics());
        Bitmap bmpScaled = Bitmap.createScaledBitmap(qrcodeBitmap, px, px, false);
        bmpScaled.setDensity(Bitmap.DENSITY_NONE);
        imageView.setImageBitmap(bmpScaled);
        if (ConfigUtils.isTheDarkSide(this)) {
            ConfigUtils.invertColors(imageView);
        }

        imageView.setOnClickListener(v -> new QRCodePopup(ExportIDResultActivity.this, getWindow().getDecorView(), ExportIDResultActivity.this).show(v, backupData));
    }

    private void showTooltip() {
        if (!dependencies.getPreferenceService().getIsExportIdTooltipShown()) {
            getToolbar().postDelayed(() -> {

                View menuItemView = findViewById(R.id.menu_backup_share);
                int[] location = new int[2];
                menuItemView.getLocationOnScreen(location);
                location[0] += menuItemView.getWidth() / 2;
                location[1] += menuItemView.getHeight();

                tooltipPopup = new TooltipPopup(this, R.string.preferences__tooltip_export_id_shown, this);
                tooltipPopup.show(this, menuItemView, null, getString(R.string.tooltip_export_id), TooltipPopup.Alignment.BELOW_ANCHOR_ARROW_RIGHT, location, 5000);
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
        String html = "<html><body><center><h1>" + getString(R.string.backup_share_subject) +
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

        // Keep a reference to WebView object until you pass the PrintDocumentAdapter to the PrintManager
        printWebView = webView;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            done();
        } else if (item.getItemId() == R.id.menu_print) {
            printBitmap(qrcodeBitmap);
        } else if (item.getItemId() == R.id.menu_backup_share) {
            shareId();
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
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
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
}
