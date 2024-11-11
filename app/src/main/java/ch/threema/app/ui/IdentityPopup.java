/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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

package ch.threema.app.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.Group;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.lang.ref.WeakReference;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.AddContactActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.webclient.activities.SessionsActivity;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.SessionService;
import ch.threema.base.ThreemaException;

public class IdentityPopup extends DimmingPopupWindow {

    private WeakReference<Activity> activityRef = new WeakReference<>(null);
    private ImageView qrCodeView;
    private QRCodeService qrCodeService;
    private MaterialSwitch webEnableView;
    private SessionService sessionService;
    private int animationCenterX, animationCenterY;
    private ProfileButtonListener profileButtonListener;

    public IdentityPopup(Context context) {
        super(context);

        final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager == null) {
            dismiss();
            return;
        }

        final UserService userService = serviceManager.getUserService();

        qrCodeService = serviceManager.getQRCodeService();

        try {
            sessionService = serviceManager.getWebClientServiceManager().getSessionService();
        } catch (ThreemaException e) {
            dismiss();
            return;
        }

        LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout popupLayout = (FrameLayout) layoutInflater.inflate(R.layout.popup_identity, null, false);

        TextView identityLabelTextView = popupLayout.findViewById(R.id.identity_label);
        this.qrCodeView = popupLayout.findViewById(R.id.qr_image);
        Group webControls = popupLayout.findViewById(R.id.web_controls);
        this.webEnableView = popupLayout.findViewById(R.id.web_enable);
        popupLayout.findViewById(R.id.web_label).setOnClickListener(v -> {
            Intent intent = new Intent(context, SessionsActivity.class);
            activityRef.get().startActivity(intent);
            dismiss();
        });

        if (ConfigUtils.isOnPremBuild()) {
            popupLayout.findViewById(R.id.share_button).setVisibility(View.GONE);
        } else {
            popupLayout.findViewById(R.id.share_button).setOnClickListener(v -> ShareUtil.shareContact(activityRef.get(), null));
        }

        identityLabelTextView.setText(userService.getIdentity());
        identityLabelTextView.setContentDescription(context.getString(R.string.my_id) + " " + userService.getIdentity());
        identityLabelTextView.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(null, userService.getIdentity());
            clipboard.setPrimaryClip(clip);

            Toast.makeText(context, R.string.contact_details_id_copied, Toast.LENGTH_SHORT).show();

            return true;
        });

        setContentView(popupLayout);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        setWidth(FrameLayout.LayoutParams.WRAP_CONTENT);
        setHeight(FrameLayout.LayoutParams.WRAP_CONTENT);
        setAnimationStyle(0);
        setFocusable(false);
        setTouchable(true);
        setOutsideTouchable(true);
        setBackgroundDrawable(new BitmapDrawable());

        popupLayout.setOnClickListener(v -> dismiss());

        MaterialButton scanButton = popupLayout.findViewById(R.id.scan_button);
        scanButton.setOnClickListener(v -> scanQR());

        MaterialButton profileButton = popupLayout.findViewById(R.id.profile_button);
        profileButton.setOnClickListener(v -> {
            dismiss();
            this.profileButtonListener.onClicked();
        });

        if (qrCodeView != null) {
            qrCodeView.setOnClickListener(this::zoomQR);
        }

        if (webControls != null && webEnableView != null) {
            if (AppRestrictionUtil.isWebDisabled(context)) {
                // Webclient is disabled, hide UI elements
                webEnableView.setEnabled(false);
                webControls.setVisibility(View.GONE);
            } else {
                webEnableView.setChecked(this.isWebClientEnabled());
                webEnableView.setOnCheckedChangeListener((buttonView, isChecked) -> startWebClient(isChecked));
            }
        }
    }

    private void scanQR() {
        if (getContext() == null) {
            return;
        }
        if (ConfigUtils.supportsGroupLinks()) {
            QRScannerUtil.getInstance().initiateGeneralThreemaQrScanner(activityRef.get(), getContext().getString(R.string.qr_scanner_id_hint));
        } else {
            Intent intent = new Intent(getContext(), AddContactActivity.class);
            intent.putExtra(AddContactActivity.EXTRA_ADD_BY_QR, true);
            if (activityRef.get() != null) {
                activityRef.get().startActivity(intent);
            }
        }
    }

    private void zoomQR(View view) {
        if (getContext() == null) {
            return;
        }
        new QRCodePopup(
            getContext(),
            activityRef.get().getWindow().getDecorView(),
            null
        ).show(
            view,
            null,
            QRCodeServiceImpl.QR_TYPE_ID
        );
    }

    /**
     * @param activity              The calling activity
     * @param toolbarView           Toolbar this popup will be aligned to
     * @param location              center location of navigation icon in toolbar
     * @param profileButtonListener Get Callback when profile button was clicked
     * @param onDismissListener     Called when the popup was dismissed
     */
    public void show(
        Activity activity,
        final View toolbarView,
        int[] location,
        ProfileButtonListener profileButtonListener,
        OnDismissListener onDismissListener
    ) {

        if (getContext() == null) {
            return;
        }

        this.activityRef = new WeakReference<>(activity);
        this.profileButtonListener = profileButtonListener;

        int offsetY = activity.getResources().getDimensionPixelSize(R.dimen.navigation_icon_size) / 2;
        int offsetX = activity.getResources().getDimensionPixelSize(R.dimen.identity_popup_arrow_inset_left) + (activity.getResources().getDimensionPixelSize(R.dimen.identity_popup_arrow_width) / 2);

        animationCenterX = offsetX;
        animationCenterY = 0;

        Bitmap bitmap = qrCodeService.getUserQRCode();
        if (bitmap == null) {
            dismiss();
            return;
        }

        final BitmapDrawable bitmapDrawable = new BitmapDrawable(getContext().getResources(), bitmap);
        bitmapDrawable.setFilterBitmap(false);

        this.qrCodeView.setImageDrawable(bitmapDrawable);
        if (ConfigUtils.isTheDarkSide(getContext())) {
            ConfigUtils.invertColors(this.qrCodeView);
        }

        if (toolbarView != null) {
            int[] toolbarLocation = {0, 0};
            toolbarView.getLocationInWindow(toolbarLocation);

            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            try {
                showAtLocation(toolbarView, Gravity.LEFT | Gravity.TOP, location[0] - offsetX, location[1] + offsetY);
            } catch (WindowManager.BadTokenException e) {
                //
            }

            dimBackground();

            getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getContentView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    AnimationUtil.circularReveal(getContentView(), animationCenterX, animationCenterY, false);
                }
            });
        }
        WebClientListenerManager.serviceListener.add(this.webClientServiceListener);
        setOnDismissListener(onDismissListener);
    }

    @Override
    public void dismiss() {
        if (isShowing()) {
            AnimationUtil.circularObscure(
                getContentView(),
                animationCenterX,
                animationCenterY,
                false,
                IdentityPopup.super::dismiss
            );
        }
        WebClientListenerManager.serviceListener.remove(this.webClientServiceListener);
    }

    private final WebClientServiceListener webClientServiceListener = new WebClientServiceListener() {
        @Override
        public void onEnabled() {
            this.setEnabled(true);
        }

        @Override
        public void onDisabled() {
            this.setEnabled(false);
        }

        private void setEnabled(final boolean enabled) {
            RuntimeUtil.runOnUiThread(() -> {
                if (webEnableView != null) {
                    webEnableView.setChecked(enabled);
                }
            });
        }
    };

    private boolean isWebClientEnabled() {
        return sessionService.isEnabled();
    }

    private void startWebClient(boolean shouldStart) {
        if (getContext() == null) {
            return;
        }
        if (shouldStart && sessionService.getAllSessionModels().isEmpty()) {
            getContext().startActivity(new Intent(getContext(), SessionsActivity.class));
            dismiss();
        } else {
            sessionService.setEnabled(shouldStart);
        }
    }

    public interface ProfileButtonListener {
        void onClicked();
    }
}
