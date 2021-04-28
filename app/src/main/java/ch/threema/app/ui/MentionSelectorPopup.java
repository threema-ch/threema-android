/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import java.util.Collections;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;

public class MentionSelectorPopup extends PopupWindow implements MentionSelectorAdapter.OnClickListener {

	private Context context;
	private LinearLayout popupLayout;
	private MentionSelectorAdapter mentionAdapter;
	private GroupService groupService;
	private ContactService contactService;
	private UserService userService;
	private PreferenceService preferenceService;
	private String filterText;
	private int filterStart;
	private GroupModel groupModel;
	private RecyclerView recyclerView;
	private ContactModel allContactModel;
	private MentionSelectorListener mentionSelectorListener;
	private ComposeEditText editText;
	private int dividersHeight, viewableSpaceHeight;
	private int popupY, popupX;
	private TextWatcher textWatcher = new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}

		@Override
		public void afterTextChanged(Editable s) {
			if (TextUtils.isEmpty(s)) {
				editText.post(new Runnable() {
					@Override
					public void run() {
						dismiss();
					}
				});
			}
			else if (!s.toString().equals(filterText)) {
				String filterTextAfterAtChar = null;
				int spacePosition = -1;
				try {
					filterTextAfterAtChar = s.toString().substring(filterStart);
					if (!TestUtil.empty(filterTextAfterAtChar)) {
						spacePosition = filterTextAfterAtChar.indexOf(" ");
					}
				} catch (IndexOutOfBoundsException e) {
					//
				}

				if (spacePosition != -1) {
					filterText = s.toString().substring(0, filterStart + spacePosition);
					editText.setSelection(filterStart + spacePosition);
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
		this.popupLayout = (LinearLayout) layoutInflater.inflate(R.layout.popup_mention_selector, null, false);

		setContentView(popupLayout);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
		setAnimationStyle(0);
		setFocusable(false);
		setTouchable(true);
		setOutsideTouchable(false);
		setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

		this.recyclerView = this.popupLayout.findViewById(R.id.group_members_list);

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

	public void show(Activity activity, final ComposeEditText editText, final int originXOffset) {
		if (this.mentionAdapter == null) {
			dismiss();
			return;
		}

		int[] originLocation = {0, 0};
		this.editText = editText;
		editText.setLocked(true);
		editText.getLocationInWindow(originLocation);
		editText.addTextChangedListener(textWatcher);
		this.filterStart = editText.getSelectionStart();
		int screenHeight = activity.getWindowManager().getDefaultDisplay().getHeight();
		this.popupX = originLocation[0];
		this.popupY = screenHeight - originLocation[1] + ConfigUtils.getNavigationBarHeight(activity);

		this.viewableSpaceHeight = originLocation[1] - ConfigUtils.getStatusBarHeight(context) - ConfigUtils.getActionBarSize(context);
		this.dividersHeight = 2 * context.getResources().getDimensionPixelSize(R.dimen.list_divider_height);

		if (this.popupX > originXOffset) {
			this.setWidth(activity.getWindowManager().getDefaultDisplay().getWidth() - this.popupX + originXOffset);
			this.popupX -= originXOffset;
		} else {
			this.setWidth(activity.getWindowManager().getDefaultDisplay().getWidth());
		}
		this.setHeight(this.viewableSpaceHeight);

		try {
			showAtLocation(editText, Gravity.LEFT | Gravity.BOTTOM, this.popupX, this.popupY);

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

		this.recyclerView.getLayoutParams().height = Math.min(maxHeight, viewableSpaceHeight - dividersHeight);
		this.recyclerView.requestLayout();
	}

	private MentionSelectorAdapter updateList(boolean init) {
		List<ContactModel> groupContacts = contactService.getByIdentities(groupService.getGroupIdentities(groupModel));

		Collections.sort(groupContacts, (model1, model2) -> ContactUtil.getSafeNameString(model1, preferenceService).compareTo(
			ContactUtil.getSafeNameString(model2, preferenceService)
		));

		groupContacts.add(allContactModel);

		if (!init && filterText.length() - filterStart > 0) {
			groupContacts = Functional.filter(groupContacts, (IPredicateNonNull<ContactModel>) contactModel -> ContactUtil.getSafeNameString(contactModel, preferenceService).toLowerCase().contains(filterText.substring(filterStart).toLowerCase()));
		}

		if (groupContacts.size() < 1) {
			dismiss();
			return null;
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
}
