/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.dialogs;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.adapters.ballot.BallotVoteListAdapter;
import ch.threema.app.emojis.EmojiConversationTextView;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.services.ballot.BallotVoteResult;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.LoadingUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.storage.models.ballot.BallotChoiceModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class BallotVoteDialog extends ThreemaDialogFragment {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BallotVoteDialog");

    private Activity activity;
    private AlertDialog alertDialog;
    private ListView listView;
    private BallotModel ballotModel;

    private BallotService ballotService;
    private String identity;
    private int ballotId;
    private BallotVoteListAdapter listAdapter = null;
    private EmojiConversationTextView titleTextView;

    private boolean disableBallotModelListener = false;

    private Thread votingThread = null;

    private final BallotListener ballotListener = new BallotListener() {
        @Override
        public void onClosed(BallotModel ballotModel) {
        }

        @Override
        public void onModified(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> updateView());
        }

        @Override
        public void onCreated(BallotModel ballotModel) {
        }

        @Override
        public void onRemoved(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> {
                Toast.makeText(getContext(), "ballot removed", Toast.LENGTH_SHORT).show();
                dismiss();
            });
        }

        @Override
        public boolean handle(BallotModel ballotModel) {
            return !disableBallotModelListener && ballotId == ballotModel.getId();
        }
    };

    private final BallotVoteListener ballotVoteListener = new BallotVoteListener() {
        @Override
        public void onSelfVote(BallotModel ballotModel) {
        }

        @Override
        public void onVoteChanged(BallotModel ballotModel, String votingIdentity, boolean isFirstVote) {
            RuntimeUtil.runOnUiThread(() -> updateView());
        }

        @Override
        public void onVoteRemoved(BallotModel ballotModel, String votingIdentity) {
            RuntimeUtil.runOnUiThread(() -> updateView());
        }

        @Override
        public boolean handle(BallotModel b) {
            return b != null && ballotModel != null && b.getId() == ballotModel.getId();
        }
    };

    public static BallotVoteDialog newInstance(@StringRes int ballotId) {
        BallotVoteDialog dialog = new BallotVoteDialog();
        Bundle args = new Bundle();
        args.putInt("ballotId", ballotId);

        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        ListenerManager.ballotListeners.add(this.ballotListener);
        ListenerManager.ballotVoteListeners.add(this.ballotVoteListener);
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
    }

    @Override
    public void onDetach() {
        this.activity = null;

        super.onDetach();
    }

    @NonNull
    @Override
    public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState != null && alertDialog != null) {
            return alertDialog;
        }

        ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        if (serviceManager != null) {
            try {
                this.ballotService = serviceManager.getBallotService();
                this.identity = serviceManager.getUserService().getIdentity();
            } catch (Exception e) {
                logger.error("Exception", e);
                return null;
            }
        }

        ballotId = getArguments().getInt("ballotId");
        ballotModel = ballotService.get(ballotId);

        final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_ballot_vote, null);
        this.listView = dialogView.findViewById(R.id.ballot_list);
        this.titleTextView = dialogView.findViewById(R.id.title);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
        builder.setView(dialogView);
        builder.setPositiveButton(getString(R.string.ballot_vote), (dialog, whichButton) -> vote());
        builder.setNegativeButton(R.string.cancel, (dialog, whichButton) -> dismiss());

        alertDialog = builder.create();
        if (titleTextView != null) {
            titleTextView.setText(ballotModel.getName());
        }

        return alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (this.listView != null) {
            this.listView.setOnItemClickListener((adapterView, view, i, l) -> {
                ((CheckableRelativeLayout) view).toggle();
            });
            this.listView.setClipToPadding(false);
        }

        this.updateView();
    }

    @Override
    public void onDestroy() {
        ListenerManager.ballotListeners.remove(this.ballotListener);
        ListenerManager.ballotVoteListeners.remove(this.ballotVoteListener);
        super.onDestroy();
    }

    /******/

    private void updateView() {
        try {
            if (this.ballotId <= 0) {
                dismiss();
                return;
            }

            try {
                this.disableBallotModelListener = true;
                BallotModel ballotModel = this.ballotService.get(this.ballotId);

                if (ballotModel == null && activity != null) {
                    Toast.makeText(activity, R.string.ballot_not_exist, Toast.LENGTH_SHORT).show();
                    logger.error("invalid ballot model");
                    dismiss();
                    return;
                }

                this.ballotService.viewingBallot(ballotModel, true);
            } finally {
                //important!
                this.disableBallotModelListener = false;
            }

            Map<Integer, Integer> selected;

            if (this.listAdapter != null) {
                selected = this.listAdapter.getSelectedChoices();
            } else {
                //load from db
                selected = new HashMap<>();
                for (final BallotVoteModel c : this.ballotService.getMyVotes(this.ballotId)) {
                    selected.put(c.getBallotChoiceId(), c.getChoice());
                }
            }
            List<BallotChoiceModel> ballotChoiceModelList = this.ballotService.getChoices(this.ballotId);
            boolean showVoting = this.ballotModel.getType() == BallotModel.Type.INTERMEDIATE || this.ballotModel.getState() == BallotModel.State.CLOSED;
            this.listAdapter = new BallotVoteListAdapter(
                getContext(),
                ballotChoiceModelList,
                selected,
                this.ballotModel.getState() != BallotModel.State.OPEN,
                this.ballotModel.getAssessment() == BallotModel.Assessment.MULTIPLE_CHOICE,
                showVoting);
            this.listView.setAdapter(this.listAdapter);
//			this.alertDialog.setTitle(EmojiMarkupUtil.getInstance().addTextSpans(ballotModel.getName()));
        } catch (NotAllowedException e) {
            logger.error("cannot reload choices", e);
        }
    }

    private void vote() {
        //show loading
        if (!BallotUtil.canVote(this.ballotModel, this.identity)) {
            return;
        }

        logger.debug("vote");
        if (this.votingThread != null && this.votingThread.isAlive()) {
            logger.debug("voting thread alive, abort");
            return;
        }

        logger.debug("create new voting thread");
        this.votingThread = LoadingUtil.runInAlert(getFragmentManager(),
            R.string.ballot_vote,
            R.string.please_wait,
            new Runnable() {
                @Override
                public void run() {
                    try {
                        voteThread();
                        dismiss();
                    } catch (Exception x) {
                        logger.error("Exception", x);
                    }
                }
            });
    }

    private void voteThread() {
        if (!BallotUtil.canVote(ballotModel, identity)) {
            return;
        }
        try {
            final BallotVoteResult result = this.ballotService.vote(ballotModel.getId(), this.listAdapter.getSelectedChoices(), TriggerSource.LOCAL);
            if (result != null) {
                RuntimeUtil.runOnUiThread(() -> {
                    if (activity != null) {
                        if (result.isSuccess()) {
                            Toast.makeText(activity, R.string.ballot_vote_posted_successfully, Toast.LENGTH_SHORT).show();
                            dismiss();
                        } else {
                            Toast.makeText(activity, R.string.ballot_vote_posted_failed, Toast.LENGTH_SHORT).show();
                            updateView();
                        }
                    }
                });
            }

        } catch (final NotAllowedException e) {
            RuntimeUtil.runOnUiThread(() -> {
                if (activity != null) {
                    Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
