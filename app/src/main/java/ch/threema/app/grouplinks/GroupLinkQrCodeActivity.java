/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

package ch.threema.app.grouplinks;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.text.HtmlCompat;
import ch.threema.app.R;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.services.FileService;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class GroupLinkQrCodeActivity extends ThreemaToolbarActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("GroupLinkQrCodeActivity");

    private FileService fileService;
    private QRCodeService qrCodeService;
    private String groupLink;
    private String groupName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
    }

    @Override
    protected void initServices() {
        super.initServices();
        this.fileService = serviceManager.getFileService();
        this.qrCodeService = serviceManager.getQRCodeService();
    }

    @Override
    protected void handleDeviceInsets() {
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.main_content),
            InsetSides.ltr()
        );
        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.qr_container_backup),
            InsetSides.bottom(),
            SpacingValues.bottom(R.dimen.grid_unit_x2)
        );
    }

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        this.groupLink = getIntent().getStringExtra(IntentDataUtil.INTENT_DATA_GROUP_LINK);
        this.groupName = getIntent().getStringExtra(IntentDataUtil.INTENT_DATA_GROUP_NAME);

        if (groupLink == null) {
            logger.error("No group link received... finishing");
            finish();
            return false;
        }

        initLayout();
        return true;
    }

    private void initLayout() {
        setSupportActionBar(findViewById(R.id.toolbar));
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            finish();
            return;
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        actionBar.setTitle(getString(R.string.group_qr_code_title));

        Bitmap qrBitmap = qrCodeService.getRawQR(groupLink, true, QRCodeServiceImpl.QR_TYPE_GROUP_LINK);
        final BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), qrBitmap);
        bitmapDrawable.setFilterBitmap(false);

        ((ImageView) findViewById(R.id.thumbnail_view)).setImageDrawable(bitmapDrawable);
        ((TextView) findViewById(R.id.qr_code_description))
            .setText(
                HtmlCompat.fromHtml(String.format(
                    getString(R.string.group_link_qr_desc),
                    groupName
                ), HtmlCompat.FROM_HTML_MODE_COMPACT)
            );
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_group_link_qrcode;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_group_link_qrcode, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.menu_share) {
            shareQrBitmap();
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareQrBitmap() {
        try {
            Uri qrCodeShareFileUri = this.fileService.getTempShareFileUri(
                BitmapUtil.getBitmapFromView(findViewById(R.id.qr_code_container)));
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, qrCodeShareFileUri);
            intent.setType(MimeUtil.MIME_TYPE_IMAGE_PNG);
            if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(qrCodeShareFileUri.getScheme())) {
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            startActivity(Intent.createChooser(intent, getResources().getText(R.string.share_via)));
        } catch (IOException e) {
            logger.error("Exception sharing group QR-code", e);
            Toast.makeText(this,
                String.format(getString(R.string.an_error_occurred_more), e),
                Toast.LENGTH_LONG
            ).show();
        }
    }
}
