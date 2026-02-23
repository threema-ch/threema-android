package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.storage.models.DistributionListModel;

public interface DistributionListListener {
    @AnyThread
    void onCreate(DistributionListModel distributionListModel);

    @AnyThread
    void onModify(DistributionListModel distributionListModel);

    @AnyThread
    default void onRemove(DistributionListModel distributionListModel) {}

}
