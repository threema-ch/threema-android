package ch.threema.app.adapters;

import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.COLLAPSED;
import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.EXPANDED;
import static ch.threema.app.adapters.GroupDetailAdapter.GroupDescState.NONE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.List;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.services.ContactService;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.GroupDetailViewModel;
import ch.threema.app.ui.SectionHeaderView;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DisplayableContactOrUser;
import ch.threema.app.utils.DisplayableGroupParticipant;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.data.models.GroupModel;

public class GroupDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public enum GroupDescState {NONE, COLLAPSED, EXPANDED}

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private boolean isGroupEditable = false;

    private final Context context;
    private final ContactService contactService;
    private final GroupModel groupModel;
    private List<DisplayableGroupParticipant> groupMembers;
    private OnGroupDetailsClickListener onClickListener;
    private final GroupDetailViewModel groupDetailViewModel;
    HeaderHolder headerHolder;

    public static class ItemHolder extends RecyclerView.ViewHolder {
        public final View view;
        public final TextView nameView, idView;
        public final AvatarView avatarView;
        public final Chip adminChip;

        public ItemHolder(View view) {
            super(view);
            this.view = view;
            this.nameView = itemView.findViewById(R.id.group_name);
            this.avatarView = itemView.findViewById(R.id.avatar_view);
            this.idView = itemView.findViewById(R.id.threemaid);
            this.adminChip = itemView.findViewById(R.id.admin_chip);
        }
    }

    public class HeaderHolder extends RecyclerView.ViewHolder {
        private final SectionHeaderView groupMembersTitleView;
        private final View addMembersView;
        public final ImageView changeGroupDescButton;
        public final SectionHeaderView groupDescTitle;
        private final TextView expandButton;
        public final TextView groupDescText;
        public final SectionHeaderView groupDescChangedDate;
        public final View groupNoticeView;
        public final TextView groupNoticeTextView;

        public HeaderHolder(View view) {
            super(view);

            // items in object
            this.groupMembersTitleView = itemView.findViewById(R.id.group_members_title);
            this.addMembersView = itemView.findViewById(R.id.add_member);
            this.changeGroupDescButton = itemView.findViewById(R.id.change_group_desc_btn);
            this.groupDescTitle = itemView.findViewById(R.id.group_desc_title);
            this.groupDescText = itemView.findViewById(R.id.group_desc_text);
            this.groupDescText.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                    if (groupDetailViewModel.getGroupDescState() == NONE || !checkIfTextFitsInCollapsedView()) {
                        expandButton.setVisibility(View.VISIBLE);
                    } else {
                        expandButton.setVisibility(View.GONE);
                    }
                }
            });
            this.groupNoticeView = itemView.findViewById(R.id.group_notice_view);
            this.groupNoticeTextView = itemView.findViewById(R.id.group_notice);

            boolean isCreator = groupModel.isCreator();
            boolean isMember = groupModel.isMember();

            if (!isCreator && !isMember) {
                // Show empty group notice
                this.groupNoticeView.setVisibility(View.VISIBLE);
                this.groupNoticeTextView.setText(R.string.group_not_a_member_notice);
            } else if (isCreator && !isMember) {
                // Show notice that this group has been dissolved
                this.groupNoticeView.setVisibility(View.VISIBLE);
                this.groupNoticeTextView.setText(R.string.group_dissolved_notice);
            } else {
                // Don't show any notice
                this.groupNoticeView.setVisibility(View.GONE);
            }

            this.expandButton = itemView.findViewById(R.id.expand_group_desc_text);
            this.groupDescChangedDate = itemView.findViewById(R.id.group_desc_changed_date);
        }


        private boolean checkIfTextFitsInCollapsedView() {
            Layout layout = headerHolder.groupDescText.getLayout();
            if (layout != null) {
                int lines = layout.getLineCount();
                if (lines > 0) {
                    int ellipsisCount = layout.getEllipsisCount(lines - 1);
                    return ellipsisCount == 0 && lines <= 3;
                }
            }
            return true;
        }


    }

    /**
     * Create the adapter to display the group details.
     *
     * @param context              the context
     * @param groupModel           the group model of the group
     * @param groupDetailViewModel the group detail view model
     */
    public GroupDetailAdapter(
        Context context,
        GroupModel groupModel,
        GroupDetailViewModel groupDetailViewModel,
        @NonNull ContactService contactService
    ) {
        this.context = context;
        this.groupModel = groupModel;
        this.groupDetailViewModel = groupDetailViewModel;
        this.contactService = contactService;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setGroupMembers(List<DisplayableGroupParticipant> groupMembers) {
        this.groupMembers = groupMembers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ITEM) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_detail, parent, false);

            return new ItemHolder(v);
        } else if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.header_group_detail, parent, false);
            return new HeaderHolder(v);
        }
        throw new RuntimeException("no matching item type");
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ItemHolder) {
            ItemHolder itemHolder = (ItemHolder) holder;
            final DisplayableGroupParticipant displayableGroupParticipant = getItem(position);
            final DisplayableContactOrUser displayableContactOrUser = displayableGroupParticipant.getDisplayableContactOrUser();
            Bitmap avatar = this.contactService.getAvatar(displayableContactOrUser.getIdentity(), false);

            itemHolder.nameView.setText(displayableContactOrUser.getDisplayName());
            itemHolder.idView.setText(displayableContactOrUser.getIdentity());
            AdapterUtil.styleContact(itemHolder.nameView, displayableContactOrUser.getIdentityState());
            itemHolder.avatarView.setImageBitmap(avatar);
            itemHolder.avatarView.setBadgeVisible(displayableGroupParticipant.getDisplayableContactOrUser().getShowBadge());
            itemHolder.view.setOnClickListener(v -> onClickListener.onGroupMemberClick(v, displayableContactOrUser.getIdentity()));

            boolean isCreator = displayableGroupParticipant instanceof DisplayableGroupParticipant.Creator;
            itemHolder.adminChip.setVisibility(isCreator ? View.VISIBLE : View.GONE);
            itemHolder.idView.setVisibility(isCreator ? View.GONE : View.VISIBLE);
        } else {
            this.headerHolder = (HeaderHolder) holder;
            headerHolder.addMembersView.setOnClickListener(v -> onClickListener.onAddMembersClick(v));

            isGroupEditable = groupModel.isCreator() && groupModel.isMember();

            if (ConfigUtils.supportGroupDescription()) {
                initGroupDescriptionSection();
            } else {
                disableGroupDescription();
            }

            boolean addMembersViewVisibility = isGroupEditable
                && groupMembers != null && groupMembers.size() < BuildConfig.MAX_GROUP_SIZE;

            if (groupMembers != null && !groupMembers.isEmpty()) {
                headerHolder.groupMembersTitleView.setVisibility(View.VISIBLE);
                headerHolder.groupMembersTitleView.setText(ConfigUtils.getSafeQuantityString(context, R.plurals.number_of_group_members, groupMembers.size(), groupMembers.size()));
            } else {
                headerHolder.groupMembersTitleView.setVisibility(View.GONE);
            }

            headerHolder.addMembersView.setVisibility(addMembersViewVisibility ? View.VISIBLE : View.GONE);
        }
    }

    private void initGroupDescriptionSection() {
        updateGroupDescriptionLayout();

        headerHolder.expandButton.setOnClickListener(view -> {
            switch (groupDetailViewModel.getGroupDescState()) {
                case NONE:
                    onClickListener.onGroupDescriptionEditClick();
                    break;
                case EXPANDED:
                    groupDetailViewModel.setGroupDescState(COLLAPSED);
                    showCollapsedGroupDescription();
                    break;
                case COLLAPSED:
                    groupDetailViewModel.setGroupDescState(EXPANDED);
                    showExpandedGroupDescription();
                    break;
            }
        });

        headerHolder.changeGroupDescButton.setOnClickListener(s -> onClickListener.onGroupDescriptionEditClick());
    }

    @Override
    public int getItemCount() {
        if (groupMembers != null)
            return groupMembers.size() + 1;
        else return 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (isPositionHeader(position))
            return TYPE_HEADER;

        return TYPE_ITEM;
    }

    private boolean isPositionHeader(int position) {
        return position == 0;
    }

    public int getPosition(DisplayableGroupParticipant displayableGroupMember) {
        return groupMembers.indexOf(displayableGroupMember) + 1;
    }

    public DisplayableGroupParticipant getItem(int position) {
        return groupMembers.get(position - 1);
    }

    public void setOnClickListener(OnGroupDetailsClickListener listener) {
        onClickListener = listener;
    }

    /**
     * Updates the layout based on the group description data of the view model
     */
    public void updateGroupDescriptionLayout() {
        switch (groupDetailViewModel.getGroupDescState()) {
            case NONE:
                showNoGroupDescription();
                break;
            case COLLAPSED:
                showCollapsedGroupDescription();
                showGroupDescTimestamp();
                break;
            case EXPANDED:
                showExpandedGroupDescription();
                showGroupDescTimestamp();
                break;
        }
    }

    /**
     * Display the group desc timestamp
     */
    private void showGroupDescTimestamp() {
        headerHolder.groupDescChangedDate.setText(context.getString(R.string.changed_group_desc_date)
            + LocaleUtil.formatTimeStampString(context, groupDetailViewModel.getGroupDescTimestamp().getTime(), false));
        headerHolder.groupDescChangedDate.setVisibility(View.VISIBLE);
    }

    /**
     * Shows the collapsed group description
     */
    private void showCollapsedGroupDescription() {
        showGroupDescription();
        headerHolder.groupDescText.setMaxLines(3);
        headerHolder.expandButton.setText(R.string.read_more);
    }

    /**
     * Shows the expanded group description
     */
    private void showExpandedGroupDescription() {
        showGroupDescription();
        headerHolder.expandButton.setText(R.string.read_less);
        headerHolder.groupDescText.setMaxLines(Integer.MAX_VALUE);
    }

    /**
     * Make the group description elements visible and hide
     */
    private void showGroupDescription() {
        headerHolder.groupDescTitle.setVisibility(View.VISIBLE);
        headerHolder.expandButton.setVisibility(View.VISIBLE);
        headerHolder.groupDescText.setVisibility(View.VISIBLE);
        headerHolder.groupDescText.setText(groupDetailViewModel.getGroupDesc());
        LinkifyUtil.getInstance().linkifyText(headerHolder.groupDescText, true);
        if (isGroupEditable) {
            headerHolder.changeGroupDescButton.setVisibility(View.VISIBLE);
        } else {
            headerHolder.changeGroupDescButton.setVisibility(View.GONE);
        }
    }

    /**
     * Hide the group description ui elements and shows a button to add a group description
     */
    private void showNoGroupDescription() {
        groupDetailViewModel.setGroupDescState(NONE);
        headerHolder.groupDescTitle.setVisibility(View.GONE);
        headerHolder.groupDescText.setVisibility(View.GONE);
        headerHolder.groupDescChangedDate.setVisibility(View.GONE);
        headerHolder.changeGroupDescButton.setVisibility(View.GONE);
        headerHolder.expandButton.setText(R.string.add_group_description);
        if (isGroupEditable) {
            headerHolder.expandButton.setVisibility(View.VISIBLE);
        } else {
            headerHolder.expandButton.setVisibility(View.GONE);
        }
    }

    /**
     * Hides all the group description related ui elements
     */
    private void disableGroupDescription() {
        headerHolder.groupDescTitle.setVisibility(View.GONE);
        headerHolder.groupDescText.setVisibility(View.GONE);
        headerHolder.groupDescChangedDate.setVisibility(View.GONE);
        headerHolder.changeGroupDescButton.setVisibility(View.GONE);
        headerHolder.expandButton.setVisibility(View.GONE);
    }

    public interface OnGroupDetailsClickListener {
        void onGroupMemberClick(View v, @NonNull String identity);

        void onGroupDescriptionEditClick();

        void onAddMembersClick(View v);
    }
}
