/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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

package ch.threema.app.adapters.decorators;

import android.content.Context;
import android.os.Parcel;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.services.GroupService;
import ch.threema.app.ui.SelectorDialogItem;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.BallotUtil;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.data.media.BallotDataModel;

public class BallotChatAdapterDecorator extends ChatAdapterDecorator {
	private static final Logger logger = LoggerFactory.getLogger(BallotChatAdapterDecorator.class);

	private final static int ACTION_VOTE = 0, ACTION_RESULTS = 1, ACTION_CLOSE = 2;

	public BallotChatAdapterDecorator(Context context, AbstractMessageModel messageModel, Helper helper) {
		super(context, messageModel, helper);
	}

	@Override
	protected void configureChatMessage(final ComposeMessageHolder holder, final int position) {
		try {
			final AbstractMessageModel messageModel = this.getMessageModel();
			String explain = "";

			BallotDataModel ballotData = messageModel.getBallotData();
			if (ballotData == null) {
				throw new NotAllowedException("invalid ballot message");
			}

			final BallotModel ballotModel = this.helper.getBallotService().get(ballotData.getBallotId());

			if (ballotModel == null) {
				explain = "";
				holder.bodyTextView.setText("");
			} else {
				switch (ballotData.getType()) {
					case BALLOT_CREATED:
						if (ballotModel.getState() != BallotModel.State.CLOSED) {
							explain = getContext().getString(R.string.ballot_tap_to_vote);
						}
						break;
					case BALLOT_MODIFIED:
						if (ballotModel.getState() != BallotModel.State.CLOSED) {
							explain = getContext().getString(R.string.ballot_tap_to_vote);
						}
						break;
					case BALLOT_CLOSED:
						explain = getContext().getString(R.string.ballot_tap_to_view_results);
						break;
				}

				if (this.showHide(holder.bodyTextView, true)) {
					holder.bodyTextView.setText(ballotModel.getName());
				}
			}

			if (this.showHide(holder.secondaryTextView, true)) {
				holder.secondaryTextView.setText(explain);
			}

			this.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					onActionButtonClick(ballotModel);
				}
			}, holder.messageBlockView);

			if (holder.controller != null) {
				holder.controller.setImageResource(R.drawable.ic_outline_rule);
			}

		} catch (NotAllowedException x) {
			logger.error("Exception", x);
		}
	}

	private void onActionButtonClick(final BallotModel ballotModel) {
		if (getMessageModel() instanceof GroupMessageModel) {
			try {
				GroupService groupService = ThreemaApplication.getServiceManager().getGroupService();
				GroupMessageModel groupMessageModel = (GroupMessageModel) getMessageModel();

				if (groupService != null) {
					GroupModel groupModel = groupService.getById(groupMessageModel.getGroupId());

					if (groupModel != null) {
						if (groupService.isGroupMember(groupModel)) {
							showChooser(ballotModel);
						}
					}
				}
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		} else {
			showChooser(ballotModel);
		}
	}

	private void showChooser(final BallotModel ballotModel) {

		ArrayList<SelectorDialogItem> items = new ArrayList<>();
		final ArrayList<Integer> action = new ArrayList<>();
		String title = null;

		if (BallotUtil.canVote(ballotModel, helper.getMyIdentity())) {
			items.add(new SelectorDialogItem(getContext().getString(R.string.ballot_vote), R.drawable.ic_vote_outline));
			action.add(ACTION_VOTE);
		}

		if (BallotUtil.canViewMatrix(ballotModel, helper.getMyIdentity())) {
			if (ballotModel.getState() == BallotModel.State.CLOSED) {
				items.add(new SelectorDialogItem(getContext().getString(R.string.ballot_result_final), R.drawable.ic_ballot_outline));
			} else {
				items.add(new SelectorDialogItem(getContext().getString(R.string.ballot_result_intermediate), R.drawable.ic_ballot_outline));
			}
			action.add(ACTION_RESULTS);
		}

		if (BallotUtil.canClose(ballotModel, helper.getMyIdentity())) {
			items.add(new SelectorDialogItem(getContext().getString(R.string.ballot_close), R.drawable.ic_check));
			action.add(ACTION_CLOSE);
		}

		if (BallotUtil.canClose(ballotModel, helper.getMyIdentity())
			|| BallotUtil.canViewMatrix(ballotModel, helper.getMyIdentity())) {
			title = String.format(getContext().getString(R.string.ballot_received_votes),
				helper.getBallotService().getVotedParticipants(ballotModel.getId()).size(),
				helper.getBallotService().getParticipants(ballotModel.getId()).length);
		}

		if (items.size() > 1) {
			SelectorDialog selectorDialog = SelectorDialog.newInstance(title, items, null, new SelectorDialog.SelectorDialogInlineClickListener() {
				@Override
				public void onClick(String tag, int which, Object data) {
					switch (action.get(which)) {
						case ACTION_VOTE:
							BallotUtil.openVoteDialog(helper.getFragment().getFragmentManager(), ballotModel, helper.getMyIdentity());
							break;
						case ACTION_RESULTS:
							BallotUtil.openMatrixActivity(getContext(), ballotModel, helper.getMyIdentity());
							break;
						case ACTION_CLOSE:
							BallotUtil.requestCloseBallot(ballotModel, helper.getMyIdentity(), helper.getFragment(), null);
							break;
						default:
							break;
					}
				}

				@Override
				public void onCancel(String tag) {}

				@Override
				public void onNo(String tag) {}

				@Override
				public int describeContents() {return 0;}

				@Override
				public void writeToParcel(Parcel dest, int flags) {
				}
			});
			selectorDialog.show(helper.getFragment().getFragmentManager(), "chooseAction");
		} else {
			BallotUtil.openDefaultActivity(getContext(), helper.getFragment().getFragmentManager(), ballotModel, helper.getMyIdentity());
		}
	}
}
