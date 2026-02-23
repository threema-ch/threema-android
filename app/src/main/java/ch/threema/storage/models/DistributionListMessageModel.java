package ch.threema.storage.models;

import androidx.annotation.NonNull;

public class DistributionListMessageModel extends AbstractMessageModel {
    public static final String TABLE = "distribution_list_message";
    public static final String COLUMN_DISTRIBUTION_LIST_ID = "distributionListId";

    private long distributionListId;

    public DistributionListMessageModel() {
        super();
    }

    public DistributionListMessageModel(boolean isStatusMessage) {
        super(isStatusMessage);
    }

    public long getDistributionListId() {
        return this.distributionListId;
    }

    public DistributionListMessageModel setDistributionListId(long distributionListId) {
        this.distributionListId = distributionListId;
        return this;
    }

    @Override
    public String toString() {
        return "distribution_list_message.id = " + this.getId();
    }

    /**
     * TODO(ANDR-XXXX): evil code!
     */
    public void copyFrom(@NonNull AbstractMessageModel sourceModel) {
        //copy all objects
        setType(sourceModel.getType());
        setDataObject(sourceModel.getDataObject());
        setCorrelationId(sourceModel.getCorrelationId());
        setSaved(sourceModel.isSaved());
        setState(sourceModel.getState());
        setModifiedAt(sourceModel.getModifiedAt());
        setDeliveredAt(sourceModel.getDeliveredAt());
        setReadAt(sourceModel.getReadAt());
        setEditedAt(sourceModel.getEditedAt());
        setDeletedAt(sourceModel.getDeletedAt());
        setBody(sourceModel.getBody());
        setCaption(sourceModel.getCaption());
        setQuotedMessageId(sourceModel.getQuotedMessageId());
        setForwardSecurityMode(sourceModel.getForwardSecurityMode());
        setDisplayTags(sourceModel.getDisplayTags());
        setApiMessageId(sourceModel.getApiMessageId());
    }
}
