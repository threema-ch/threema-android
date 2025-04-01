/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.activities.ballot;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.exceptions.NoIdentityException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.ballot.BallotMatrixData;
import ch.threema.app.services.ballot.BallotMatrixService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.HintedImageView;
import ch.threema.app.ui.HintedTextView;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.BallotVoteModel;

public class BallotMatrixActivity extends BallotDetailActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BallotMatrixActivity");

    private BallotService ballotService;
    private ContactService contactService;
    private GroupService groupService;
    private String identity;
    private View scrollParent, noVotesView;

    private final BallotVoteListener ballotVoteListener = new BallotVoteListener() {
        @Override
        public void onSelfVote(BallotModel ballotModel) {
            ballotListener.onModified(ballotModel);
        }

        @Override
        public void onVoteChanged(BallotModel ballotModel, String votingIdentity, boolean isFirstVote) {
            ballotListener.onModified(ballotModel);
        }

        @Override
        public void onVoteRemoved(BallotModel ballotModel, String votingIdentity) {
            ballotListener.onModified(ballotModel);
        }

        @Override
        public boolean handle(BallotModel ballotModel) {
            return ballotListener.handle(ballotModel);
        }
    };

    private final BallotListener ballotListener = new BallotListener() {
        @Override
        public void onClosed(BallotModel ballotModel) {
            this.onModified(ballotModel);
        }

        @Override
        public void onModified(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> {
                //keep it simple man!
                updateView();
            });
        }

        @Override
        public void onCreated(BallotModel ballotModel) {
            //ignore
        }

        @Override
        public void onRemoved(BallotModel ballotModel) {
            RuntimeUtil.runOnUiThread(() -> {
                Toast.makeText(BallotMatrixActivity.this, "ballot removed", Toast.LENGTH_SHORT).show();
                finish();
            });
        }

        @Override
        public boolean handle(BallotModel b) {
            return getBallotModel() != null && b != null
                && getBallotModel().getId() == b.getId();
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!this.requireInstancesOrExit()) {
            return;
        }

        int ballotId = IntentDataUtil.getBallotId(this.getIntent());

        if (ballotId != 0) {
            try {
                BallotModel ballotModel = this.ballotService.get(ballotId);
                if (ballotModel == null) {
                    throw new ThreemaException("invalid ballot");
                }

                this.setBallotModel(ballotModel);
            } catch (ThreemaException e) {
                LogUtil.exception(e, this);
                finish();
                return;
            }
        }

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            if (getBallotModel().getState() == BallotModel.State.CLOSED) {
                actionBar.setTitle(R.string.ballot_result_final);
            } else {
                actionBar.setTitle(R.string.ballot_result_intermediate);
            }
        }

        TextView textView = findViewById(R.id.text_view);
        if (TestUtil.required(textView, this.getBallotModel().getName())) {
            textView.setText(this.getBallotModel().getName());
        }

        noVotesView = findViewById(R.id.no_votes_yet);
        scrollParent = findViewById(R.id.scroll_parent);

        ListenerManager.ballotListeners.add(this.ballotListener);
        ListenerManager.ballotVoteListeners.add(this.ballotVoteListener);
        this.updateView();
    }

    @Override
    public int getLayoutResource() {
        return R.layout.activity_ballot_matrix;
    }

    private void updateView() {
        TableLayout dataTableLayout = findViewById(R.id.matrix_data);

        if (dataTableLayout == null) {
            logger.error("The data table layout is null");
            return;
        }

        dataTableLayout.removeAllViews();

        BallotModel.DisplayType displayType = BallotModel.DisplayType.LIST_MODE;

        final BallotModel ballotModel = ballotService.get(this.getBallotModelId());
        if (ballotModel != null) {
            displayType = ballotModel.getDisplayType();
        }

        final BallotMatrixData matrixData = this.ballotService.getMatrixData(this.getBallotModelId());

        if (matrixData == null) {
            //wrong data! exit now
            Toast.makeText(this, "invalid data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        List<BallotMatrixService.Participant> allParticipants = getAllParticipants(matrixData, displayType);
        List<BallotMatrixService.Participant> votedParticipants = new ArrayList<>();
        List<BallotMatrixService.Participant> notVotedParticipants = new ArrayList<>();

        for (BallotMatrixService.Participant participant : allParticipants) {
            if (participant.hasVoted()) {
                votedParticipants.add(participant);
            } else {
                notVotedParticipants.add(participant);
            }
        }

        if (votedParticipants.isEmpty() && displayType == BallotModel.DisplayType.LIST_MODE) {
            // no votes
            noVotesView.setVisibility(View.VISIBLE);
            scrollParent.setVisibility(View.GONE);
            return;
        }

        noVotesView.setVisibility(View.GONE);
        scrollParent.setVisibility(View.VISIBLE);

        dataTableLayout.addView(getHeaderRow(votedParticipants));

        for (BallotMatrixService.Choice c : matrixData.getChoices()) {
            // create a new row for each answer
            TableRow row = new TableRow(this);

            // add answer first
            View headerCell = getLayoutInflater().inflate(R.layout.row_cell_ballot_matrix_choice_label, null);
            ((HintedTextView) headerCell.findViewById(R.id.choice_label)).setText(c.getBallotChoiceModel().getName());
            row.addView(headerCell);

            // add sums
            View sumCell = getLayoutInflater().inflate(R.layout.row_cell_ballot_matrix_choice_sum, null);
            TextView sumText = sumCell.findViewById(R.id.voting_sum);

            sumText.setText(String.valueOf(c.getVoteCount()));

            if (c.isWinner()) {
                sumCell.findViewById(R.id.cell).setBackgroundResource(R.drawable.matrix_winner_cell);
                sumText.setTextColor(getResources().getColor(android.R.color.white));
            }

            row.addView(sumCell);

            for (BallotMatrixService.Participant p : votedParticipants) {
                row.addView(getVotedParticipantView(matrixData, p, c));
            }

            dataTableLayout.addView(row);
        }

        TextView notVotedTextView = findViewById(R.id.not_voted);
        MaterialCardView notVotedContainer = findViewById(R.id.not_voted_container);

        if (contactService != null && notVotedParticipants.size() > 0) {
            notVotedContainer.setVisibility(View.VISIBLE);
            String userList = "";

            for (BallotMatrixService.Participant p : notVotedParticipants) {
                if (!"".equals(userList)) {
                    userList += ", ";
                }
                userList += NameUtil.getDisplayNameOrNickname(p.getIdentity(), contactService);
            }
            notVotedTextView.setText(getString(R.string.not_voted_user_list, userList));
        } else {
            notVotedContainer.setVisibility(View.GONE);
        }
    }

    @NonNull
    private List<BallotMatrixService.Participant> getAllParticipants(@NonNull BallotMatrixData matrixData, @NonNull BallotModel.DisplayType displayType) {
        List<BallotMatrixService.Participant> allParticipants = matrixData.getParticipants();

        if (displayType == BallotModel.DisplayType.SUMMARY_MODE) {
            for (BallotMatrixService.Participant p : allParticipants) {
                if (p.getIdentity().equals(getMyIdentity())) {
                    return Collections.singletonList(p);
                }
            }
        }

        return allParticipants;
    }

    @NonNull
    private TableRow getHeaderRow(@NonNull List<BallotMatrixService.Participant> votedParticipants) {
        // add header row containing names/avatars of participants
        TableRow nameHeaderRow = new TableRow(this);

        getLayoutInflater().inflate(R.layout.row_cell_ballot_matrix_empty, nameHeaderRow);

        getLayoutInflater().inflate(R.layout.row_cell_ballot_matrix_empty, nameHeaderRow);

        for (BallotMatrixService.Participant p : votedParticipants) {
            final ContactModel contactModel = this.contactService.getByIdentity(p.getIdentity());

            View nameCell = getLayoutInflater().inflate(R.layout.row_cell_ballot_matrix_name, null);
            String name = NameUtil.getDisplayNameOrNickname(contactModel, true);

            HintedImageView hintedImageView = nameCell.findViewById(R.id.avatar_view);
            if (hintedImageView != null) {
                hintedImageView.setContentDescription(name);

                Bitmap avatar = contactService.getAvatar(contactModel, false);
                hintedImageView.setImageBitmap(avatar);
            }

            nameHeaderRow.addView(nameCell);
        }

        return nameHeaderRow;
    }

    @NonNull
    private View getVotedParticipantView(@NonNull BallotMatrixData matrixData, @NonNull BallotMatrixService.Participant p, @NonNull BallotMatrixService.Choice c) {
        View choiceVoteView;

        if (c.isWinner()) {
            choiceVoteView = getLayoutInflater().inflate(R.layout.row_cell_ballot_matrix_choice_winner, null);
        } else {
            choiceVoteView = getLayoutInflater().inflate(R.layout.row_cell_ballot_matrix_choice, null);
        }

        BallotVoteModel vote = matrixData.getVote(p, c);
        ViewUtil.show(
            (View) choiceVoteView.findViewById(R.id.voting_value_1),
            p.hasVoted() && vote != null && vote.getChoice() == 1);


        ViewUtil.show(
            (View) choiceVoteView.findViewById(R.id.voting_value_0),
            p.hasVoted() && (vote == null || vote.getChoice() != 1));

        ViewUtil.show(
            (View) choiceVoteView.findViewById(R.id.voting_value_none),
            !p.hasVoted());

        return choiceVoteView;
    }

    @Override
    protected void onDestroy() {
        ListenerManager.ballotListeners.remove(this.ballotListener);
        ListenerManager.ballotVoteListeners.remove(this.ballotVoteListener);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected boolean checkInstances() {
        return TestUtil.required(
            this.ballotService,
            this.contactService,
            this.groupService,
            this.identity);
    }

    @Override
    protected void instantiate() {
        super.instantiate();
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager != null) {
            try {
                this.ballotService = serviceManager.getBallotService();
                this.identity = serviceManager.getUserService().getIdentity();
                this.contactService = serviceManager.getContactService();
                this.groupService = serviceManager.getGroupService();
            } catch (NoIdentityException | MasterKeyLockedException |
                     FileSystemNotPresentException e) {
                logger.error("Exception", e);
            }
        }
    }

    private boolean requireInstancesOrExit() {
        if (!this.requiredInstances()) {
            logger.error("Required instances failed");
            this.finish();
            return false;
        }
        return true;
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        return true;
    }

    @Override
    protected void handleOnBackPressed() {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    BallotService getBallotService() {
        if (this.requiredInstances()) {
            return this.ballotService;
        }

        return null;
    }

    @Override
    public ContactService getContactService() {
        if (requiredInstances()) {
            return this.contactService;
        }
        return null;
    }

    @Override
    public GroupService getGroupService() {
        if (requiredInstances()) {
            return this.groupService;
        }
        return null;
    }

    @Override
    public String getIdentity() {
        if (requiredInstances()) {
            return this.identity;
        }
        return null;
    }
}
