package ch.threema.app.backuprestore;

import java.io.Serializable;

public class BackupRestoreDataConfig implements Serializable {
    private final String password;
    private boolean backupIdentity = true;
    private boolean backupContactAndMessages = true;
    private boolean backupMedia = true;
    private boolean backupAvatars = true;
    private boolean backupThumbnails = false;
    private boolean backupNonces = true;
    private boolean backupReactions = true;

    public BackupRestoreDataConfig(String password) {
        this.password = password;
    }

    public String getPassword() {
        return this.password;
    }

    public boolean backupIdentity() {
        return backupIdentity;
    }

    public BackupRestoreDataConfig setBackupIdentity(boolean backupIdentity) {
        this.backupIdentity = backupIdentity;
        return this;
    }

    public boolean backupContactAndMessages() {
        return this.backupContactAndMessages;
    }

    public boolean backupGroupsAndMessages() {
        return this.backupContactAndMessages();
    }

    public boolean backupDistributionLists() {
        return this.backupContactAndMessages();
    }

    public boolean backupBallots() {
        return this.backupContactAndMessages();
    }

    public BackupRestoreDataConfig setBackupContactAndMessages(boolean backupContactAndMessages) {
        this.backupContactAndMessages = backupContactAndMessages;
        return this;
    }

    public boolean backupMedia() {
        return this.backupMedia;
    }

    public boolean backupThumbnails() {
        return this.backupThumbnails;
    }

    public boolean backupAvatars() {
        return this.backupAvatars;
    }

    public boolean backupNonces() {
        return this.backupNonces;
    }

    public boolean backupReactions() {
        return this.backupReactions;
    }

    public BackupRestoreDataConfig setBackupMedia(boolean backupMedia) {
        this.backupMedia = backupMedia;
        return this;
    }

    public BackupRestoreDataConfig setBackupThumbnails(boolean backupThumbnails) {
        this.backupThumbnails = backupThumbnails;
        return this;
    }

    public BackupRestoreDataConfig setBackupAvatars(boolean backupAvatars) {
        this.backupAvatars = backupAvatars;
        return this;
    }

    public BackupRestoreDataConfig setBackupNonces(boolean backupNonces) {
        this.backupNonces = backupNonces;
        return this;
    }

    public BackupRestoreDataConfig setBackupReactions(boolean backupReactions) {
        this.backupReactions = backupReactions;
        return this;
    }
}
