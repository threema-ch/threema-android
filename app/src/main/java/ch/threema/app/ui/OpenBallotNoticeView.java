/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
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

import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.BallotListener;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.utils.AvatarConverterUtil;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ballot.BallotModel;

/**
 * A view that shows all open ballots (polls) for a chat in a ChipGroup and allows users to vote or close the ballot
 */
public class OpenBallotNoticeView extends ConstraintLayout implements DefaultLifecycleObserver, Chip.OnClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("OpenBallotNoticeView");
	private static final int MAX_BALLOTS_SHOWN = 20;
	private static final int MAX_BALLOT_TITLE_LENGTH = 25;
	private ChipGroup chipGroup;
	private BallotService ballotService;
	private UserService userService;
	private PreferenceService preferenceService;
	private ContactService contactService;
	private String identity;
	private MessageReceiver messageReceiver;
	private int numOpenBallots;

	private final BallotVoteListener ballotVoteListener = new BallotVoteListener() {
		@Override
		public void onSelfVote(BallotModel ballotModel) {
			RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
		}

		@Override
		public void onVoteChanged(BallotModel ballotModel, String votingIdentity, boolean isFirstVote) {
			RuntimeUtil.runOnUiThread(() -> updateBallotDisplay());
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
		inflater.inflate(R.layout.view_open_ballots, this);

		identity = userService.getIdentity();
	}

	@UiThread
	public void show(boolean animated) {
		if (getVisibility() != VISIBLE  && numOpenBallots > 0 && !preferenceService.getBallotOverviewHidden()) {
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
						public MessageReceiver getReceiver() {
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
				chipGroup.removeAllViews();
				numOpenBallots = ballotModels.size();
				if (numOpenBallots <= 0) {
					hide(false);
				} else {
					int i = 0;

					Chip firstChip = new Chip(getContext());
					ChipDrawable firstChipDrawable = ChipDrawable.createFromAttributes(getContext(),
						null,
						0,
						R.style.Chip_ChatNotice_Overview_Intro);
					firstChip.setChipDrawable(firstChipDrawable);
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						firstChip.setTextAppearance(R.style.TextAppearance_Chip_ChatNotice);
					} else {
						firstChip.setTextSize(14);
					}
					firstChip.setTextColor(ConfigUtils.getColorFromAttribute(getContext(), R.attr.text_color_openNotice));
					firstChip.setChipBackgroundColor(ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.background_openNotice)));
					firstChip.setText(R.string.ballot_open);
					firstChip.setClickable(false);
					chipGroup.addView(firstChip);

					int j = 0;
					for (BallotModel ballot: ballotModels) {
						// show only the latest MAX_BALLOTS_SHOWN open ballots
						if (i++ >= MAX_BALLOTS_SHOWN) {
							break;
						}

						int voters = ballotService.getVotedParticipants(ballot.getId()).size();
						int participants = ballotService.getParticipants(ballot.getId()).length;
						if (participants == 0) {
							continue;
						}

						String name = ballot.getName();

						if (TestUtil.empty(name)) {
							name = getContext().getString(R.string.ballot_placeholder);
						} else {
							if (name.length() > MAX_BALLOT_TITLE_LENGTH) {
								name = name.substring(0, MAX_BALLOT_TITLE_LENGTH);
								name += "…";
							}
						}

						Chip chip = new Chip(getContext());
						ChipDrawable chipDrawable = ChipDrawable.createFromAttributes(getContext(),
							null,
							0,
							R.style.Chip_ChatNotice_Overview);
						chip.setChipDrawable(chipDrawable);
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
							chip.setTextAppearance(R.style.TextAppearance_Chip_ChatNotice);
						} else {
							chip.setTextSize(14);
						}
						chip.setOnClickListener((View v) -> {
							OpenBallotNoticeView.this.onChipClick(v, voters == participants);
						});

						new AsyncTask<Void, Void, Bitmap>() {
							@Override
							protected Bitmap doInBackground(Void... params) {
								Bitmap bitmap = contactService.getAvatar(contactService.getByIdentity(ballot.getCreatorIdentity()), false);
								if (bitmap != null) {
									return BitmapUtil.replaceTransparency(bitmap, Color.WHITE);
								}
								return null;
							}

							@Override
							protected void onPostExecute(Bitmap avatar) {
								if (avatar != null) {
									chip.setChipIcon(AvatarConverterUtil.convertToRound(getResources(), avatar));
								} else {
									chip.setChipIconResource(R.drawable.ic_vote_outline);
								}
							}
						}.execute();

						chip.setTag(ballot);
						chip.setTextEndPadding(getResources().getDimensionPixelSize(R.dimen.chip_end_padding_text_only));

						ColorStateList foregroundColor, backgroundColor;
						boolean isMine = BallotUtil.isMine(ballot, userService);

						if (isMine) {
							chip.setText(name + " (" + voters + "/" + participants + ")");
						} else {
							chip.setText(name);
						}

						if (isMine && voters == participants) {
							// all votes are in
							if (ConfigUtils.getAppTheme(getContext()) == ConfigUtils.THEME_DARK) {
								foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.textColorSecondary));
								backgroundColor = ColorStateList.valueOf(getResources().getColor(R.color.material_red));
							} else {
								foregroundColor = ColorStateList.valueOf(getResources().getColor(R.color.material_red));
								backgroundColor = foregroundColor.withAlpha(getResources().getInteger(R.integer.chip_alpha));
							}
						} else {
							if (ConfigUtils.getAppTheme(getContext()) == ConfigUtils.THEME_DARK) {
								foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.textColorPrimary));
								backgroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorAccent));
							} else {
								foregroundColor = ColorStateList.valueOf(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorAccent));
								backgroundColor = foregroundColor.withAlpha(getResources().getInteger(R.integer.chip_alpha));
							}
						}

						chip.setTextColor(foregroundColor);
						chip.setChipBackgroundColor(backgroundColor);

						chipGroup.addView(chip);
						j++;
					}
					if (j > 0) {
						show(false);
					}
				}
			}
		}.execute();
	}

	public void setMessageReceiver(@NonNull MessageReceiver messageReceiver) {
		this.messageReceiver = messageReceiver;
		updateBallotDisplay();
	}

	public void setVisibilityListener(VisibilityListener listener) {
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();

		this.chipGroup = findViewById(R.id.chip_group);
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

	@Override
	public void onClick(View v) {
		BallotModel model = (BallotModel) v.getTag();

		if (BallotUtil.canClose(model, identity)) {
			int voters = ballotService.getVotedParticipants(model.getId()).size();
			int participants = ballotService.getParticipants(model.getId()).length;

			if (participants > 0 && voters == participants) {
				onChipClick(v, true);
				return;
			}
		}

		vote(model);
	}

	@SuppressLint("RestrictedApi")
	public void onChipClick(View v, boolean isVoteComplete) {
		BallotModel ballotModel = (BallotModel) v.getTag();

		if (ballotModel != null) {
			MenuBuilder menuBuilder = new MenuBuilder(getContext());
			new MenuInflater(getContext()).inflate(R.menu.chip_open_ballots, menuBuilder);

			ConfigUtils.themeMenu(menuBuilder, ConfigUtils.getColorFromAttribute(getContext(), R.attr.textColorSecondary));

			if (BallotUtil.canViewMatrix(ballotModel, identity)) {
				menuBuilder.findItem(R.id.menu_ballot_results).setTitle(ballotModel.getState() == BallotModel.State.CLOSED ? R.string.ballot_result_final : R.string.ballot_result_intermediate);
			}

			MenuItem highlightItem;
			@ColorInt int highlightColor;

			if (isVoteComplete) {
				highlightItem = menuBuilder.findItem(R.id.menu_ballot_close);
				highlightColor = getContext().getResources().getColor(R.color.material_red);
			} else {
				if (ballotService.hasVoted(ballotModel.getId(), userService.getIdentity())) {
					highlightItem = menuBuilder.findItem(R.id.menu_ballot_results);
				} else {
					highlightItem = menuBuilder.findItem(R.id.menu_ballot_vote);
				}
				highlightColor = ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorAccent);
			}
			SpannableString s = new SpannableString(highlightItem.getTitle());
			s.setSpan(new ForegroundColorSpan(highlightColor), 0, s.length(), 0);
			highlightItem.setTitle(s);
			ConfigUtils.themeMenuItem(highlightItem, highlightColor);

			menuBuilder.setCallback(new MenuBuilder.Callback() {
				@Override
				public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
					switch (item.getItemId()) {
						case R.id.menu_ballot_vote:
							vote(ballotModel);
							break;
						case R.id.menu_ballot_results:
							BallotUtil.openMatrixActivity(getContext(), ballotModel, identity);
							break;
						case R.id.menu_ballot_close:
							close(ballotModel);
							break;
						case R.id.menu_ballot_delete:
							delete(ballotModel);
							break;
					}
					return true;
				}

				@Override
				public void onMenuModeChange(MenuBuilder menu) {}
			});

			if (!BallotUtil.canViewMatrix(ballotModel, identity)) {
				menuBuilder.removeItem(R.id.menu_ballot_results);
			}

			if (!BallotUtil.canClose(ballotModel, identity)) {
				menuBuilder.removeItem(R.id.menu_ballot_close);;
			}

			Context wrapper = new ContextThemeWrapper(getContext(), ConfigUtils.getAppTheme(getContext()) == ConfigUtils.THEME_DARK ? R.style.AppBaseTheme_Dark : R.style.AppBaseTheme);
			MenuPopupHelper optionsMenu = new MenuPopupHelper(wrapper, menuBuilder, v);
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
						BallotUtil.closeBallot(getActivity(), model, ballotService);
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

	public interface VisibilityListener {
		void onDismissed();
	}
}
