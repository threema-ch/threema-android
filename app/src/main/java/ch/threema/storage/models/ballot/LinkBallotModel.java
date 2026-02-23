package ch.threema.storage.models.ballot;

public interface LinkBallotModel {
    public enum Type {
        CONTACT, GROUP
    }

    int getBallotId();

    Type getType();
}
