/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.adapters.MentionSelectorAdapter;
import ch.threema.app.collections.Functional;
import ch.threema.app.collections.IPredicateNonNull;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

public class MentionSelectorPopup extends PopupWindow implements MentionSelectorAdapter.OnClickListener {

	private final Context context;
	private MentionSelectorAdapter mentionAdapter;
	private final GroupService groupService;
	private final ContactService contactService;
	private final UserService userService;
	private final PreferenceService preferenceService;
	private String filterText;
	private int filterStart;
	private final GroupModel groupModel;
	private final RecyclerView recyclerView;
	private final ContactModel allContactModel;
	private final MentionSelectorListener mentionSelectorListener;
	private ComposeEditText editText;
	private int viewableSpaceHeight;
	private final TextWatcher textWatcher = new TextWatcher() {
		private void run() {
			dismiss();
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			try {
				if (count == 0 && start == 0) { // @ at first position is deleted
					editText.post(this::run);
					return;
				}
				char last = s.charAt(start -1);
				if ((count == 0 && (' ' == last || '\n' == last)) // if backspace is hit until the @ is gone
					|| (count == 1 && (' ' == s.charAt(start) || '\n' == s.charAt(start)))) { // if spacebar or newline is added, escape the mention popup.
					editText.post(this::run);
				}
			}
			catch (IndexOutOfBoundsException e) {
				// don't care, happens when deleting a char after the @ the first time around
				// hacky because there is no other logic with the listener callback that would not mess with the rest of the logic.
			}
		}

		@Override
		public void afterTextChanged(Editable s) {
			if (TextUtils.isEmpty(s)) {// if text field is completely empty
				editText.post(this::run);
			}
			else if (!s.toString().equals(filterText)) {
				String filterTextAfterAtChar = null;
				int spacePosition = -1;
				try {
					filterTextAfterAtChar = s.toString().substring(filterStart);
					if (!TestUtil.empty(filterTextAfterAtChar)) {
						spacePosition = filterTextAfterAtChar.indexOf(" ");
						if (spacePosition == -1) {
							spacePosition = filterTextAfterAtChar.indexOf("\n");
						}
					}
				} catch (IndexOutOfBoundsException e) {
					//
				}

				if (spacePosition != -1) {
					filterText = s.toString().substring(0, filterStart + spacePosition);
				} else {
					filterText = s.toString();
				}

				updateList(false);
				updateRecyclerViewDimensions();
			}
		}
	};

	public MentionSelectorPopup(
		final Context context,
		MentionSelectorListener mentionSelectorListener,
		GroupService groupService,
		ContactService contactService,
		UserService userService,
		PreferenceService preferenceService,
		GroupModel groupModel
	) {
		super(context);

		this.context = context;
		this.groupService = groupService;
		this.contactService = contactService;
		this.userService = userService;
		this.preferenceService = preferenceService;
		this.groupModel = groupModel;
		this.mentionSelectorListener = mentionSelectorListener;
		this.allContactModel = new ContactModel(ContactService.ALL_USERS_PLACEHOLDER_ID, new byte[]{});
		this.allContactModel.setName(context.getString(R.string.all), "");
		this.allContactModel.setState(ContactModel.State.ACTIVE);

		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		MaterialCardView popupLayout = (MaterialCardView) layoutInflater.inflate(R.layout.popup_mention_selector, null, false);

		setContentView(popupLayout);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
		setAnimationStyle(0);
		setFocusable(false);
		setTouchable(true);
		setOutsideTouchable(false);
		setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		setWindowLayoutMode(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		setHeight(1);

		this.recyclerView = popupLayout.findViewById(R.id.group_members_list);

		LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
		linearLayoutManager.setStackFromEnd(true);

		this.recyclerView.setLayoutManager(linearLayoutManager);
		this.recyclerView.setItemAnimator(null);

		this.filterText = "";
		this.filterStart = 0;

		MentionSelectorAdapter adapter = updateList(true);

		if (adapter != null) {
			this.recyclerView.setAdapter(adapter);
		}
	}

	public void show(@NonNull Activity activity, @NonNull final ComposeEditText editText, @Nullable View boundary) {
		if (this.mentionAdapter == null) {
			dismiss();
			return;
		}

		int[] coordinates = getPositionCoordinates(activity, boundary != null ? boundary : editText);

		int popupX = 0;
		int popupY = coordinates[1] + context.getResources().getDimensionPixelSize(R.dimen.compose_bottom_panel_padding_vertical);

		this.editText = editText;
		editText.setLocked(true);
		editText.addTextChangedListener(textWatcher);
		this.filterStart = editText.getSelectionStart();

		this.viewableSpaceHeight = coordinates[2] - context.getResources().getDimensionPixelSize(R.dimen.compose_bottom_panel_padding_vertical);

		this.setWidth(activity.getWindowManager().getDefaultDisplay().getWidth());
		this.setHeight(this.viewableSpaceHeight);

		try {
			showAtLocation(editText, Gravity.LEFT | Gravity.BOTTOM, popupX, popupY);

			getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);
					AnimationUtil.slideInAnimation(getContentView(), true, 150);
				}
			});
		} catch (WindowManager.BadTokenException e) {
			//
		}
	}

	private void updateRecyclerViewDimensions() {
		int maxHeight = context.getResources().getDimensionPixelSize(R.dimen.group_detail_list_item_size) * this.mentionAdapter.getItemCount();

		this.recyclerView.getLayoutParams().height = Math.min(maxHeight, viewableSpaceHeight);
		this.recyclerView.requestLayout();
	}

	private MentionSelectorAdapter updateList(boolean init) {
		List<ContactModel> groupContacts = contactService.getByIdentities(groupService.getGroupIdentities(groupModel));
		final boolean isSortingFirstName = preferenceService.isContactListSortingFirstName();

		Collections.sort(groupContacts, (model1, model2) -> ContactUtil.getSafeNameString(model1, isSortingFirstName).compareTo(
			ContactUtil.getSafeNameString(model2, isSortingFirstName)
		));

		groupContacts.add(allContactModel);

		if (!init && filterText.length() - filterStart > 0) {
			groupContacts = Functional.filter(groupContacts, (IPredicateNonNull<ContactModel>) contactModel -> {
				String lowercaseName = filterText.substring(filterStart).toLowerCase();
				if (userService.isMe(contactModel.getIdentity()) && NameUtil.getQuoteName(contactModel, userService).toLowerCase().contains(lowercaseName)) {
					return true;
				}
				return ContactUtil.getSafeNameString(contactModel, isSortingFirstName).toLowerCase().contains(lowercaseName);
			});
		}

		if (groupContacts.isEmpty()) {// just show all selector as default placeholder if there are no more specific results
			groupContacts.add(allContactModel);
		}

		if (this.mentionAdapter == null) {
			this.mentionAdapter = new MentionSelectorAdapter(context, this.userService, this.contactService, this.groupService, this.groupModel);
			this.mentionAdapter.setOnClickListener(this);
		}

		if (mentionAdapter != null) {
			this.mentionAdapter.setData(groupContacts);
		}
		return this.mentionAdapter;
	}

	@Override
	public void onItemClick(View v, ContactModel contactModel) {
		if (contactModel != null) {
			String identity = contactModel.getIdentity();

			if (this.mentionSelectorListener != null) {
				dismiss();
				this.mentionSelectorListener.onContactSelected(identity, filterText != null ? filterText.length() - filterStart + 1 : 0, filterStart > 0 ? filterStart - 1 : 0);
			}
		}
	}

	@Override
	public void dismiss() {
		if (this.editText != null) {
			this.editText.removeTextChangedListener(textWatcher);
			this.editText.setLocked(false);
		}
		super.dismiss();
	}

	public interface MentionSelectorListener {
		void onContactSelected(String identity, int length, int insertPosition);
	}

	@SuppressWarnings("deprecation")
	private int[] getPositionCoordinates(@NonNull Activity activity, @NonNull View view) {
		int[] windowLocation = {0, 0};
		int[] screenLocation = {0, 0};
		view.getLocationInWindow(windowLocation);
		view.getLocationOnScreen(screenLocation);

		// In the ExpandableTextEntryDialog we need the values from the screen location
		boolean useLocationInWindow = windowLocation[1] == screenLocation[1];
		int location;
		if (useLocationInWindow) {
			location = windowLocation[1];
		} else {
			location = screenLocation[1] - view.getMeasuredHeight() / 2;
		}

		int screenHeight = activity.getWindowManager().getDefaultDisplay().getHeight();

		int x = useLocationInWindow ? windowLocation[0] : screenLocation[0];
		x += view.getPaddingLeft();
		int y = screenHeight - location;

		if (useLocationInWindow) {
			y += ConfigUtils.getNavigationBarHeight(activity);
		}

		// Status and action bar size is only included in window location
		int height = location - (useLocationInWindow ? ConfigUtils.getStatusBarHeight(context) + ConfigUtils.getActionBarSize(context) : 0);

		return new int[]{x, y, height};
	}

}
