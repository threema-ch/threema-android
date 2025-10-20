/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.ListFragment;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.RecipientListBaseActivity;
import ch.threema.app.adapters.FilterResultsListener;
import ch.threema.app.adapters.FilterableListAdapter;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationCategoryService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DistributionListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.ui.CheckableConstraintLayout;
import ch.threema.app.ui.CheckableRelativeLayout;
import ch.threema.app.ui.DebouncedOnClickListener;
import ch.threema.app.ui.EmptyView;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.SnackbarUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;

public abstract class RecipientListFragment extends ListFragment implements ListView.OnItemLongClickListener, FilterResultsListener {
    public static final String ARGUMENT_MULTI_SELECT = "ms";
    public static final String ARGUMENT_MULTI_SELECT_FOR_COMPOSE = "msi";

    private static final Logger logger = LoggingUtil.getThreemaLogger("RecipientListFragment");

    protected ContactService contactService;
    protected GroupService groupService;
    protected DistributionListService distributionListService;
    protected ConversationService conversationService;
    protected PreferenceService preferenceService;
    protected BlockedIdentitiesService blockedIdentitiesService;
    @Nullable
    protected ConversationCategoryService conversationCategoryService;
    protected FragmentActivity activity;
    protected Parcelable listInstanceState;
    protected ExtendedFloatingActionButton floatingActionButton;
    protected Snackbar snackbar;
    protected CircularProgressIndicator progressBar;
    protected View topLayout;
    protected boolean multiSelect = true, multiSelectIdentity = false;
    protected FilterableListAdapter adapter;

    private boolean isVisible = false;
    private static long selectionTime = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = getActivity();

        final ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        try {
            contactService = serviceManager.getContactService();
            groupService = serviceManager.getGroupService();
            distributionListService = serviceManager.getDistributionListService();
            blockedIdentitiesService = serviceManager.getBlockedIdentitiesService();
            conversationService = serviceManager.getConversationService();
            preferenceService = serviceManager.getPreferenceService();
            conversationCategoryService = serviceManager.getConversationCategoryService();
        } catch (ThreemaException e) {
            LogUtil.exception(e, activity);
            return null;
        }

        Bundle bundle = getArguments();
        if (bundle != null) {
            multiSelect = bundle.getBoolean(ARGUMENT_MULTI_SELECT, true);
            multiSelectIdentity = bundle.getBoolean(ARGUMENT_MULTI_SELECT_FOR_COMPOSE, false);
        }

        topLayout = inflater.inflate(R.layout.fragment_list, container, false);
        return topLayout;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ArrayList<Integer> checkedItemPositions = null;

        // recover after rotation
        if (savedInstanceState != null) {
            this.listInstanceState = savedInstanceState.getParcelable(getBundleName());
            checkedItemPositions = savedInstanceState.getIntegerArrayList(getBundleName() + "c");
        }

        createListAdapter(checkedItemPositions);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            view,
            InsetSides.horizontal()
        );

        getListView().setDividerHeight(0);
        getListView().setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        if (!multiSelect && getAddText() != 0) {
            View headerView = View.inflate(activity, R.layout.header_recipient_list, null);
            ((ImageView) headerView.findViewById(R.id.avatar_view)).setImageResource(getAddIcon());
            ((TextView) headerView.findViewById(R.id.text_view)).setText(getAddText());
            headerView.findViewById(R.id.container).setOnClickListener(v -> {
                Intent intent = getAddIntent();
                if (intent != null) {
                    startActivity(intent);
                }
            });
            getListView().addHeaderView(headerView);
        }

        progressBar = view.findViewById(R.id.progress);

        floatingActionButton = view.findViewById(R.id.floating);

        if (isMultiSelectAllowed()) {
            getListView().setOnItemLongClickListener(this);
            floatingActionButton.setOnClickListener(new DebouncedOnClickListener(500) {
                @Override
                public void onDebouncedClick(View v) {
                    onFloatingActionButtonClick();
                }
            });

            floatingActionButton.setIconResource(R.drawable.ic_keyboard_arrow_right);
            floatingActionButton.setText(R.string.label_continue);
        } else {
            floatingActionButton.hide();
        }
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        if (getListView().getChoiceMode() != AbsListView.CHOICE_MODE_MULTIPLE) {
            if (System.currentTimeMillis() - selectionTime > 500) {
                selectionTime = System.currentTimeMillis();
                getListView().setChoiceMode(AbsListView.CHOICE_MODE_NONE);
                onItemClick(v);
            }
        } else {
            if (adapter.getCheckedItemCount() <= 0) {
                stopMultiSelect();
            } else {
                updateMultiSelect();
            }
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
        startMultiSelect();
        getListView().setItemChecked(position, true);
        if (v instanceof CheckableConstraintLayout) {
            ((CheckableConstraintLayout) v).setChecked(true);
        } else {
            ((CheckableRelativeLayout) v).setChecked(true);
        }
        updateMultiSelect();

        return true;
    }

    private void startMultiSelect() {
        getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        if (isVisible) {
            snackbar = SnackbarUtil.make(topLayout, "", Snackbar.LENGTH_INDEFINITE, 4);
            // snackbar.setBackgroundTint(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorPrimary));
            // snackbar.setTextColor(ConfigUtils.getColorFromAttribute(getContext(), R.attr.colorOnSecondary));
            snackbar.getView().getLayoutParams().width = AppBarLayout.LayoutParams.MATCH_PARENT;
            snackbar.show();
            snackbar.getView().post(new Runnable() {
                @Override
                public void run() {
                    floatingActionButton.show();
                }
            });
        }
    }

    private void updateMultiSelect() {
        if (getListView().getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE) {
            if (getAdapter().getCheckedItemCount() > 0) {
                if (snackbar != null) {
                    snackbar.setText(getString(
                        multiSelectIdentity ?
                            R.string.threema_message_to :
                            R.string.really_send,
                        getRecipientList()));
                }
            }
        }
    }

    private void stopMultiSelect() {
        getListView().setChoiceMode(AbsListView.CHOICE_MODE_NONE);
        if (snackbar != null && snackbar.isShown()) {
            snackbar.dismiss();
        }
        floatingActionButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                floatingActionButton.hide();
            }
        }, 100);
    }

    private void onItemClick(View v) {
        final Object object = adapter.getClickedItem(v);
        if (object != null) {
            ((RecipientListBaseActivity) activity).prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(object)));
        }
    }

    private void onFloatingActionButtonClick() {
        logger.info("FAB clicked");
        final HashSet<?> objects = adapter.getCheckedItems();
        if (!objects.isEmpty()) {
            if (multiSelectIdentity) {
                ContactModel contactModel = null;
                List<String> identities = new ArrayList<>();
                for (Object object : objects) {
                    if (object instanceof ContactModel) {
                        contactModel = (ContactModel) object;
                        identities.add(contactModel.getIdentity());
                    }
                }

                if (identities.size() > 1) {
                    try {
                        DistributionListModel distributionListModel = distributionListService.createDistributionList(
                            null,
                            identities.toArray(new String[0]),
                            true);

                        ((RecipientListBaseActivity) activity).prepareForwardingOrSharing(new ArrayList<>(Collections.singleton(distributionListModel)));

                        return;
                    } catch (ThreemaException e) {
                        logger.error("Unable to create distribution list", e);
                    }
                } else if (identities.size() == 1) {
                    ((RecipientListBaseActivity) activity).prepareForwardingOrSharing(new ArrayList<>(Collections.singletonList(contactModel)));
                    return;
                }
                Toast.makeText(requireContext(), R.string.contact_not_found, Toast.LENGTH_LONG).show();
            } else {
                ((RecipientListBaseActivity) activity).prepareForwardingOrSharing(new ArrayList<>(objects));
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        try {
            ListView listView = getListView();

            if (listView != null) {
                outState.putParcelable(getBundleName(), listView.onSaveInstanceState());
                // save checked items, if any
                if (listView.getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE && getAdapter().getCheckedItemCount() > 0) {
                    outState.putIntegerArrayList(getBundleName() + "c", getAdapter().getCheckedItemPositions());
                }
            }
        } catch (Exception e) {
            // getListView may cause IllegalStateException
        }

        super.onSaveInstanceState(outState);
    }

    protected void restoreCheckedItems(ArrayList<Integer> checkedItemPositions) {
        // restore previously checked items
        if (checkedItemPositions != null && !checkedItemPositions.isEmpty()) {
            startMultiSelect();
            updateMultiSelect();
        }
    }

    private String getRecipientList() {
        StringBuilder builder = new StringBuilder();

        for (Object model : adapter.getCheckedItems()) {
            String name = null;
            if (model instanceof ContactModel) {
                name = NameUtil.getDisplayNameOrNickname((ContactModel) model, true);
            } else if (model instanceof GroupModel) {
                name = NameUtil.getDisplayName((GroupModel) model, this.groupService);
            } else if (model instanceof DistributionListModel) {
                name = NameUtil.getDisplayName((DistributionListModel) model, this.distributionListService);
            }
            if (name != null) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(name);
            }
        }
        return builder.toString();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        isVisible = isVisibleToUser;

        if (isVisibleToUser) {
            if (isMultiSelectAllowed() && getView() != null) {
                if (getListView().getChoiceMode() == AbsListView.CHOICE_MODE_MULTIPLE) {
                    if (snackbar == null || !snackbar.isShownOrQueued()) {
                        startMultiSelect();
                        updateMultiSelect();
                    }
                }
            }
        }
    }

    public FilterableListAdapter getAdapter() {
        return adapter;
    }

    void setListAdapter(FilterableListAdapter adapter) {
        super.setListAdapter(adapter);

        if (isAdded()) {
            try {
                progressBar.setVisibility(View.GONE);

                // add text view if contact list is empty
                EmptyView emptyView = new EmptyView(activity);
                emptyView.setup(getEmptyText());
                ((ViewGroup) getListView().getParent()).addView(emptyView);
                getListView().setEmptyView(emptyView);
            } catch (IllegalStateException ignored) {
            }

            showTooltipIfNeeded();
        }
    }

    private void showTooltipIfNeeded() {
        if (
            !isMultiSelectAllowed() ||
                !multiSelectIdentity ||
                floatingActionButton == null ||
                preferenceService.getMultipleRecipientsTooltipCount() >= 1
        ) {
            return;
        }

        getListView().post(() -> {
            try {
                var listView = getListView();
                if (listView.getCount() < 3) {
                    // We want at least 3 items, the first one being the "New contact" button,
                    // the second one the one we center the tooltip on, and we want a third one as the tooltip message only makes
                    // sense if there actually are multiple recipients that can be selected.
                    return;
                }

                preferenceService.incrementMultipleRecipientsTooltipCount();

                int[] location = new int[2];
                listView.getLocationOnScreen(location);

                int itemHeight = getResources().getDimensionPixelSize(R.dimen.messagelist_item_height);

                Rect rect = new Rect(
                    location[0] + 200,
                    location[1] + itemHeight,
                    location[0] + 200 + itemHeight,
                    location[1] + (itemHeight * 2)
                );

                final @ColorInt int textColor = ConfigUtils.getColorFromAttribute(requireContext(), R.attr.colorOnPrimary);

                TapTargetView.showFor(
                    requireActivity(),
                    TapTarget.forBounds(rect, getString(R.string.tooltip_multiple_recipients_title), getString(R.string.tooltip_multiple_recipients_text))
                        .outerCircleColorInt(ConfigUtils.getColorFromAttribute(requireContext(), R.attr.colorPrimary))
                        .outerCircleAlpha(0.96f)
                        .targetCircleColor(android.R.color.transparent)
                        .titleTextSize(24)
                        .titleTextColorInt(textColor)
                        .descriptionTextSize(18)
                        .descriptionTextColorInt(textColor)
                        .textColorInt(textColor)
                        .textTypeface(Typeface.SANS_SERIF)
                        .dimColor(android.R.color.black)
                        .drawShadow(true)
                        .cancelable(true)
                        .tintTarget(true)
                        .transparentTarget(true)
                        .targetRadius(50),
                    null
                );
            } catch (Exception ignore) {
                // catch null typeface exception on CROSSCALL Action-X3
            }
        });
    }

    @Override
    public void onResultsAvailable(int count) {
        if (isAdded() && activity != null) {
            ((RecipientListBaseActivity) activity).onQueryResultChanged(this, count);
        }
    }

    protected abstract void createListAdapter(@Nullable ArrayList<Integer> checkedItems);

    protected abstract String getBundleName();

    protected abstract @StringRes int getEmptyText();

    protected abstract @DrawableRes int getAddIcon();

    protected abstract @StringRes int getAddText();

    protected abstract Intent getAddIntent();

    protected abstract boolean isMultiSelectAllowed();
}
