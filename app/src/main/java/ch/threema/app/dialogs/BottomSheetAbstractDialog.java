/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2022 Threema GmbH
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
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialog;
import ch.threema.app.R;
import ch.threema.app.adapters.BottomSheetGridAdapter;
import ch.threema.app.adapters.BottomSheetListAdapter;
import ch.threema.app.ui.BottomSheetItem;

public abstract class BottomSheetAbstractDialog extends BottomSheetDialogFragment {
	private BottomSheetDialogCallback callback;
	private BottomSheetDialogInlineClickListener inlineCallback;

	private Activity activity;

	public interface BottomSheetDialogCallback {
		void onSelected(String tag);
	}

	public interface BottomSheetDialogInlineClickListener extends Parcelable {
		void onSelected(String tag);
		void onCancel(String tag);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			callback = (BottomSheetDialogCallback) getTargetFragment();
		} catch (ClassCastException e) {
			//
		}

		// called from an activity rather than a fragment
		if (callback == null) {
			if ((activity instanceof BottomSheetDialogCallback)) {
				callback = (BottomSheetDialogCallback) activity;
			}
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		this.activity = activity;
	}

	@Override
	public void onResume() {
		super.onResume();

		// Hack to set width of bottom sheet
		WindowManager wm = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
		Display display = wm.getDefaultDisplay();
		DisplayMetrics metrics = new DisplayMetrics();
		display.getMetrics(metrics);
		int width = metrics.widthPixels < 1440 ? metrics.widthPixels : 1440;
		int height = -1;

		Window window = getDialog().getWindow();
		if (window != null) {
			window.setLayout(width, height);
		}
	}

	@NonNull
	@Override
	public AppCompatDialog onCreateDialog(Bundle savedInstanceState) {
		int title = getArguments().getInt("title");
		int selectedItem = getArguments().getInt("selected");
		final ArrayList<BottomSheetItem> items = getArguments().getParcelableArrayList("items");
		BottomSheetDialogInlineClickListener listener = getArguments().getParcelable("listener");

		final BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

		final View dialogView = activity.getLayoutInflater().inflate(
					this instanceof BottomSheetGridDialog ?
					R.layout.dialog_bottomsheet_grid :
					R.layout.dialog_bottomsheet_list,
					null);
		final AbsListView listView = dialogView.findViewById(R.id.list_view);
		final TextView titleView = dialogView.findViewById(R.id.title_text);

		if (listener != null) {
			inlineCallback = listener;
		}

		if (title != 0) {
			titleView.setText(title);
		} else {
			titleView.setVisibility(View.GONE);
		}

		listView.setAdapter(
				this instanceof BottomSheetGridDialog ?
				new BottomSheetGridAdapter(getContext(), items) :
				new BottomSheetListAdapter(getContext(), items, selectedItem));
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
				if (items != null && i < items.size()) {
					dismiss();
					if (inlineCallback != null) {
						inlineCallback.onSelected(items.get(i).getTag());
					} else {
						callback.onSelected(items.get(i).getTag());
					}
				}
			}
		});

		dialog.setContentView(dialogView);
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override
			public void onShow(DialogInterface dialog) {
				BottomSheetDialog d = (BottomSheetDialog) dialog;

				final FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
				if (bottomSheet != null) {
					bottomSheet.post(new Runnable() {
						@Override
						public void run() {
							// there will be no rounded corners in expanded state due to this
							// https://github.com/material-components/material-components-android/pull/437#issuecomment-536668983
							BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
						}
					});
				}
			}
		});

		return dialog;
	}

	@Override
	public void onCancel(@NonNull DialogInterface dialog) {
		super.onCancel(dialog);

		if (inlineCallback != null) {
			inlineCallback.onCancel(this.getTag());
		}
	}
}
