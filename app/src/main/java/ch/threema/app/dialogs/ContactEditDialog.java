/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.emojis.EmojiEditText;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.services.ContactService;
import ch.threema.app.ui.AvatarEditView;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.base.Contact;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.storage.models.ContactModel;

public class ContactEditDialog extends ThreemaDialogFragment implements AvatarEditView.AvatarEditListener {
	private static final Logger logger = LoggerFactory.getLogger(ContactEditDialog.class);

	// TODO Handle activity destruction after configuration change with "don't save activities" on

	private static final String ARG_TITLE = "title";
	private static final String ARG_TEXT1 = "text1";
	private static final String ARG_TEXT2 = "text2";
	private static final String ARG_HINT1 = "hint1";
	private static final String ARG_HINT2 = "hint2";
	private static final String ARG_IDENTITY = "identity";
	private static final String ARG_GROUP_ID = "groupId";

	private static final String BUNDLE_CROPPED_AVATAR_FILE = "cropped_avatar_file";;

	public static int CONTACT_AVATAR_HEIGHT_PX = 512;
	public static int CONTACT_AVATAR_WIDTH_PX = 512;

	private WeakReference<ContactEditDialogClickListener> callbackRef = new WeakReference<>(null);
	private Activity activity;
	private AvatarEditView avatarEditView;
	private File croppedAvatarFile = null;

	public static ContactEditDialog newInstance(ContactModel contactModel) {
		final int inputType = InputType.TYPE_CLASS_TEXT
			| InputType.TYPE_TEXT_VARIATION_PERSON_NAME
			| InputType.TYPE_TEXT_FLAG_CAP_WORDS;

		if(ContactUtil.isChannelContact(contactModel)) {
			//business contact don't have a second name
			return newInstance(
					R.string.edit_name_only,
					contactModel.getFirstName(),
					null,
					R.string.name,
					0,
					contactModel.getIdentity(),
					inputType,
					ContactUtil.CHANNEL_NAME_MAX_LENGTH_BYTES);

		}
		else {
			return newInstance(
					R.string.edit_name_only,
					contactModel.getFirstName(),
					contactModel.getLastName(),
					R.string.first_name,
					R.string.last_name,
					contactModel.getIdentity(),
					inputType,
					Contact.CONTACT_NAME_MAX_LENGTH_BYTES);
		}
	}

	public static ContactEditDialog newInstance(Bundle args) {
		ContactEditDialog dialog = new ContactEditDialog();
		dialog.setArguments(args);
		return dialog;
	}

	/**
	 * Create a ContactEditDialog with two input fields.
	 */
	public static ContactEditDialog newInstance(@StringRes int title, String text1, String text2,
	                                            @StringRes int hint1, @StringRes int hint2,
	                                            String identity, int inputType, int maxLength) {
		final Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putString(ARG_TEXT1, text1);
		args.putString(ARG_TEXT2, text2);
		args.putInt(ARG_HINT1, hint1);
		args.putInt(ARG_HINT2, hint2);
		args.putString(ARG_IDENTITY, identity);
		args.putInt("inputType", inputType);
		args.putInt("maxLength", maxLength);
		return newInstance(args);
	}

	/**
	 * Create a ContactEditDialog with just one input field.
	 */
	public static ContactEditDialog newInstance(@StringRes int title, String text1,
	                                            @StringRes int hint1,
	                                            String identity, int inputType, int maxLength) {
		final Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putString(ARG_TEXT1, text1);
		args.putInt(ARG_HINT1, hint1);
		args.putString(ARG_IDENTITY, identity);
		args.putInt("inputType", inputType);
		args.putInt("maxLength", maxLength);
		return newInstance(args);
	}

	/**
	 * Create a ContactEditDialog for a group
	 */
	public static ContactEditDialog newInstance(@StringRes int title, @StringRes int hint1, int groupId, int inputType, File avatarPreset, boolean useDefaultAvatar, int maxLength) {
		final Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_HINT1, hint1);
		args.putInt(ARG_GROUP_ID, groupId);
		args.putInt("inputType", inputType);
		args.putSerializable("avatarPreset", avatarPreset);
		args.putBoolean("useDefaultAvatar", useDefaultAvatar);
		args.putInt("maxLength", maxLength);

		return newInstance(args);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		if (this.avatarEditView != null) {
			this.avatarEditView.onActivityResult(requestCode, resultCode, intent);
		}
	}

	@Override
	public void onAvatarSet(File avatarFile) {
		croppedAvatarFile = avatarFile;
	}

	@Override
	public void onAvatarRemoved() {
		croppedAvatarFile = null;
	}

	public interface ContactEditDialogClickListener {
		void onYes(String tag, String text1, String text2, File avatar);
		void onNo(String tag);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callbackRef = new WeakReference<>((ContactEditDialogClickListener) getTargetFragment());
		} catch (ClassCastException e) {
			//
		}

		// called from an activity rather than a fragment
		if (callbackRef.get() == null) {
			if (!(activity instanceof ContactEditDialogClickListener)) {
				throw new ClassCastException("Calling fragment must implement ContactEditDialogClickListener interface");
			}
			callbackRef = new WeakReference<>((ContactEditDialogClickListener) activity);
		}
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		int title = getArguments().getInt(ARG_TITLE);
		String text1 = getArguments().getString(ARG_TEXT1);
		String text2 = getArguments().getString(ARG_TEXT2);
		int hint1 = getArguments().getInt(ARG_HINT1);
		int hint2 = getArguments().getInt(ARG_HINT2);
		String identity = getArguments().getString(ARG_IDENTITY);
		int groupId = getArguments().getInt(ARG_GROUP_ID);
		int inputType = getArguments().getInt("inputType");
		int maxLength = getArguments().getInt("maxLength", 0);

		final String tag = this.getTag();
		croppedAvatarFile = (File) getArguments().getSerializable("avatarPreset");

		ContactService contactService = null;
		try {
			contactService = ThreemaApplication.getServiceManager().getContactService();
		} catch (MasterKeyLockedException|FileSystemNotPresentException e) {
			logger.error("Exception", e);
		}

		if (savedInstanceState != null) {
			croppedAvatarFile = (File) savedInstanceState.getSerializable(BUNDLE_CROPPED_AVATAR_FILE);
		}

		final View dialogView = activity.getLayoutInflater().inflate(R.layout.dialog_contact_edit, null);
		final EmojiEditText editText1 = dialogView.findViewById(R.id.first_name);
		final EmojiEditText editText2 = dialogView.findViewById(R.id.last_name);
		final TextInputLayout editText1Layout = dialogView.findViewById(R.id.firstname_layout);
		final TextInputLayout editText2Layout = dialogView.findViewById(R.id.lastname_layout);

		avatarEditView = dialogView.findViewById(R.id.avatar_edit_view);

		if (savedInstanceState == null) {
			avatarEditView.setFragment(this);
			avatarEditView.setListener(this);
		}

		if (!TestUtil.empty(identity)) {
			avatarEditView.setVisibility(View.GONE);
			if (contactService != null) {
				ContactModel contactModel = contactService.getByIdentity(identity);

				//hide second name on business contact
				if (ContactUtil.isChannelContact(contactModel)) {
					ViewUtil.show(editText2, false);
				}
			}
		} else if (groupId != 0) {
			editText2.setVisibility(View.GONE);

			avatarEditView.setUndefinedAvatar(AvatarEditView.AVATAR_TYPE_GROUP);
			avatarEditView.setEditable(true);
		}

		if (hint1 != 0) {
			editText1Layout.setHint(getString(hint1));
		}

		if (hint2 != 0) {
			editText2Layout.setHint(getString(hint2));
		} else {
			editText2.setVisibility(View.GONE);
			editText2Layout.setVisibility(View.GONE);
		}

		if (!TestUtil.empty(text1)) {
			editText1.setText(text1);
		}

		if (!TestUtil.empty(text2)) {
			editText2.setText(text2);
		}

		if (inputType != 0) {
			editText1.setInputType(inputType);
			editText2.setInputType(inputType);
		}

		if (maxLength > 0) {
			editText1.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
			editText2.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
		}

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());

		if (title != 0) {
			builder.setTitle(title);
		}

		builder.setView(dialogView);

		builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						if (callbackRef.get() != null) {
							callbackRef.get().onYes(tag, editText1.getText().toString(), editText2.getText().toString(), croppedAvatarFile);
						}
					}
				}
		);
		builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						if (callbackRef.get() != null) {
							callbackRef.get().onNo(tag);
						}
					}
				}
		);

		setCancelable(false);

		return builder.create();
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialogInterface) {
		if (callbackRef.get() != null) {
			callbackRef.get().onNo(this.getTag());
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putSerializable(BUNDLE_CROPPED_AVATAR_FILE, croppedAvatarFile);
	}
}
