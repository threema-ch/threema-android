/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.transition.Fade;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.models.MessageId;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ballot.BallotModel;

/**
 * A view that shows all open ballots (polls) for a chat in a ChipGroup and allows users to vote or close the ballot
 */
public class OpenBallotNoticeView extends ConstraintLayout implements DefaultLifecycleObserver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("OpenBallotNoticeView");
    private static final int MAX_BALLOTS_SHOWN = 20;
    private static final int MAX_BALLOT_TITLE_LENGTH = 20;
    private ChipGroup chipGroup;
    private final List<BallotChipHolder> shownBallots = new LinkedList<>();
    private BallotService ballotService;
    private UserService userService;
    private PreferenceService preferenceService;
    private ContactService contactService;
    private String identity;
    private MessageReceiver<?> messageReceiver;
    private int numOpenBallots;
    private OnCloseClickedListener onCloseClickedListener;

    private final BallotVoteListener ballotVoteListener = new BallotVoteListener() {
        @Override
        public void onSelfVote(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
        }

        @Override
        public void onVoteChanged(BallotModel ballotModel, String votingIdentity, boolean isFirstVote) {
            // There is no need to update the chips if the vote has been changed. However, update
            // the view when a first vote has been received as this may change the vote counter.
            if (isFirstVote) {
                RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
            }
        }

        @Override
        public void onVoteRemoved(BallotModel ballotModel, String votingIdentity) {
            RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
        }

        @Override
        public boolean handle(BallotModel ballotModel) {
            if (ballotModel != null) {
                try {
                    return ballotService.belongsToMe(ballotModel.getId(), messageReceiver);
                } catch (NotAllowedException e) {
                    logger.error("Exception", e);
                }
            }
            return false;
        }
    };

    private final BallotListener ballotListener = new BallotListener() {
        @Override
        public void onClosed(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
        }

        @Override
        public void onModified(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
        }

        @Override
        public void onCreated(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
        }

        @Override
        public void onRemoved(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
        }

        @Override
        public boolean handle(BallotModel ballotModel) {
            if (ballotModel != null) {
                try {
                    return ballotService.belongsToMe(ballotModel.getId(), messageReceiver);
                } catch (NotAllowedException e) {
                    logger.error("Exception", e);
                }
            }
            return false;
        }
    };

    public OpenBallotNoticeView(Context context) {
        super(context);
        init(context);
    }

    public OpenBallotNoticeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OpenBallotNoticeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        if (!(getContext() instanceof AppCompatActivity)) {
            return;
        }

        getActivity().getLifecycle().addObserver(this);

        try {
            ballotService = ThreemaApplication.getServiceManager().getBallotService();
            userService = ThreemaApplication.getServiceManager().getUserService();
            preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
            contactService = ThreemaApplication.getServiceManager().getContactService();
        } catch (Exception e) {
            logger.error("Exception", e);
        }

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.notice_open_ballots, this);

        getLayoutTransition().disableTransitionType(LayoutTransition.CHANGING);
        getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        getLayoutTransition().enableTransitionType(LayoutTransition.DISAPPEARING);
        getLayoutTransition().enableTransitionType(LayoutTransition.APPEARING);

        findViewById(R.id.button_cancel).setOnClickListener(v -> {
            if (onCloseClickedListener != null) {
                onCloseClickedListener.onCloseClicked();
            }
        });

        identity = userService.getIdentity();
    }

    @UiThread
    public void show(boolean animated) {
        if (getVisibility() != VISIBLE && numOpenBallots > 0 && !preferenceService.getBallotOverviewHidden()) {
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

    @UiThread
    @SuppressLint("StaticFieldLeak")
    private void updateBallotDisplay() {
        if (messageReceiver == null) {
            return;
        }

        new AsyncTask<Void, Void, List<BallotModel>>() {
            @Override
            protected List<BallotModel> doInBackground(Void... voids) {
                try {
                    return ballotService.getBallots(new BallotService.BallotFilter() {
                        @Override
                        public MessageReceiver<?> getReceiver() {
                            return messageReceiver;
                        }

                        @Override
                        public BallotModel.State[] getStates() {
                            return new BallotModel.State[]{BallotModel.State.OPEN};
                        }

                        @Override
                        public String createdOrNotVotedByIdentity() {
                            return identity;
                        }

                        @Override
                        public boolean filter(BallotModel ballotModel) {
                            return true;
                        }
                    });
                } catch (NotAllowedException | IllegalStateException e) {
                    logger.error("Exception", e);
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<BallotModel> ballotModels) {
                // Hide this view if there are no open ballots (anymore)
                if (ballotModels.isEmpty()) {
                    hide(false);
                    return;
                }

                int numBallotsShown = 0;
                for (int i = 0; i < ballotModels.size(); i++) {
                    if (shownBallots.size() > i) {
                        // Update the available chips if possible
                        shownBallots.get(i).updateBallotModel(ballotModels.get(i));
                    } else {
                        // Add new chips if there are not enough chips present
                        shownBallots.add(new BallotChipHolder(ballotModels.get(i)));
                    }
                    // Count the shown chips. Note that chips with invalid ballots are not shown,
                    // but remain in this list in case an update makes them valid.
                    if (shownBallots.get(i).isShown()) {
                        numBallotsShown++;
                    }
                    // Don't add more than limit
                    if (numBallotsShown >= MAX_BALLOTS_SHOWN) {
                        break;
                    }
                }

                // Remove the last ballot models
                for (int i = shownBallots.size() - 1; i >= ballotModels.size(); i--) {
                    BallotChipHolder removedHolder = shownBallots.remove(i);
                    removedHolder.remove();
                }

                OpenBallotNoticeView.this.numOpenBallots = numBallotsShown;

                if (numBallotsShown > 0) {
                    show(false);
                }
            }
        }.execute();
    }

    public void setMessageReceiver(@NonNull MessageReceiver<?> messageReceiver) {
        this.messageReceiver = messageReceiver;
        updateBallotDisplay();
    }

    public void update() {
        updateBallotDisplay();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        this.chipGroup = findViewById(R.id.chip_group);
        this.chipGroup.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGING);
        this.chipGroup.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        this.chipGroup.getLayoutTransition().disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        this.chipGroup.getLayoutTransition().enableTransitionType(LayoutTransition.DISAPPEARING);
        this.chipGroup.getLayoutTransition().enableTransitionType(LayoutTransition.APPEARING);
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        ListenerManager.ballotListeners.add(this.ballotListener);
        ListenerManager.ballotVoteListeners.add(this.ballotVoteListener);
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        ListenerManager.ballotVoteListeners.remove(this.ballotVoteListener);
        ListenerManager.ballotListeners.remove(this.ballotListener);
    }

    @SuppressLint("RestrictedApi")
    public void onChipClick(@NonNull View v, @Nullable BallotModel ballotModel, boolean isVoteComplete) {
        if (ballotModel != null) {
            MenuBuilder menuBuilder = new MenuBuilder(getContext());
            new MenuInflater(getContext()).inflate(R.menu.chip_open_ballots, menuBuilder);

            // Set all icon colors, as MenuInflater ignores specified iconTint xml attribute
            for (int i = 0; i < menuBuilder.size(); i++) {
                final @NonNull MenuItem menuItem = menuBuilder.getItem(i);
                ConfigUtils.tintMenuIcon(getContext(), menuItem, R.attr.colorOnSurface);
            }

            if (BallotUtil.canViewMatrix(ballotModel, identity)) {
                menuBuilder.findItem(R.id.menu_ballot_results).setTitle(ballotModel.getState() == BallotModel.State.CLOSED ? R.string.ballot_result_final : R.string.ballot_result_intermediate);
            }

            final @Nullable MenuItem highlightItem;
            if (isVoteComplete) {
                highlightItem = menuBuilder.findItem(R.id.menu_ballot_close);
            } else if (ballotService.hasVoted(ballotModel.getId(), userService.getIdentity())) {
                highlightItem = menuBuilder.findItem(R.id.menu_ballot_results);
            } else {
                highlightItem = menuBuilder.findItem(R.id.menu_ballot_vote);
            }
            if (highlightItem != null) {
                @ColorInt int highlightColor = ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary);
                SpannableString highlightItemSpannable = new SpannableString(highlightItem.getTitle());
                highlightItemSpannable.setSpan(new ForegroundColorSpan(highlightColor), 0, highlightItemSpannable.length(), 0);
                highlightItem.setTitle(highlightItemSpannable);
                ConfigUtils.tintMenuIcon(highlightItem, highlightColor);
            }

            menuBuilder.setCallback(new MenuBuilder.Callback() {
                @Override
                public boolean onMenuItemSelected(@NonNull MenuBuilder menu, @NonNull MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.menu_ballot_vote) {
                        vote(ballotModel);
                    } else if (id == R.id.menu_ballot_results) {
                        BallotUtil.openMatrixActivity(getContext(), ballotModel, identity);
                    } else if (id == R.id.menu_ballot_close) {
                        close(ballotModel);
                    } else if (id == R.id.menu_ballot_delete) {
                        delete(ballotModel);
                    }
                    return true;
                }

                @Override
                public void onMenuModeChange(@NonNull MenuBuilder menu) {
                    // nothing to do
                }
            });

            if (!BallotUtil.canViewMatrix(ballotModel, identity)) {
                menuBuilder.removeItem(R.id.menu_ballot_results);
            }

            if (!BallotUtil.canClose(ballotModel, identity)) {
                menuBuilder.removeItem(R.id.menu_ballot_close);
            }

            MenuPopupHelper optionsMenu = new MenuPopupHelper(getContext(), menuBuilder, v);
            optionsMenu.setForceShowIcon(true);
            optionsMenu.show();
        }
    }

    private void vote(BallotModel model) {
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();

        if (BallotUtil.canVote(model, identity)) {
            BallotUtil.openVoteDialog(fragmentManager, model, identity);
        }
    }

    private void close(BallotModel model) {
        if (BallotUtil.canClose(model, identity)) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(R.string.ballot_close)
                .setMessage(R.string.ballot_really_close)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BallotUtil.closeBallot(getActivity(), model, ballotService, MessageId.random(), TriggerSource.LOCAL);
                    }
                });
            builder.create().show();
        }
    }

    private void delete(BallotModel model) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
            .setTitle(R.string.single_ballot_really_delete)
            .setMessage(getContext().getString(R.string.single_ballot_really_delete_text))
            .setNegativeButton(R.string.no, null)
            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        ballotService.remove(model);
                    } catch (NotAllowedException e) {
                        logger.error("Exception", e);
                    }
                }
            });
        builder.create().show();
    }

    private AppCompatActivity getActivity() {
        return (AppCompatActivity) getContext();
    }

    public void setOnCloseClickedListener(OnCloseClickedListener listener) {
        onCloseClickedListener = listener;
    }

    private class BallotChipHolder {
        @NonNull
        private BallotModel ballot;
        private final Chip chip;
        private boolean isShown = true;
        private int displayedVotes = -1;
        private int displayedParticipants = -1;

        private final Animation animation = new RotateAnimation(
            -3f,
            3,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        );

        private BallotChipHolder(@NonNull BallotModel ballotModel) {
            this.ballot = ballotModel;
            this.chip = createChip();

            chipGroup.addView(this.chip);

            animation.setDuration(50);
            animation.setRepeatCount(4);
            animation.setRepeatMode(Animation.REVERSE);

            show();
        }

        private void updateBallotModel(@NonNull BallotModel ballotModel) {
            boolean isAnotherBallot = ballot.getId() != ballotModel.getId();
            this.ballot = ballotModel;
            if (isAnotherBallot) {
                show();
            } else {
                updateName();
                setColor(BallotUtil.isMine(ballot, userService), displayedVotes, displayedParticipants);
            }
        }

        @NonNull
        private Chip createChip() {
            Chip ballotChip = new Chip(getContext());

            ChipDrawable chipDrawable = ChipDrawable.createFromAttributes(getContext(),
                null,
                0,
                R.style.Threema_Chip_ChatNotice_Overview);
            ballotChip.setChipDrawable(chipDrawable);
            ballotChip.setTextAppearance(R.style.Threema_TextAppearance_Chip_ChatNotice);
            ballotChip.setTextEndPadding(getResources().getDimensionPixelSize(R.dimen.chip_end_padding_text_only));

            return ballotChip;
        }

        private void show() {
            chip.setVisibility(View.VISIBLE);

            int votes = ballotService.getVotedParticipants(ballot.getId()).size();
            int participants = ballotService.getParticipants(ballot.getId()).length;
            if (participants == 0) {
                displayedVotes = -1;
                displayedParticipants = -1;
                chip.setVisibility(View.GONE);
                isShown = false;
                return;
            }

            displayedVotes = votes;
            displayedParticipants = participants;

            chip.setOnClickListener((View v) -> OpenBallotNoticeView.this.onChipClick(v, ballot, votes == participants));

            boolean isMine = BallotUtil.isMine(ballot, userService);

            chip.setText(getText(isMine, votes, participants));

            setAvatar();

            setColor(isMine, votes, participants);
        }

        private void updateName() {
            int votes = ballotService.getVotedParticipants(ballot.getId()).size();
            int participants = ballotService.getParticipants(ballot.getId()).length;
            chip.setText(getText(BallotUtil.isMine(ballot, userService), votes, participants));
            if (votes > displayedVotes && participants == displayedParticipants) {
                // Animate view when the number of votes increased
                chip.setAnimation(animation);
            }
            displayedVotes = votes;
            displayedParticipants = participants;
        }

        private void remove() {
            chipGroup.removeView(chip);
        }

        private boolean isShown() {
            return isShown;
        }

        @SuppressLint("DefaultLocale")
        @NonNull
        private String getText(boolean isMine, int votes, int participants) {
            String name = ballot.getName();

            if (TestUtil.isEmptyOrNull(name)) {
                name = getContext().getString(R.string.ballot_placeholder);
            } else {
                if (name.length() > MAX_BALLOT_TITLE_LENGTH) {
                    name = name.substring(0, MAX_BALLOT_TITLE_LENGTH);
                    name += "…";
                }
            }
            if (isMine) {
                return String.format("%s (%d/%d)", name, votes, participants);
            } else {
                return name;
            }
        }

        private void setAvatar() {
            new AsyncTask<Void, Void, Bitmap>() {
                @Override
                protected Bitmap doInBackground(Void... params) {
                    Bitmap bitmap = contactService.getAvatar(ballot.getCreatorIdentity(), false);
                    if (bitmap != null) {
                        return BitmapUtil.replaceTransparency(bitmap, Color.WHITE);
                    }
                    return null;
                }

                @Deprecated
                @Override
                protected void onPostExecute(Bitmap avatar) {
                    if (avatar != null) {
                        chip.setChipIcon(AvatarConverterUtil.convertToRound(getResources(), avatar));
                    } else {
                        chip.setChipIconResource(R.drawable.ic_vote_outline);
                    }
                }
            }.execute();
        }

        private void setColor(boolean isMine, int voters, int participants) {
            ColorStateList foregroundColor, backgroundColor;

            if (isMine && voters == participants) {
                // all votes are in
                foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnSecondaryContainer));
                backgroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorSecondaryContainer));
            } else {
                foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnPrimary));
                backgroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary));
            }

            chip.setTextColor(foregroundColor);
            chip.setChipBackgroundColor(backgroundColor);
        }
    }

    public interface OnCloseClickedListener {
        void onCloseClicked();
    }
}
