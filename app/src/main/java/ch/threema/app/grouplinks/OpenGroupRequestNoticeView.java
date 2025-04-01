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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.transition.Fade;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;

import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.GroupId;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;

public class OpenGroupRequestNoticeView extends ConstraintLayout implements DefaultLifecycleObserver,
    OnClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("OpenGroupRequestNoticeView");

    private static final int MAX_REQUESTS_SHOWN = 20;
    private static final String DIALOG_HANDLE_REQUEST = "handle_request";

    ExecutorService executor = Executors.newFixedThreadPool(2);
    Handler handler = new Handler(Looper.getMainLooper());

    private ChipGroup chipGroup;
    private ContactService contactService;
    private DatabaseServiceNew databaseService;

    GroupId groupId;

    private final IncomingGroupJoinRequestListener groupJoinRequestListener = new IncomingGroupJoinRequestListener() {
        @Override
        public void onReceived(IncomingGroupJoinRequestModel incomingGroupJoinRequestModel, GroupModel groupModel) {
            RuntimeUtil.runOnUiThread(OpenGroupRequestNoticeView.this::updateGroupRequests);
        }

        @Override
        public void onRespond() {
            RuntimeUtil.runOnUiThread(OpenGroupRequestNoticeView.this::updateGroupRequests);
        }

    };

    public OpenGroupRequestNoticeView(@NonNull Context context) {
        super(context);
    }

    public OpenGroupRequestNoticeView(Context context, AttributeSet attrs) throws ThreemaException {
        super(context, attrs);
        init(context);
    }

    public OpenGroupRequestNoticeView(Context context, AttributeSet attrs, int defStyleAttr) throws ThreemaException {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        ListenerManager.incomingGroupJoinRequestListener.add(this.groupJoinRequestListener);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        ListenerManager.incomingGroupJoinRequestListener.remove(this.groupJoinRequestListener);
    }

    private void init(Context context) throws ThreemaException {
        if (!(getContext() instanceof AppCompatActivity)) {
            return;
        }

        getActivity().getLifecycle().addObserver(this);

        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager == null) {
            throw new ThreemaException("Missing serviceManager");
        }
        this.databaseService = serviceManager.getDatabaseServiceNew();
        this.contactService = serviceManager.getContactService();

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.notice_open_ballots, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.chipGroup = findViewById(R.id.chip_group);
    }

    public void setGroupIdReference(GroupId groupId) {
        this.groupId = groupId;
    }

    public void updateGroupRequests() {
        // return if we receive a new request and the listener triggers while we have a non group chat open
        if (groupId == null) {
            return;
        }
        executor.execute(() -> {
            List<IncomingGroupJoinRequestModel> requests = databaseService.getIncomingGroupJoinRequestModelFactory()
                .getAllOpenRequestsForGroup(groupId);
            handler.post(() -> {
                if (requests.isEmpty()) {
                    hide(false);
                    return;
                }
                chipGroup.removeAllViews();
                addFirstChip();

                int i = 0;
                for (IncomingGroupJoinRequestModel request : requests) {
                    if (i++ >= MAX_REQUESTS_SHOWN) {
                        break;
                    }
                    addChip(request);
                    i++;
                }
                show(false);
            });
        });
    }

    private void addFirstChip() {
        Chip firstChip = new Chip(getContext());
        ChipDrawable firstChipDrawable = ChipDrawable.createFromAttributes(getContext(),
            null,
            0,
            R.style.Threema_Chip_ChatNotice_Overview_Intro);
        firstChip.setChipDrawable(firstChipDrawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            firstChip.setTextAppearance(R.style.Threema_TextAppearance_Chip_ChatNotice);
        } else {
            firstChip.setTextSize(14);
        }
        firstChip.setTextColor(getResources().getColor(R.color.text_color_openNotice));
        firstChip.setText(ThreemaApplication.getAppContext().getString(R.string.open_group_requests_chips_title));
        firstChip.setClickable(false);
        chipGroup.addView(firstChip);
    }

    private void addChip(@NonNull IncomingGroupJoinRequestModel request) {
        Chip chip = new Chip(getContext());
        ChipDrawable chipDrawable = ChipDrawable.createFromAttributes(getContext(),
            null,
            0,
            R.style.Threema_Chip_ChatNotice_Overview);
        chip.setChipDrawable(chipDrawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            chip.setTextAppearance(R.style.Threema_TextAppearance_Chip_ChatNotice);
        } else {
            chip.setTextSize(14);
        }

        RuntimeUtil.runOnUiThread(() -> {
            Bitmap bitmap = contactService.getAvatar(contactService.getByIdentity(request.getRequestingIdentity()), false);
            if (bitmap != null) {
                bitmap = BitmapUtil.replaceTransparency(bitmap, Color.WHITE);
                chip.setChipIcon(AvatarConverterUtil.convertToRound(OpenGroupRequestNoticeView.this.getResources(), bitmap));
            } else {
                chip.setChipIconResource(R.drawable.ic_hand_up_stop_outline);
            }
        });

        chip.setTag(request);
        chip.setId(request.getId());
        chip.setTextEndPadding(getResources().getDimensionPixelSize(R.dimen.chip_end_padding_text_only));
        chip.setText(NameUtil.getDisplayName(contactService.getByIdentity(request.getRequestingIdentity())));
        ColorStateList foregroundColor;
        ColorStateList backgroundColor;
        if (ConfigUtils.isTheDarkSide(getContext())) {
            foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnBackground));
            backgroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary));
        } else {
            foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary));
            backgroundColor = foregroundColor.withAlpha(getResources().getInteger(R.integer.chip_alpha));
        }
        chip.setTextColor(foregroundColor);
        chip.setChipBackgroundColor(backgroundColor);
        chip.setOnClickListener(OpenGroupRequestNoticeView.this);

        chipGroup.addView(chip);
    }

    @UiThread
    public void show(boolean animated) {
        if (getVisibility() != VISIBLE) {
            if (animated) {
                Transition transition = new Fade();
                transition.setDuration(250);
                transition.addTarget(this);

                TransitionManager.endTransitions((ViewGroup) getParent());
                TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
            }
            setVisibility(VISIBLE);
        }
    }

    @UiThread
    public void hide(boolean animated) {
        if (getVisibility() != GONE) {
            if (animated) {
                Transition transition = new Fade();
                transition.setDuration(250);
                transition.addTarget(this);
                TransitionManager.endTransitions((ViewGroup) getParent());
                TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
            }
            setVisibility(GONE);
        }
    }

    @Override
    public void onClick(View view) {
        IncomingGroupJoinRequestModel request = (IncomingGroupJoinRequestModel) view.getTag();
        logger.debug("requests id to handle {} for group invite {}", request.getId(), request.getGroupInviteId());
        IncomingGroupJoinRequestDialog.newInstance(request.getId()).show(getActivity().getSupportFragmentManager(), DIALOG_HANDLE_REQUEST);
    }

    private AppCompatActivity getActivity() {
        return (AppCompatActivity) getContext();
    }
}
