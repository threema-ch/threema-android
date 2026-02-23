package ch.threema.app.exceptions;

public class InvalidEntryException extends Exception {
    private final int textId;

    public InvalidEntryException(int textId) {
        super();
        this.textId = textId;
    }

    public int getTextId() {
        return textId;
    }
}
