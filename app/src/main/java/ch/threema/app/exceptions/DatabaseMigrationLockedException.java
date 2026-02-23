package ch.threema.app.exceptions;

import ch.threema.base.ThreemaException;

public class DatabaseMigrationLockedException extends ThreemaException {
    public DatabaseMigrationLockedException() {
        super("Database migration locked.");
    }

    public DatabaseMigrationLockedException(String s) {
        super(s);
    }
}
