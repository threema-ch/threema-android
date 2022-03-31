/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
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
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;

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
import ch.threema.app.camera.QRScannerActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.grouplinks.OutgoingGroupRequestActivity;
import ch.threema.app.qrscanner.dialog.GenericScanResultDialog;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.ShareUtil;
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
		Intent intent = new Intent(this, QRScannerActivity.class);
		intent.putExtras(getIntent());
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		scanResultLauncher.launch(intent);
	}

	public void handleActivityResult(Intent intent) {

		if (intent.getBooleanExtra(ThreemaApplication.INTENT_DATA_QRCODE_TYPE_OK, false)) {
			final String resultRaw = intent.getStringExtra(ThreemaApplication.INTENT_DATA_QRCODE);
			final Uri uri = Uri.parse(resultRaw);
			final String scheme = uri.getScheme();
			final String authority = uri.getAuthority();
			final String path = uri.getPath();

			// If not an URI, contents must be base64 web session
			if (scheme == null && authority == null) {
				handleWebSessionResult(resultRaw);
				return;
			}

			// 3mid:<IDENTITY>,<publicKeyHex>
			if (QRCodeServiceImpl.ID_SCHEME.equals(scheme)) {
				handleContactResult(resultRaw);
				return;
			}

			// threema://add?id=<IDENTITY>
			if (BuildConfig.uriScheme.equals(scheme) && "add".equals(authority)) {
				handleContactResult(resultRaw);
				return;
			}

			// https://threema.id/<IDENTITY>
			if ("https".equals(scheme) && BuildConfig.contactActionUrl.equals(authority) && path != null && path.length() == 9) {
				// For now, convert a threema.id URL to a threema://add URL.
				// TODO(ANDR-1599): Handle validation and processing of all "contact add" URLs in one place.
				final String identity = path.substring(1);
				handleContactResult(BuildConfig.uriScheme + "://add?id=" + identity);
				return;
			}

			// https://threema.group/join#<token>
			if ("https".equals(scheme) && BuildConfig.groupLinkActionUrl.equals(authority)) {
				handleGroupLinkResult(resultRaw);
				return;
			}

			// Otherwise, show dialog indicating that this is not a threema-specific QR code.
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
