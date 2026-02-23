package ch.threema.app.exceptions;

public class EntryAlreadyExistsException extends Exception {
    private final int textId;

    public EntryAlreadyExistsException(int textId) {
        super();
        this.textId = textId;
    }

    public int getTextId() {
        return textId;
    }
}
