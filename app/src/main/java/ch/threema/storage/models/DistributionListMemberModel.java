package ch.threema.storage.models;

public class DistributionListMemberModel {

    public static final String TABLE = "distribution_list_member";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_IDENTITY = "identity";
    public static final String COLUMN_DISTRIBUTION_LIST_ID = "distributionListId";
    public static final String COLUMN_IS_ACTIVE = "isActive";

    private int id;
    private String identity;
    private long distributionListId;
    private boolean isActive = true;

    public long getDistributionListId() {
        return distributionListId;
    }

    public DistributionListMemberModel setDistributionListId(long distributionListId) {
        this.distributionListId = distributionListId;
        return this;
    }

    public String getIdentity() {
        return identity;
    }

    public DistributionListMemberModel setIdentity(String identity) {
        this.identity = identity;
        return this;
    }

    public int getId() {
        return id;
    }

    public DistributionListMemberModel setId(int id) {
        this.id = id;
        return this;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public DistributionListMemberModel setActive(boolean active) {
        this.isActive = active;
        return this;
    }
}
