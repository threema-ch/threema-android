package ch.threema.app.adapters;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.chip.Chip;

import java.util.HashMap;
import java.util.List;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.app.availabilitystatus.AvailabilityStatusExtensionsKt;
import ch.threema.app.availabilitystatus.AvailabilityStatusIconElevatedView;
import ch.threema.app.services.ContactService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.InitialAvatarView;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.data.datatypes.AvailabilityStatus;
import ch.threema.data.datatypes.ContactNameFormat;
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory;
import ch.threema.domain.protocol.api.work.WorkDirectoryContact;
import ch.threema.domain.protocol.api.work.WorkOrganization;

public class DirectoryAdapter extends PagedListAdapter<WorkDirectoryContact, RecyclerView.ViewHolder> {
    @NonNull
    private final Context context;
    private final LayoutInflater inflater;
    @NonNull
    private final PreferenceService preferenceService;
    @NonNull
    private final ContactService contactService;
    @NonNull
    private final UserService userService;
    private final WorkOrganization workOrganization;
    private final HashMap<String, String> categoryMap = new HashMap<>();
    private DirectoryAdapter.OnClickItemListener onClickItemListener;
    @DrawableRes
    private final int backgroundRes;

    private static class DirectoryHolder extends RecyclerView.ViewHolder {
        private final TextView nameView;
        private final TextView identityView;
        private final ImageView statusImageView;
        private final InitialAvatarView avatarView;
        private final AvailabilityStatusIconElevatedView availabilityStatusIconElevatedView;
        private final TextView categoriesView;
        private final Chip organizationView;
        protected WorkDirectoryContact contact;

        private DirectoryHolder(final View itemView) {
            super(itemView);

            this.nameView = itemView.findViewById(R.id.name);
            this.identityView = itemView.findViewById(R.id.identity);
            this.statusImageView = itemView.findViewById(R.id.status);
            this.avatarView = itemView.findViewById(R.id.avatar_view);
            this.availabilityStatusIconElevatedView = itemView.findViewById(R.id.availability_status_avatar_icon);
            this.categoriesView = itemView.findViewById(R.id.categories);
            this.organizationView = itemView.findViewById(R.id.organization);
        }

        public View getItem() {
            return itemView;
        }
    }

    public DirectoryAdapter(
        @NonNull Context context,
        @NonNull PreferenceService preferenceService,
        @NonNull ContactService contactService,
        @NonNull UserService userService,
        @NonNull List<WorkDirectoryCategory> categoryList
    ) {
        super(DIFF_CALLBACK);

        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.preferenceService = preferenceService;
        this.contactService = contactService;
        this.userService = userService;
        this.workOrganization = preferenceService.getWorkOrganization();

        for (WorkDirectoryCategory category : categoryList) {
            this.categoryMap.put(category.id, category.name);
        }

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        this.backgroundRes = outValue.resourceId;
    }

    public interface OnClickItemListener {
        void onClick(WorkDirectoryContact workDirectoryContact, int position);

        void onAdd(@NonNull WorkDirectoryContact workDirectoryContact, int position);
    }

    public void setOnClickItemListener(DirectoryAdapter.OnClickItemListener onClickItemListener) {
        this.onClickItemListener = onClickItemListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View itemView = inflater.inflate(R.layout.item_directory, viewGroup, false);
        itemView.setBackgroundResource(R.drawable.listitem_background_selector);
        return new DirectoryHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {

        final @NonNull DirectoryHolder directoryHolder = (DirectoryHolder) viewHolder;
        final @Nullable WorkDirectoryContact workDirectoryContact = this.getItem(position);
        if (workDirectoryContact == null) {
            return;
        }

        directoryHolder.contact = workDirectoryContact;

        final boolean isMe = directoryHolder.contact.threemaId.equals(userService.getIdentity());

        if (this.onClickItemListener != null && !isMe) {
            directoryHolder.statusImageView.setOnClickListener(
                v -> onClickItemListener.onAdd(directoryHolder.contact, viewHolder.getBindingAdapterPosition())
            );
            directoryHolder.itemView.setOnClickListener(
                v -> onClickItemListener.onClick(directoryHolder.contact, viewHolder.getBindingAdapterPosition())
            );
        }

        @NonNull String name;
        if (preferenceService.getContactNameFormat() == ContactNameFormat.FIRSTNAME_LASTNAME) {
            name = (workDirectoryContact.firstName != null ? workDirectoryContact.firstName + " " : "") +
                (workDirectoryContact.lastName != null ? workDirectoryContact.lastName : "");
        } else {
            name = (workDirectoryContact.lastName != null ? workDirectoryContact.lastName + " " : "") +
                (workDirectoryContact.firstName != null ? workDirectoryContact.firstName : "");
        }
        if (!TestUtil.isEmptyOrNull(workDirectoryContact.csi)) {
            name += " " + workDirectoryContact.csi;
        }

        directoryHolder.nameView.setText(name);

        StringBuilder categoriesBuilder = new StringBuilder();
        if (!workDirectoryContact.categoryIds.isEmpty()) {
            int count = 0;
            for (String categoryId : workDirectoryContact.categoryIds) {
                if (count != 0) {
                    categoriesBuilder.append(", ");
                }
                categoriesBuilder.append(categoryMap.get(categoryId));
                count++;
            }
        }
        directoryHolder.categoriesView.setText(categoriesBuilder.toString());

        directoryHolder.avatarView.setInitials(workDirectoryContact.firstName, workDirectoryContact.lastName);

        bindAvailabilityStatusIcon(directoryHolder, workDirectoryContact);

        directoryHolder.identityView.setText(workDirectoryContact.threemaId);

        if (workDirectoryContact.organization.getName() != null && !workDirectoryContact.organization.getName().equals(workOrganization.getName())) {
            directoryHolder.organizationView.setText(workDirectoryContact.organization.getName());
            directoryHolder.organizationView.setVisibility(View.VISIBLE);
        } else {
            directoryHolder.organizationView.setVisibility(View.GONE);
        }

        final boolean isAddedContact = contactService.getByIdentity(workDirectoryContact.threemaId) != null;

        directoryHolder.statusImageView.setBackgroundResource(isAddedContact ? 0 : this.backgroundRes);
        directoryHolder.statusImageView.setImageResource(
            isMe
                ? R.drawable.ic_person_outline
                : (isAddedContact ? R.drawable.ic_keyboard_arrow_right_black_24dp : R.drawable.ic_add_circle_outline_black_24dp)
        );
        directoryHolder.statusImageView.setContentDescription(
            context.getString(
                isMe
                    ? R.string.me_myself_and_i
                    : (isAddedContact ? R.string.title_compose_message : R.string.menu_add_contact)
            )
        );
        directoryHolder.statusImageView.setClickable(!isAddedContact && !isMe);
        directoryHolder.statusImageView.setFocusable(!isAddedContact && !isMe);
    }

    private void bindAvailabilityStatusIcon(@NonNull DirectoryHolder holder, @NonNull WorkDirectoryContact workDirectoryContact) {
        if (!ConfigUtils.supportsAvailabilityStatus()) {
            return;
        }

        /*
         * The work directory will miss the "availability" JSON response key entirely, if the status was set to "None" by the work user. This is why
         * in this case we need to map "null" to "None".
         */
        final @Nullable AvailabilityStatus availabilityStatus = (workDirectoryContact.availability != null)
            ? AvailabilityStatus.fromProtocolBase64(workDirectoryContact.availability)
            : AvailabilityStatus.None.INSTANCE;

        holder.availabilityStatusIconElevatedView.setVisibility(
            availabilityStatus instanceof AvailabilityStatus.Set
                ? View.VISIBLE
                : View.GONE
        );
        holder.availabilityStatusIconElevatedView.setStatus(
            availabilityStatus instanceof AvailabilityStatus.Set
                ? (AvailabilityStatus.Set) availabilityStatus
                : null
        );
        holder.availabilityStatusIconElevatedView.setContentDescription(
            availabilityStatus instanceof AvailabilityStatus.Set
                ? context.getString(AvailabilityStatusExtensionsKt.displayNameRes(availabilityStatus))
                : null
        );
    }

    private static final DiffUtil.ItemCallback<WorkDirectoryContact> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull WorkDirectoryContact oldItem, @NonNull WorkDirectoryContact newItem) {
                return oldItem.threemaId.equals(newItem.threemaId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull WorkDirectoryContact oldItem, @NonNull WorkDirectoryContact newItem) {
                return oldItem.threemaId.equals(newItem.threemaId);
            }
        };
}
