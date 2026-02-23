package ch.threema.storage.models.access;

public class Access {
    private boolean allowed = true;
    private int notAllowedTestResourceId = 0;

    public Access() {
    }

    public Access(boolean allowed, int notAllowedTestResourceId) {
        this();
        this.allowed = allowed;
        this.notAllowedTestResourceId = notAllowedTestResourceId;
    }

    public boolean isAllowed() {
        return this.allowed;
    }

    public int getNotAllowedTestResourceId() {
        return this.notAllowedTestResourceId;
    }
}
