/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.SQLException;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.FitWindowsFrameLayout;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;

import java.util.Date;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.emojis.EmojiEditText;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.mediaattacher.ControlPanelButton;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.group.GroupInviteService;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.factories.GroupInviteModelFactory;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.GroupInviteModel;
import java8.util.Optional;

public class AddGroupLinkBottomSheet extends ThreemaToolbarActivity implements View.OnClickListener {

    private static final Logger logger = LoggingUtil.getThreemaLogger("AddGroupLinkBottomSheet");

    private static final String DIALOG_TAG_EDIT_EXPIRATION_DATE = "editDate";
    private static final String BUNDLE_NEW_INVITE_ID_EXTRA = "newInvite";

    private GroupService groupService;
    private GroupInviteService groupInviteService;
    private GroupInviteModelFactory groupInviteRepository;

    private GroupInviteModel groupLinkToAdd;

    private TextInputLayout textInputLayout;
    private EmojiEditText newGroupLinkName;
    private MaterialCheckBox administrationCheckbox;
    private AppCompatImageButton expirationDateButton;
    private TextView linkExpirationDate;
    private ControlPanelButton qrButton;
    private ControlPanelButton shareButton;

    @Override
    protected boolean initActivity(Bundle savedInstanceState) {
        if (!super.initActivity(savedInstanceState)) {
            return false;
        }

        int groupId = getIntent().getIntExtra(ThreemaApplication.INTENT_DATA_GROUP, 0);
        GroupModel groupModel = this.groupService.getById(groupId);

        if (groupModel == null) {
            logger.error("Exception, no group model received.. finishing");
            finish();
            return false;
        }

        // try to reset the previously created invite on configuration change
        if (savedInstanceState != null) {
            Optional<GroupInviteModel> optionalGroupInvite = groupInviteRepository.getById(savedInstanceState.getInt(BUNDLE_NEW_INVITE_ID_EXTRA));

            if (optionalGroupInvite.isPresent()) {
                this.groupLinkToAdd = optionalGroupInvite.get();
            }
        }
        // create a new link otherwise
        if (this.groupLinkToAdd == null) {
            try {
                this.groupLinkToAdd = groupInviteService.createGroupInvite(groupModel, false);
            } catch (Exception e) {
                LogUtil.error(String.format(getString(R.string.an_error_occurred_more), e.getMessage()), this);
            }
        }

        initLayout();
        initListeners();
        return true;
    }

    @Override
    protected void initServices() {
        super.initServices();
        try {
            this.groupInviteService = serviceManager.getGroupInviteService();
            this.groupService = serviceManager.getGroupService();
            this.groupInviteRepository = serviceManager.getDatabaseServiceNew().getGroupInviteModelFactory();
        } catch (FileSystemNotPresentException | MasterKeyLockedException e) {
            logger.error("Exception, services not available... finishing", e);
            finish();
        }
    }

    private void initLayout() {
        getWindow().setStatusBarColor(this.getResources().getColor(R.color.attach_status_bar_color_collapsed));

        this.textInputLayout = findViewById(R.id.text_input_layout);
        this.newGroupLinkName = findViewById(R.id.link_name);
        this.administrationCheckbox = findViewById(R.id.administration_checkbox);
        this.expirationDateButton = findViewById(R.id.expiration_date_button);
        this.linkExpirationDate = findViewById(R.id.item_property2);
        this.qrButton = findViewById(R.id.qr_code_button);
        this.shareButton = findViewById(R.id.share_button);

        // horizontal layout -> fill screen 2/3 with bottom sheet
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            CoordinatorLayout rootView = findViewById(R.id.coordinator);
            ConstraintLayout bottomSheetContainer = findViewById(R.id.bottom_sheet);
            CoordinatorLayout.LayoutParams bottomSheetContainerParams = (CoordinatorLayout.LayoutParams) bottomSheetContainer.getLayoutParams();
            FrameLayout.LayoutParams attacherLayoutParams = (FrameLayout.LayoutParams) rootView.getLayoutParams();

            FitWindowsFrameLayout contentFrameLayout = (FitWindowsFrameLayout) ((ViewGroup) rootView.getParent()).getParent();
            contentFrameLayout.setOnClickListener(v -> finish());

            attacherLayoutParams.width = ThreemaApplication.getAppContext().getResources().getDisplayMetrics().widthPixels * 2 / 3;
            attacherLayoutParams.gravity = Gravity.CENTER;
            bottomSheetContainerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bottomSheetContainerParams.gravity = Gravity.CENTER;
            bottomSheetContainerParams.insetEdge = Gravity.CENTER;

            bottomSheetContainer.setLayoutParams(bottomSheetContainerParams);
            rootView.setLayoutParams(attacherLayoutParams);

            contentFrameLayout.setOnClickListener(v -> finish());
        }

        this.linkExpirationDate.setText(this.groupLinkToAdd.getExpirationDate() != null ?
            DateUtils.formatDateTime(this, this.groupLinkToAdd.getExpirationDate().getTime(), DateUtils.FORMAT_SHOW_DATE)
            : getString(R.string.group_link_expiration_none));
        this.newGroupLinkName.setText(groupLinkToAdd.getInviteName());
        textInputLayout.setEndIconVisible(false);
        textInputLayout.setEndIconActivated(false);
    }

    private void initListeners() {
        findViewById(R.id.coordinator).setOnClickListener(this);
        findViewById(R.id.expiration_date_button).setOnClickListener(this);
        this.qrButton.setOnClickListener(this);
        this.shareButton.setOnClickListener(this);
        this.textInputLayout.setEndIconOnClickListener(this);
        this.administrationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                GroupInviteModel updateModel = new GroupInviteModel.Builder(groupLinkToAdd)
                    .withManualConfirmation(isChecked)
                    .build();
                groupInviteRepository.update(updateModel);
                this.groupLinkToAdd = updateModel;
            } catch (SQLException | GroupInviteModel.MissingRequiredArgumentsException e) {
                LogUtil.error(String.format(getString(R.string.an_error_occurred_more), e.getMessage()), this);
            }
        });

        this.newGroupLinkName.setOnFocusChangeListener((v, hasFocus) -> {
            textInputLayout.setEndIconVisible(hasFocus);
            textInputLayout.setEndIconActivated(hasFocus);
        });

        this.newGroupLinkName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // don't bother
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // don't bother
            }

            @Override
            public void afterTextChanged(Editable s) {
                // don't allow empty names
                boolean hasMessageText = s.toString().trim().length() > 0;
                textInputLayout.setEndIconVisible(hasMessageText);
                textInputLayout.setEndIconActivated(hasMessageText);
            }
        });

        ConstraintLayout bottomSheetLayout = findViewById(R.id.bottom_sheet);
        BottomSheetBehavior<ConstraintLayout> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == STATE_HIDDEN) {
                    finish();
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // don't bother about sliding
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUNDLE_NEW_INVITE_ID_EXTRA, this.groupLinkToAdd.getId());
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_group_link;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.coordinator) {
            finish();
        } else if (id == R.id.expiration_date_button) {
            final MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.group_link_edit_expiration_date)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();
            datePicker.addOnPositiveButtonClickListener(selection -> {
                Long date = datePicker.getSelection();
                if (this.linkExpirationDate != null && date != null) {
                    linkExpirationDate.setText(DateUtils.formatDateTime(this, date, DateUtils.FORMAT_SHOW_DATE));
                    try {
                        GroupInviteModel updateModel = new GroupInviteModel.Builder(groupLinkToAdd)
                            .withExpirationDate(new Date(date)).build();
                        groupInviteRepository.update(
                            updateModel
                        );
                        this.groupLinkToAdd = updateModel;

                    } catch (SQLException | GroupInviteModel.MissingRequiredArgumentsException e) {
                        LogUtil.error(String.format(getString(R.string.an_error_occurred_more), e.getMessage()), this);
                    }
                }
            });
            datePicker.show(getSupportFragmentManager(), DIALOG_TAG_EDIT_EXPIRATION_DATE);
        } else if (id == R.id.share_button) {
            this.groupInviteService.shareGroupLink(
                this,
                groupLinkToAdd);
        } else if (id == R.id.qr_code_button) {
            Intent qrIntent = new Intent(AddGroupLinkBottomSheet.this, GroupLinkQrCodeActivity.class);
            IntentDataUtil.append(groupInviteService.encodeGroupInviteLink(groupLinkToAdd), groupLinkToAdd.getOriginalGroupName(), qrIntent);
            startActivity(qrIntent);
        } else if (id == R.id.text_input_end_icon) {
            updateLinkName();
        }
    }

    private void updateLinkName() {
        Editable editedLinkName = newGroupLinkName.getText();
        try {
            if (editedLinkName != null) {
                GroupInviteModel updateModel = new GroupInviteModel.Builder(groupLinkToAdd).withInviteName(editedLinkName.toString()).build();
                groupInviteRepository.update(updateModel);
                this.groupLinkToAdd = updateModel;
                Toast.makeText(getApplicationContext(),
                    getString(R.string.group_link_update_success),
                    Toast.LENGTH_LONG
                ).show();
            }
        } catch (SQLException | GroupInviteModel.MissingRequiredArgumentsException e) {
            LogUtil.error(String.format(getString(R.string.an_error_occurred_more), e.getMessage()), this);
        }
        View focusedView = getCurrentFocus();
        if (focusedView != null) {
            focusedView.clearFocus();
            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View focusedView = getCurrentFocus();
            if (focusedView instanceof EmojiEditText) {
                Rect outRect = new Rect();
                focusedView.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    focusedView.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}
