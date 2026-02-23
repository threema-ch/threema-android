package ch.threema.app.exceptions;

import ch.threema.base.ThreemaException;

public class DatabaseMigrationFailedException extends ThreemaException {
    public DatabaseMigrationFailedException() {
        super("Database migration failed.");
    }

    public DatabaseMigrationFailedException(String s) {
        super(s);
    }
}
