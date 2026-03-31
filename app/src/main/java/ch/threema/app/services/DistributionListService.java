package ch.threema.app.services;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.base.SessionScoped;
import ch.threema.base.ThreemaException;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListModel;

@SessionScoped
public interface DistributionListService extends AvatarService<Long> {
    interface DistributionListFilter {
        boolean sortingByDate();

        boolean sortingAscending();

        boolean showHidden();
    }

    @Nullable
    DistributionListModel getById(long id);

    @NonNull
    List<DistributionListModel> getByIds(@NonNull List<Long> ids);

    DistributionListModel createDistributionList(@Nullable String name, String[] memberIdentities) throws ThreemaException;

    DistributionListModel createDistributionList(@Nullable String name, String[] memberIdentities, boolean isHidden) throws ThreemaException;

    DistributionListModel updateDistributionList(DistributionListModel distributionListModel, String name, String[] memberIdentities) throws ThreemaException;

    boolean addMemberToDistributionList(DistributionListModel distributionListModel, String identity);

    boolean remove(DistributionListModel distributionListModel);

    boolean removeAll();

    String[] getDistributionListIdentities(DistributionListModel distributionListModel);

    List<DistributionListMemberModel> getDistributionListMembers(DistributionListModel distributionListModel);

    List<DistributionListModel> getAll();

    List<DistributionListModel> getAll(DistributionListFilter filter);

    List<ContactModel> getMembers(DistributionListModel distributionListModel);

    String getMembersString(DistributionListModel distributionListModel);

    @Nullable
    DistributionListMessageReceiver createReceiver(long distributionListId);

    @NonNull
    DistributionListMessageReceiver createReceiver(@NonNull DistributionListModel distributionListModel);

    String getUniqueIdString(DistributionListModel distributionListModel);

    void setIsArchived(DistributionListModel distributionListModel, boolean archived);

    /**
     * Set the `lastUpdate` field of the specified distribution list to the current date.
     * <p>
     * Save the model and notify listeners.
     */
    void bumpLastUpdate(@NonNull DistributionListModel distributionListModel);
}
