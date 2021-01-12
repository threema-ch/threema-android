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
import android.os.Bundle;
import android.os.Parcelable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;

public class SelectorDialog extends ThreemaDialogFragment {
	private SelectorDialogClickListener callback;
	private SelectorDialogInlineClickListener inlineCallback;
	private Activity activity;
	private Object object;
	private AlertDialog alertDialog;

	public static SelectorDialog newInstance(String title, ArrayList<String> items, String negative, SelectorDialogInlineClickListener listener) {
		// do not use inline callbacks in activities that don't have android:configChanges="orientation|screenSize|keyboardHidden" set
		// or fragments without setRetainInstance(true)
		SelectorDialog dialog = new SelectorDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putStringArrayList("items", items);
		args.putString("negative", negative);
		args.putParcelable("listener", listener);

		dialog.setArguments(args);
		return dialog;
	}

	public static SelectorDialog newInstance(String title, ArrayList<String> items, String negative) {
		SelectorDialog dialog = new SelectorDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putStringArrayList("items", items);
		args.putString("negative", negative);

		dialog.setArguments(args);
		return dialog;
	}

	public static SelectorDialog newInstance(String title, ArrayList<String> items, ArrayList<Integer> values, String negative) {
		SelectorDialog dialog = new SelectorDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putIntegerArrayList("values", values);
		args.putStringArrayList("items", items);
		args.putString("negative", negative);

		dialog.setArguments(args);
		return dialog;
	}

	public static SelectorDialog newInstance(String title, ArrayList<String> items, ArrayList<Integer> values, String negative, SelectorDialogInlineClickListener listener) {
		SelectorDialog dialog = new SelectorDialog();
		Bundle args = new Bundle();
		args.putString("title", title);
		args.putIntegerArrayList("values", values);
		args.putStringArrayList("items", items);
		args.putString("negative", negative);
		args.putParcelable("listener", listener);

		dialog.setArguments(args);
		return dialog;
	}

	public interface SelectorDialogClickListener {
		void onClick(String tag, int which, Object data);
		void onCancel(String tag);
		void onNo(String tag);
	}

	public interface SelectorDialogInlineClickListener extends Parcelable {
		void onClick(String tag, int which, Object data);
		void onCancel(String tag);
		void onNo(String tag);
	}

	public void setData(Object o) {
		object = o;
	}

	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialogInterface) {
		super.onCancel(dialogInterface);

		if (inlineCallback != null) {
			inlineCallback.onCancel(this.getTag());
		} else {
			callback.onCancel(this.getTag());
		}
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		String title = getArguments().getString("title");
		final ArrayList<String> items = getArguments().getStringArrayList("items");
		final ArrayList<Integer> values = getArguments().getIntegerArrayList("values");
		String negative = getArguments().getString("negative");
		SelectorDialogInlineClickListener listener = getArguments().getParcelable("listener");

		if (listener != null) {
			inlineCallback = listener;
		}

		final String tag = this.getTag();

		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity(), getTheme());
		if (title != null) {
			builder.setTitle(title);
		}
		builder.setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();

				if (values != null && values.size() > 0) {
					if (inlineCallback != null) {
						inlineCallback.onClick(tag, values.get(which), object);
					} else {
						callback.onClick(tag, values.get(which), object);
					}
				} else {
					if (inlineCallback != null) {
						inlineCallback.onClick(tag, which, object);
					} else {
						callback.onClick(tag, which, object);
					}
				}
			}
		});
		if (negative != null) {
			builder.setNegativeButton(negative, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					if (inlineCallback != null) {
						inlineCallback.onNo(tag);
					} else {
						callback.onNo(tag);
					}
				}
			});
		}

		alertDialog = builder.create();

		return alertDialog;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callback = (SelectorDialogClickListener) getTargetFragment();
		} catch (ClassCastException e) {
			//
		}

		// maybe called from an activity rather than a fragment
		if (callback == null) {
			if ((activity instanceof SelectorDialogClickListener)) {
				callback = (SelectorDialogClickListener) activity;
			}
		}
	}
}
