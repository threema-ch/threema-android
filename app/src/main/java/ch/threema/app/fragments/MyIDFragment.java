/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.fragments;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ExportIDActivity;
import ch.threema.app.activities.ProfilePicRecipientsActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.asynctasks.DeleteIdentityAsyncTask;
import ch.threema.app.asynctasks.LinkWithEmailAsyncTask;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.listeners.ProfileListener;
import ch.threema.app.listeners.SMSVerificationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.CheckIdentityRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.FingerPrintService;
import ch.threema.app.services.LocaleService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.AvatarEditView;
import ch.threema.app.ui.ImagePopup;
import ch.threema.app.ui.QRCodePopup;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.LinkMobileNoException;
import ch.threema.client.ProtocolDefines;
import ch.threema.localcrypto.MasterKeyLockedException;

import static ch.threema.app.ThreemaApplication.EMAIL_LINKED_PLACEHOLDER;
import static ch.threema.app.ThreemaApplication.PHONE_LINKED_PLACEHOLDER;

public class MyIDFragment extends MainFragment
		implements
		View.OnClickListener,
		GenericAlertDialog.DialogClickListener,
		TextEntryDialog.TextEntryDialogClickListener,
		PasswordEntryDialog.PasswordEntryDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(MyIDFragment.class);

	private static final int MAX_REVOCATION_PASSWORD_LENGTH = 256;
	private static final int LOCK_CHECK_REVOCATION = 33;
	private static final int LOCK_CHECK_DELETE_ID = 34;
	private static final int LOCK_CHECK_EXPORT_ID = 35;

	private ServiceManager serviceManager;
	private UserService userService;
	private PreferenceService preferenceService;
	private FingerPrintService fingerPrintService;
	private LocaleService localeService;
	private ContactService contactService;
	private FileService fileService;
	private AvatarEditView avatarView;
	private EmojiTextView nicknameTextView;
	private boolean hidden = false;
	private View fragmentView;

	private boolean isReadonlyProfile = false;
	private boolean isDisabledProfilePicReleaseSettings = false;

	private static final String DIALOG_TAG_EDIT_NICKNAME = "cedit";
	private static final String DIALOG_TAG_SET_REVOCATION_KEY = "setRevocationKey";
	private static final String DIALOG_TAG_LINKED_EMAIL = "linkedEmail";
	private static final String DIALOG_TAG_LINKED_MOBILE = "linkedMobile";
	private static final String DIALOG_TAG_REALLY_DELETE = "reallyDeleteId";
	private static final String DIALOG_TAG_DELETE_ID = "deleteId";
	private static final String DIALOG_TAG_LINKED_MOBILE_CONFIRM = "cfm";
	private static final String DIALOG_TAG_REVOKING = "revk";

	private final SMSVerificationListener smsVerificationListener = new SMSVerificationListener() {
		@Override
		public void onVerified() {
		 	RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updatePendingState(getView(), false);
				}
			});
		}

		@Override
		public void onVerificationStarted() {
		 	RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updatePendingState(getView(), false);
				}
			});
		}
	};

	private final ProfileListener profileListener = new ProfileListener() {
		@Override
		public void onAvatarChanged() {
			// a profile picture has been set so it's safe to assume user wants others to see his pic
			if (!isDisabledProfilePicReleaseSettings) {
				if (preferenceService != null && preferenceService.getProfilePicRelease() == PreferenceService.PROFILEPIC_RELEASE_NOBODY) {
					preferenceService.setProfilePicRelease(PreferenceService.PROFILEPIC_RELEASE_EVERYONE);
					RuntimeUtil.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							if (isAdded() && !isDetached() && fragmentView != null) {
								AppCompatSpinner spinner = fragmentView.findViewById(R.id.picrelease_spinner);
								if (spinner != null) {
									spinner.setSelection(preferenceService.getProfilePicRelease());
								}
							}
						}
					});
				}
			}
		}

		@Override
		public void onAvatarRemoved() {}

		@Override
		public void onNicknameChanged(String newNickname) {
			RuntimeUtil.runOnUiThread(() -> reloadNickname());
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setRetainInstance(true);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		if (!this.requiredInstances()) {
			logger.error("could not instantiate required objects");
			return null;
		}

		fragmentView = getView();

		if (fragmentView == null) {
			fragmentView = inflater.inflate(R.layout.fragment_my_id, container, false);

			this.updatePendingState(fragmentView, true);

			LayoutTransition l = new LayoutTransition();
			l.enableTransitionType(LayoutTransition.CHANGING);
			ViewGroup viewGroup = fragmentView.findViewById(R.id.fragment_id_container);
			viewGroup.setLayoutTransition(l);

			if (ConfigUtils.isWorkRestricted()) {
				Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__readonly_profile));
				if (value != null) {
					isReadonlyProfile = value;
				}

				value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_send_profile_picture));
				if (value != null) {
					isDisabledProfilePicReleaseSettings = value;
				}
			}

			TextView textView = fragmentView.findViewById(R.id.keyfingerprint);
			textView.setText(fingerPrintService.getFingerPrint(getIdentity()));

			fragmentView.findViewById(R.id.policy_explain).setVisibility(isReadonlyProfile || AppRestrictionUtil.isBackupsDisabled(ThreemaApplication.getAppContext()) || AppRestrictionUtil.isIdBackupsDisabled(ThreemaApplication.getAppContext()) ? View.VISIBLE : View.GONE);

			final ImageView picReleaseConfImageView = fragmentView.findViewById(R.id.picrelease_config);
			picReleaseConfImageView.setOnClickListener(this);
			picReleaseConfImageView.setVisibility(preferenceService.getProfilePicRelease() == PreferenceService.PROFILEPIC_RELEASE_SOME ? View.VISIBLE : View.GONE);

			configureEditWithButton(fragmentView.findViewById(R.id.linked_email_layout), fragmentView.findViewById(R.id.change_email), isReadonlyProfile);
			configureEditWithButton(fragmentView.findViewById(R.id.linked_mobile_layout), fragmentView.findViewById(R.id.change_mobile), isReadonlyProfile);

			configureEditWithButton(fragmentView.findViewById(R.id.delete_id_layout), fragmentView.findViewById(R.id.delete_id), isReadonlyProfile);
			configureEditWithButton(fragmentView.findViewById(R.id.revocation_key_layout), fragmentView.findViewById(R.id.revocation_key), isReadonlyProfile);
			configureEditWithButton(fragmentView.findViewById(R.id.export_id_layout), fragmentView.findViewById(R.id.export_id), (AppRestrictionUtil.isBackupsDisabled(ThreemaApplication.getAppContext()) ||
												AppRestrictionUtil.isIdBackupsDisabled(ThreemaApplication.getAppContext())));

			if (userService != null && userService.getIdentity() != null) {
				((TextView) fragmentView.findViewById(R.id.my_id)).setText(userService.getIdentity());
				fragmentView.findViewById(R.id.my_id_share).setOnClickListener(this);
				fragmentView.findViewById(R.id.my_id_qr).setOnClickListener(this);
			}

			this.avatarView = fragmentView.findViewById(R.id.avatar_edit_view);
			this.avatarView.setFragment(this);
			this.avatarView.setIsMyProfilePicture(true);
			this.avatarView.setContactModel(contactService.getMe());

			this.nicknameTextView = fragmentView.findViewById(R.id.nickname);

			if (isReadonlyProfile) {
				this.fragmentView.findViewById(R.id.profile_edit).setVisibility(View.GONE);
				this.avatarView.setEditable(false);
			} else {
				this.fragmentView.findViewById(R.id.profile_edit).setVisibility(View.VISIBLE);
				this.fragmentView.findViewById(R.id.profile_edit).setOnClickListener(this);
			}

			AppCompatSpinner spinner = fragmentView.findViewById(R.id.picrelease_spinner);
			ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(), R.array.picrelease_choices, android.R.layout.simple_spinner_item);
			adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			spinner.setAdapter(adapter);
			spinner.setSelection(preferenceService.getProfilePicRelease());
			spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					int oldPosition = preferenceService.getProfilePicRelease();
					preferenceService.setProfilePicRelease(position);
					picReleaseConfImageView.setVisibility(position == PreferenceService.PROFILEPIC_RELEASE_SOME ? View.VISIBLE : View.GONE);
					if (position == PreferenceService.PROFILEPIC_RELEASE_SOME && position != oldPosition) {
						launchProfilePictureRecipientsSelector(view);
					}
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {

				}
			});

			if (isDisabledProfilePicReleaseSettings) {
				fragmentView.findViewById(R.id.picrelease_spinner).setVisibility(View.GONE);
				fragmentView.findViewById(R.id.picrelease_config).setVisibility(View.GONE);
				fragmentView.findViewById(R.id.picrelease_text).setVisibility(View.GONE);
			}

			reloadNickname();
		}

		ListenerManager.profileListeners.add(this.profileListener);

		return fragmentView;
	}

	@Override
	public void onStart() {
		super.onStart();
		ListenerManager.smsVerificationListeners.add(this.smsVerificationListener);
	}

	@Override
	public void onStop() {
		ListenerManager.smsVerificationListeners.remove(this.smsVerificationListener);
		super.onStop();
	}

	@Override
	public void onDestroyView() {
		ListenerManager.profileListeners.remove(this.profileListener);
		super.onDestroyView();
	}

	private void updatePendingState(final View fragmentView, boolean force) {
		logger.debug("*** updatePendingState");

		if(!this.requiredInstances()) {
			return;
		}

		// update texts and enforce another update if the status of one value is pending
		if (updatePendingStateTexts(fragmentView) || force) {
			new Thread(
				new CheckIdentityRoutine(
					userService,
					success -> {
						//update after routine
						RuntimeUtil.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								updatePendingStateTexts(fragmentView);
							}
						});
					})
			).start();
		}
	}

	@SuppressLint("StaticFieldLeak")
	private boolean updatePendingStateTexts(View fragmentView) {
		boolean pending = false;

		logger.debug("*** updatePendingStateTexts");

		if(!this.requiredInstances()) {
			return false;
		}

		if (!isAdded() || isDetached() || isRemoving()) {
			return false;
		}

		//update email linked text
		TextView linkedEmailText = fragmentView.findViewById(R.id.linked_email);
		String email = this.userService.getLinkedEmail();
		email = EMAIL_LINKED_PLACEHOLDER.equals(email) ? getString(R.string.unchanged) : email;

		switch (userService.getEmailLinkingState()) {
			case UserService.LinkingState_LINKED:
				linkedEmailText.setText(email + " (" + getString(R.string.verified) + ")");
				//nothing;
				break;
			case UserService.LinkingState_PENDING:
				linkedEmailText.setText(email + " (" + getString(R.string.pending) + ")");
				pending = true;
				break;
			default:
				linkedEmailText.setText(getString(R.string.not_linked));

		}
		linkedEmailText.invalidate();

		//update mobile text
		TextView linkedMobileText = fragmentView.findViewById(R.id.linked_mobile);

		// default
		linkedMobileText.setText(getString(R.string.not_linked));

		String mobileNumber = this.userService.getLinkedMobile();
		mobileNumber = PHONE_LINKED_PLACEHOLDER.equals(mobileNumber) ? getString(R.string.unchanged) : mobileNumber;

		switch (userService.getMobileLinkingState()) {
			case UserService.LinkingState_LINKED:
				if (mobileNumber != null) {
					final String newMobileNumber = mobileNumber;
					// lookup phone numbers asynchronously
					new AsyncTask<TextView, Void, String>() {
						private TextView textView;

						@Override
						protected String doInBackground(TextView... params) {
							textView = params[0];

							if (isAdded() && getContext() != null) {
								final String verified = getContext().getString(R.string.verified);
								return localeService.getHRPhoneNumber(newMobileNumber) + " (" + verified + ")";
							}
							return null;
						}

						@Override
						protected void onPostExecute(String result) {
							if (isAdded() && !isDetached() && !isRemoving() && getContext() != null) {
								textView.setText(result);
							}
						}
					}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, linkedMobileText);
				}
				break;
			case UserService.LinkingState_PENDING:
				pending = true;

				final String newMobileNumber = this.userService.getLinkedMobile(true);
				if (newMobileNumber != null) {
					new AsyncTask<TextView, Void, String>() {
						private TextView textView;

						@Override
						protected String doInBackground(TextView... params) {
							if (isAdded() && getContext() != null && params != null) {
								textView = params[0];
								return (localeService != null ? localeService.getHRPhoneNumber(newMobileNumber) : "") + " (" + getContext().getString(R.string.pending) + ")";
							}
							return null;
						}

						@Override
						protected void onPostExecute(String result) {
							if (isAdded() && !isDetached() && !isRemoving() && getContext() != null) {
								textView.setText(result);
							}
						}
					}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, linkedMobileText);
				}
				break;
			default:

		}
		linkedMobileText.invalidate();

		//revocation key
		TextView revocationKey = fragmentView.findViewById(R.id.revocation_key_sum);
		new AsyncTask<TextView, Void, String>() {
			private TextView textView;

			@Override
			protected String doInBackground(TextView... params) {
				if (isAdded()) {
					textView = params[0];
					Date revocationKeyLastSet = userService.getLastRevocationKeySet();
					if (!isDetached() && !isRemoving() && getContext() != null) {
						if (revocationKeyLastSet != null) {
							return getContext().getString(R.string.revocation_key_set_at, LocaleUtil.formatTimeStampString(getContext(), revocationKeyLastSet.getTime(), true));
						} else {
							return getContext().getString(R.string.revocation_key_not_set);
						}
					}
				}
				return null;
			}

			@Override
			protected void onPostExecute(String result) {
				if (isAdded() && !isDetached() && !isRemoving() && getContext() != null) {
					textView.setText(result);
				}
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, revocationKey);

		return pending;
	}

	private void configureEditWithButton(RelativeLayout l, ImageView button, boolean disable) {
		if (disable) {
			button.setVisibility(View.INVISIBLE);
		} else {
			button.setOnClickListener(this);
		}
	}

	private String getIdentity() {
		if(!this.requiredInstances()) {
			return "undefined";
		}

		if (userService.hasIdentity()) {
			return userService.getIdentity();
		} else {
			return "undefined";
		}
	}

	private void deleteIdentity() {
		if(!this.requiredInstances()) {
			return;
		}

		new DeleteIdentityAsyncTask(getFragmentManager(), new Runnable() {
			@Override
			public void run() {
				System.exit(0);
			}
		}).execute();
	}

	private void setRevocationPassword() {
		DialogFragment dialogFragment = PasswordEntryDialog.newInstance(
				R.string.revocation_key_title,
				R.string.revocation_explain,
				R.string.password_hint,
				R.string.ok,
				R.string.cancel,
				8,
				MAX_REVOCATION_PASSWORD_LENGTH,
				R.string.backup_password_again_summary,
				0,0);
		dialogFragment.setTargetFragment(this, 0);
		dialogFragment.show(getFragmentManager(), DIALOG_TAG_SET_REVOCATION_KEY);
	}

	@Override
	public void onClick(View v) {
		int neutral;

		switch (v.getId()) {
			case R.id.change_email:
				neutral = 0;
				if (this.userService.getEmailLinkingState() != UserService.LinkingState_NONE) {
					neutral = R.string.unlink;
				}

				TextEntryDialog textEntryDialog = TextEntryDialog.newInstance(
						R.string.wizard2_email_linking,
						R.string.wizard2_email_hint,
						R.string.ok,
						neutral,
						R.string.cancel,
						userService.getLinkedEmail(),
						InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, TextEntryDialog.INPUT_FILTER_TYPE_NONE);
				textEntryDialog.setTargetFragment(this, 0);
				textEntryDialog.show(getFragmentManager(), DIALOG_TAG_LINKED_EMAIL);
				break;
			case R.id.change_mobile:
				String presetNumber = serviceManager.getLocaleService().getHRPhoneNumber(userService.getLinkedMobile());
				neutral = 0;
				if (this.userService.getMobileLinkingState() != UserService.LinkingState_NONE) {
					neutral = R.string.unlink;
				} else {
					presetNumber = localeService.getCountryCodePhonePrefix();
					if (!TestUtil.empty(presetNumber)) {
						presetNumber += " ";
					}
				}
				TextEntryDialog textEntryDialog1 = TextEntryDialog.newInstance(
						R.string.wizard2_phone_linking,
						R.string.wizard2_phone_hint,
						R.string.ok,
						neutral,
						R.string.cancel,
						presetNumber,
						InputType.TYPE_CLASS_PHONE,
						TextEntryDialog.INPUT_FILTER_TYPE_PHONE);
				textEntryDialog1.setTargetFragment(this, 0);
				textEntryDialog1.show(getFragmentManager(), DIALOG_TAG_LINKED_MOBILE);
				break;
			case R.id.revocation_key:
				if (!preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_NONE)) {
					HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, LOCK_CHECK_REVOCATION);
				} else {
					setRevocationPassword();
				}
				break;
			case R.id.delete_id:
				// ask for pin before entering
				if (!preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_NONE)) {
					HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, LOCK_CHECK_DELETE_ID);
				} else {
					confirmIdDelete();
				}
				break;
			case R.id.export_id:
				// ask for pin before entering
				if (!preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_NONE)) {
					HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, LOCK_CHECK_EXPORT_ID);
				} else {
					startActivity(new Intent(getContext(), ExportIDActivity.class));
				}
				break;
			case R.id.picrelease_config:
				launchProfilePictureRecipientsSelector(v);
				break;
			case R.id.profile_edit:
				TextEntryDialog nicknameEditDialog = TextEntryDialog.newInstance(R.string.set_nickname_title,
					R.string.wizard3_nickname_hint,
					R.string.ok, 0,
					R.string.cancel,
					userService.getPublicNickname(),
					InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
					0,
					ProtocolDefines.PUSH_FROM_LEN);;
				nicknameEditDialog.setTargetFragment(this, 0);
				nicknameEditDialog.show(getFragmentManager(), DIALOG_TAG_EDIT_NICKNAME);
				break;
			case R.id.my_id_qr:
				new QRCodePopup(getContext(), getActivity().getWindow().getDecorView(), getActivity()).show(v, null);
				break;
			case R.id.avatar:
				launchContactImageZoom(v);
				break;
			case R.id.my_id_share:
				ShareUtil.shareContact(getContext(), null);
				break;
		}
	}

	private void launchContactImageZoom(View v) {
		if (getView() != null) {
			View rootView = getView().findViewById(R.id.main_content);

			if (fileService.hasContactAvatarFile(contactService.getMe())) {
				ImagePopup detailPopup = new ImagePopup(getContext(), rootView, rootView.getWidth(), rootView.getHeight());
				detailPopup.show(v, contactService.getAvatar(contactService.getMe(), true), userService.getPublicNickname());
			}
		}
	}

	private void launchProfilePictureRecipientsSelector(View v) {
		AnimationUtil.startActivityForResult(getActivity(), v, new Intent(getContext(), ProfilePicRecipientsActivity.class), 55);
	}

	private void confirmIdDelete() {
		DialogFragment dialogFragment = GenericAlertDialog.newInstance(
				R.string.delete_id_title,
				R.string.delete_id_message,
				R.string.delete_id_title,
				R.string.cancel);
		((GenericAlertDialog) dialogFragment).setTargetFragment(this);
		dialogFragment.show(getFragmentManager(), DIALOG_TAG_DELETE_ID);
	}

	@SuppressLint("StaticFieldLeak")
	private void launchMobileVerification(final String normalizedPhoneNumber) {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				try {
					userService.linkWithMobileNumber(normalizedPhoneNumber);
				} catch (LinkMobileNoException e) {
					return e.getMessage();
				} catch (Exception e) {
					logger.error("Exception", e);
					return e.getMessage();
				}
				return null;
			}

			@Override
			protected void onPostExecute(String result) {
				if (isAdded() && !isDetached() && !isRemoving() && getContext() != null) {
					if (TestUtil.empty(result)) {
						Toast.makeText(getContext(), R.string.verification_started, Toast.LENGTH_LONG).show();
					} else {
						FragmentManager fragmentManager = getFragmentManager();
						if (fragmentManager != null) {
							updatePendingStateTexts(getView());
							SimpleStringAlertDialog.newInstance(R.string.verify_title, result).show(fragmentManager, "ve");
						}
					}
				}
			}
		}.execute();
	}

	@UiThread
	private void reloadNickname() {
		this.nicknameTextView.setText(!TestUtil.empty(userService.getPublicNickname()) ? userService.getPublicNickname() : userService.getIdentity());
	}

	@SuppressLint("StaticFieldLeak")
	private void setRevocationKey(String text) {
		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.revocation_key_title, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_REVOKING);
			}

			@Override
			protected Boolean doInBackground(Void... voids) {
				try {
					return userService.setRevocationKey(text);
				} catch (Exception x) {
					logger.error("Exception", x);
				}
				return false;
			}

			@Override
			protected void onPostExecute(Boolean success) {
				updatePendingStateTexts(getView());
				DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_REVOKING, true);
				if (!success) {
					Toast.makeText(getContext(), getString(R.string.error)+ ": " + getString(R.string.revocation_key_not_set), Toast.LENGTH_LONG).show();
				}
			}
		}.execute();
	}

	/*
	 * DialogFragment callbacks
	 */

	@Override
	public void onYes(String tag, Object data) {
		switch(tag) {
			case DIALOG_TAG_DELETE_ID:
				GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
						R.string.delete_id_title,
						R.string.delete_id_message2,
						R.string.delete_id_title,
						R.string.cancel);
				dialogFragment.setTargetFragment(this);
				dialogFragment.show(getFragmentManager(), DIALOG_TAG_REALLY_DELETE);
				break;
			case DIALOG_TAG_REALLY_DELETE:
				deleteIdentity();
				break;
			case DIALOG_TAG_LINKED_MOBILE_CONFIRM:
				launchMobileVerification((String) data);
				break;
			default:
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) { }

	@Override
	public void onYes(String tag, String text) {
		switch (tag) {
			case DIALOG_TAG_LINKED_MOBILE:
				final String normalizedPhoneNumber = localeService.getNormalizedPhoneNumber(text);
				GenericAlertDialog alertDialog = GenericAlertDialog.newInstance(R.string.wizard2_phone_number_confirm_title, String.format(getString(R.string.wizard2_phone_number_confirm), normalizedPhoneNumber), R.string.ok, R.string.cancel);
				alertDialog.setData(normalizedPhoneNumber);
				alertDialog.setTargetFragment(this);
				alertDialog.show(getFragmentManager(), DIALOG_TAG_LINKED_MOBILE_CONFIRM);
				break;
			case DIALOG_TAG_LINKED_EMAIL:
				new LinkWithEmailAsyncTask(getContext(), getFragmentManager(), text, () -> updatePendingStateTexts(getView())).execute();
				break;
			case DIALOG_TAG_EDIT_NICKNAME:
				// Update public nickname
				if (text != null && !text.equals(userService.getPublicNickname())) {
					if ("".equals(text.trim())) {
						text = userService.getIdentity();
					}
					userService.setPublicNickname(text);
				}
				reloadNickname();
				break;
			default:
				break;
		}
	}

	@Override
	public void onYes(String tag, final String text, boolean isChecked, Object data) {
		switch (tag) {
			case DIALOG_TAG_SET_REVOCATION_KEY:
				setRevocationKey(text);
		}
	}

	@Override
	public void onNo(String tag) {}

	@Override
	public void onNeutral(String tag) {
		switch (tag) {
			case DIALOG_TAG_LINKED_MOBILE:
				new Thread(() -> {
					try {
						userService.unlinkMobileNumber();
					} catch (Exception e) {
						LogUtil.exception(e, getActivity());
					} finally {
						 RuntimeUtil.runOnUiThread(() -> updatePendingStateTexts(getView()));
					}
				}).start();
				break;
			case DIALOG_TAG_LINKED_EMAIL:
				new LinkWithEmailAsyncTask(getContext(), getFragmentManager(), "", () -> updatePendingStateTexts(getView())).execute();
				break;
			default:
				break;
		}
	}

	final protected boolean requiredInstances() {
		if (!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	protected boolean checkInstances() {
		return TestUtil.required(
				this.serviceManager,
				this.fileService,
				this.userService,
				this.preferenceService,
				this.localeService,
				this.fingerPrintService);
	}

	protected void instantiate() {
		this.serviceManager = ThreemaApplication.getServiceManager();

		if (this.serviceManager != null) {
			try {
				this.contactService = this.serviceManager.getContactService();
				this.userService = this.serviceManager.getUserService();
				this.fileService = this.serviceManager.getFileService();
				this.preferenceService = this.serviceManager.getPreferenceService();
				this.localeService = this.serviceManager.getLocaleService();
				this.fingerPrintService = this.serviceManager.getFingerPrintService();
			} catch (MasterKeyLockedException e) {
				logger.debug("Master Key locked!");
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}
		}
	}

	public void onLogoClicked() {
		if (getView() != null) {
			NestedScrollView scrollView = getView().findViewById(R.id.fragment_id_container);
			if (scrollView != null) {
				scrollView.scrollTo(0, 0);
			}
		}
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (!hidden && hidden != this.hidden) {
			updatePendingState(getView(), false);
		}
		this.hidden = hidden;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		logger.info("saveInstance");
		super.onSaveInstanceState(outState);
	}

	/* callbacks from AvatarEditView */

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (this.avatarView != null) {
			this.avatarView.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		switch (requestCode) {
			case ThreemaActivity.ACTIVITY_ID_VERIFY_MOBILE:
				if (resultCode != Activity.RESULT_OK) {
					// todo: make sure its status is unlinked if linking failed
				}
				updatePendingState(getView(), false);
				break;
			case LOCK_CHECK_DELETE_ID:
				if (resultCode == Activity.RESULT_OK) {
					confirmIdDelete();
				}
				break;
			case LOCK_CHECK_EXPORT_ID:
				if (resultCode == Activity.RESULT_OK) {
					startActivity(new Intent(getContext(), ExportIDActivity.class));
				}
				break;
			case LOCK_CHECK_REVOCATION:
				if (resultCode == Activity.RESULT_OK) {
					setRevocationPassword();
				}
				break;
			default:
				if (this.avatarView != null) {
					this.avatarView.onActivityResult(requestCode, resultCode, intent);
				}
				break;
		}
	}

}
