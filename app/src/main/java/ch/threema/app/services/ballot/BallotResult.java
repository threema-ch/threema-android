package ch.threema.app.services.ballot;


import java.util.ArrayList;
import java.util.List;

abstract class BallotResult {
    private List<Integer> messages = new ArrayList<Integer>();
    private boolean success = false;

    public boolean isSuccess() {
        return this.success;
    }

    public List<Integer> getMessageResources() {
        return this.messages;
    }

    protected BallotResult error(int messageResourceId) {
        this.success = false;
        this.messages.add(messageResourceId);
        return this;
    }

    public BallotResult success() {
        this.success = true;
        return this;
    }

}
