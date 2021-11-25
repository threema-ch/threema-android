/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021 Threema GmbH
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

package ch.threema.app.qrscanner.activity;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.AddContactActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.grouplinks.OutgoingGroupRequestActivity;
import ch.threema.app.qrscanner.dialog.GenericScanResultDialog;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.webclient.services.QRCodeParser;
import ch.threema.app.webclient.services.QRCodeParserImpl;
import ch.threema.base.utils.Base64;

public class BaseQrScannerActivity extends AppCompatActivity implements
	GenericScanResultDialog.ScanResultClickListener,
	GenericAlertDialog.DialogClickListener {

	private static final int PERMISSION_REQUEST_CAMERA = 1;
	private static final String DIALOG_TAG_SCAN_RESULT = "show_scan_result";
	private static final String DIALOG_TAG_SCAN_ERROR = "show_scan_result";

	ActivityResultLauncher<Intent> scanResultLauncher = registerForActivityResult(
		new ActivityResultContracts.StartActivityForResult(),
		result -> {
			if (result.getResultCode() == Activity.RESULT_OK) {
				handleActivityResult(result.getData());
			} else {
				finish();
			}
		});

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
			setTheme(R.style.Theme_Threema_Translucent_Dark);
		}

		if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
			launchScanner();
		}
	}

	private void launchScanner() {
		Intent intent = new Intent(this, CaptureActivity.class);
		if (!TestUtil.empty(getIntent().getStringExtra(CaptureActivity.KEY_NEED_SCAN_HINT_TEXT))) {
			intent.putExtra(CaptureActivity.KEY_NEED_SCAN_HINT_TEXT, getIntent().getStringExtra(CaptureActivity.KEY_NEED_SCAN_HINT_TEXT));
		}
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		// lock orientation before launching scanner
		scanResultLauncher.launch(intent);
	}

	public void handleActivityResult(Intent intent) {
		ConfigUtils.setRequestedOrientation(this, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

		if (intent.getBooleanExtra(ThreemaApplication.INTENT_DATA_QRCODE_TYPE_OK, false)) {
			String resultRaw = intent.getStringExtra(ThreemaApplication.INTENT_DATA_QRCODE);
			Uri uri = Uri.parse(resultRaw);
			String scheme = uri.getScheme();
			String authority = uri.getAuthority();

			// must be base64 web session or invalid
			if (scheme == null && authority == null) {
				handleWebSessionResult(resultRaw);
				return;
			}

			if (scheme != null && scheme.equals(QRCodeServiceImpl.ID_SCHEME) ||
				(scheme != null && (scheme.equals(BuildConfig.uriScheme) && "add".equals(uri.getAuthority())))) {
				handleContactResult(resultRaw);
				return;
			}
			else if (authority != null && authority.equals(BuildConfig.groupLinkActionUrl)) {
				handleGroupLinkResult(resultRaw);
				return;
			}
			noThreemaQrCodeDialog(resultRaw);
		}
		else {
			invalidCodeDialog();
		}
	}

	private void invalidCodeDialog() {
		GenericAlertDialog.newInstance(
			R.string.scan_failure_dialog_title,
			R.string.invalid_barcode,
			R.string.try_again,
			R.string.close
		).show(getSupportFragmentManager(), DIALOG_TAG_SCAN_ERROR);
	}

	private void noThreemaQrCodeDialog(String result) {
		GenericScanResultDialog.newInstance(result).show(getSupportFragmentManager(), DIALOG_TAG_SCAN_RESULT);
	}

	private void handleContactResult(String result) {
		Intent intent = new Intent(this, AddContactActivity.class);
		intent.putExtra(AddContactActivity.EXTRA_QR_RESULT, result);
		startActivity(intent);
		finish();
	}

	private void handleGroupLinkResult(String result) {
		Intent intent = new Intent(this, OutgoingGroupRequestActivity.class);
		intent.putExtra(OutgoingGroupRequestActivity.EXTRA_QR_RESULT, result);
		startActivity(intent);
		finish();
	}

	private void handleWebSessionResult(String result) {
		try {
			byte[] base64Payload = Base64.decode(result);
			final QRCodeParser webClientQRCodeParser = new QRCodeParserImpl();
			webClientQRCodeParser.parse(base64Payload); // throws if QR is not valid
			// it was a valid web client qr code, exit method
			startWebClientByQRResult(base64Payload);
			finish();
		} catch (IOException | QRCodeParser.InvalidQrCodeException | IllegalArgumentException e) {
			// not a valid base64 or web client payload
			invalidCodeDialog();
		}
	}

	/**
	 * start a web client session (payload must be validated before
	 * the method is called)
	 *
	 * fix #ANDR-570
	 * @param payload a valid payload
	 */
	private void startWebClientByQRResult(final byte[] payload) {
		if (payload != null) {
			// start web client session screen with payload data and finish my screen
			Intent webClientIntent = new Intent(this, ch.threema.app.webclient.activities.SessionsActivity.class);
			IntentDataUtil.append(payload, webClientIntent);
			startActivity(webClientIntent);
		}
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == PERMISSION_REQUEST_CAMERA) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				launchScanner();
			} else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
				ConfigUtils.showPermissionRationale(this, getWindow().getDecorView().findViewById(android.R.id.content), R.string.permission_camera_qr_required, new BaseTransientBottomBar.BaseCallback<Snackbar>() {
					@Override
					public void onDismissed(Snackbar transientBottomBar, int event) {
						super.onDismissed(transientBottomBar, event);
						finish();
					}
				});
			} else {
				finish();
			}
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	public void onCopy(String result) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = ClipData.newPlainText(null, result);
		clipboard.setPrimaryClip(clip);
		Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onShare(String result) {
		ShareUtil.shareTextString(this, result);
	}

	@Override
	public void onClose() {
		finish();
	}

	@Override
	public void onYes(String tag, Object data) {
		launchScanner();
	}

	@Override
	public void onNo(String tag, Object data) {
		finish();
	}
}
