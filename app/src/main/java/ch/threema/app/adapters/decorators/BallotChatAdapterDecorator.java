package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.view.View;

import org.slf4j.Logger;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageState;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.media.BallotDataModel;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class BallotChatAdapterDecorator extends ChatAdapterDecorator {
    private static final Logger logger = getThreemaLogger("BallotChatAdapterDecorator");

    public final static int ACTION_VOTE = 0, ACTION_RESULTS = 1, ACTION_CLOSE = 2;

    public interface BallotChatListener {
        void showSelectorDialog(
            ArrayList<Integer> action,
            String title,
            ArrayList<SelectorDialogItem> items,
            BallotModel ballotModel
        );

        void openDefaultActivity(BallotModel ballotModel, boolean canVote);
    }

    @NonNull
    private final BallotChatListener listener;

    public BallotChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper,
        @NonNull BallotChatListener listener
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);
        this.listener = listener;
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, Context context, int position) {
        try {
            final AbstractMessageModel messageModel = this.getMessageModel();
            String explain = "";

            BallotDataModel ballotData = messageModel.getBallotData();

            final BallotModel ballotModel = this.helper.getBallotService().get(ballotData.getBallotId());

            if (ballotModel == null) {
                holder.bodyTextView.setText("");
            } else {
                switch (ballotData.getType()) {
                    case BALLOT_CREATED:
                    case BALLOT_MODIFIED:
                        if (BallotUtil.canVote(ballotModel, helper.getMessageReceiver())) {
                            explain = context.getString(R.string.ballot_tap_to_vote);
                        }
                        break;
                    case BALLOT_CLOSED:
                        explain = context.getString(R.string.ballot_tap_to_view_results);
                        break;
                }

                if (this.showHide(holder.bodyTextView, true)) {
                    holder.bodyTextView.setText(ballotModel.getName());
                }
            }

            if (this.showHide(holder.secondaryTextView, true)) {
                holder.secondaryTextView.setText(explain);
            }

            this.setOnClickListener(new DebouncedOnClickListener(500) {
                @Override
                public void onDebouncedClick(View v) {
                    if (messageModel.getState() != MessageState.FS_KEY_MISMATCH && messageModel.getState() != MessageState.SENDFAILED) {
                        showChooser(v.getContext(), ballotModel);
                    }
                }
            }, holder.messageBlockView);

            if (holder.controller != null) {
                holder.controller.setIconResource(R.drawable.ic_outline_rule);
            }

            RuntimeUtil.runOnUiThread(() -> setupResendStatus(holder));
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    private void showChooser(Context context, final BallotModel ballotModel) {
        ArrayList<SelectorDialogItem> items = new ArrayList<>();
        final ArrayList<Integer> action = new ArrayList<>();
        String title = null;

        if (BallotUtil.canVote(ballotModel, helper.getMessageReceiver())) {
            items.add(new SelectorDialogItem(context.getString(R.string.ballot_vote), R.drawable.ic_vote_outline));
            action.add(ACTION_VOTE);
        }

        var canView = BallotUtil.canViewMatrix(ballotModel);
        if (canView) {
            if (ballotModel.getState() == BallotModel.State.CLOSED) {
                items.add(new SelectorDialogItem(context.getString(R.string.ballot_result_final), R.drawable.ic_ballot_outline));
            } else {
                items.add(new SelectorDialogItem(context.getString(R.string.ballot_result_intermediate), R.drawable.ic_ballot_outline));
            }
            action.add(ACTION_RESULTS);
        }

        var canClose = BallotUtil.canClose(ballotModel, helper.getMyIdentity(), helper.getMessageReceiver());
        if (canClose) {
            items.add(new SelectorDialogItem(context.getString(R.string.ballot_close), R.drawable.ic_check));
            action.add(ACTION_CLOSE);
        }

        if (canClose || canView) {
            title = String.format(context.getString(R.string.ballot_received_votes),
                helper.getBallotService().getVotedParticipants(ballotModel.getId()).size(),
                helper.getBallotService().getParticipants(ballotModel.getId()).length);
        }

        if (items.size() > 1) {
            listener.showSelectorDialog(action, title, items, ballotModel);
        } else if (!items.isEmpty()) {
            boolean canVote = BallotUtil.canVote(ballotModel, helper.getMessageReceiver());
            listener.openDefaultActivity(ballotModel, canVote);
        }
    }
}
