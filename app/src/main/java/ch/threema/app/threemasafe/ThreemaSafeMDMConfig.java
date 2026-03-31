package ch.threema.app.threemasafe;

import org.koin.java.KoinJavaComponent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.restrictions.AppRestrictions;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.Base32;

public class ThreemaSafeMDMConfig {
    private static final int BACKUP_DISABLE = 0; // disabled threema safe backup
    private static final int BACKUP_ENABLE = 1; // enable backup (default)
    private static final int BACKUP_FORCE = 1 << 1; // force use of threema safe backup

    private static final int RESTORE_DISABLE = 0; // disable threema safe restore
    private static final int RESTORE_ENABLE = 1; // enable restore (default)
    private static final int RESTORE_FORCE = 1 << 1; // force automatic restore of safe backup

    // modifiers
    private static final int SERVER_PRESET = 1 << 2;
    private static final int PASSWORD_PRESET = 1 << 3;

    // backup options
    private static final int BACKUP_ENABLE_SERVER_PRESET = BACKUP_ENABLE | SERVER_PRESET; // use of Threema Safe is optional. using custom server
    private static final int BACKUP_FORCE_SERVER_PRESET = BACKUP_FORCE | SERVER_PRESET; // use of Threema Safe is enforced. using custom server
    // headless restore options (no UI shown)
    private static final int BACKUP_FORCE_PASSWORD_PRESET = BACKUP_FORCE | PASSWORD_PRESET; // enforce safe backups to default server. password set by administrator
    private static final int BACKUP_FORCE_PASSWORD_SERVER_PRESET = BACKUP_FORCE | PASSWORD_PRESET | SERVER_PRESET; // enforce safe backups to custom server. password set by administrator

    // restore options which require password entry
    private static final int RESTORE_ENABLE_SERVER_PRESET = RESTORE_ENABLE | SERVER_PRESET; // enable restore of arbitrary ID from predefined server
    private static final int RESTORE_FORCE_SERVER_PRESET = RESTORE_FORCE | SERVER_PRESET; // force automatic restore of given ID from predefined server
    // headless restore options (no UI shown)
    private static final int RESTORE_FORCE_PASSWORD_PRESET = RESTORE_FORCE | PASSWORD_PRESET; // force automatic restore
    private static final int RESTORE_FORCE_PASSWORD_SERVER_PRESET = RESTORE_FORCE | PASSWORD_PRESET | SERVER_PRESET; // force automatic restore from given server

    // defaults
    private int backupStatus = BACKUP_ENABLE; // safe enabled by default
    private int restoreStatus = RESTORE_ENABLE; // restore enabled by default

    private String identity = null;
    private String password = null;
    private String serverName = null;
    private String serverUsername = null;
    private String serverPassword = null;

    private static ThreemaSafeMDMConfig sInstance = null;

    public static synchronized ThreemaSafeMDMConfig getInstance() {
        if (sInstance == null) {
            sInstance = new ThreemaSafeMDMConfig();
        }
        return sInstance;
    }

    private ThreemaSafeMDMConfig() {
        if (ConfigUtils.isWorkRestricted()) {
            AppRestrictions appRestrictions = KoinJavaComponent.get(AppRestrictions.class);

            String stringPreset = appRestrictions.getSafePassword();
            if (stringPreset != null &&
                stringPreset.length() >= ThreemaSafeServiceImpl.MIN_PW_LENGTH &&
                stringPreset.length() <= ThreemaSafeServiceImpl.MAX_PW_LENGTH) {
                this.password = stringPreset;
            }

            stringPreset = appRestrictions.getSafeServerUrl();
            if (stringPreset != null) {
                this.serverName = stringPreset;
            }

            stringPreset = appRestrictions.getSafeServerUsername();
            if (stringPreset != null) {
                this.serverUsername = stringPreset;
            }

            stringPreset = appRestrictions.getSafeServerPassword();
            if (stringPreset != null) {
                this.serverPassword = stringPreset;
            }

            Boolean booleanPreset;
            if (appRestrictions.isBackupsDisabled()) {
                this.backupStatus = BACKUP_DISABLE;
            } else {
                booleanPreset = appRestrictions.isSafeEnabledOrNull();
                if (booleanPreset == null) {
                    this.backupStatus = BACKUP_ENABLE;
                    if (!TestUtil.isEmptyOrNull(serverName)) {
                        this.backupStatus |= SERVER_PRESET;
                    }
                } else if (!booleanPreset) {
                    this.backupStatus = BACKUP_DISABLE;
                } else { // true
                    this.backupStatus = BACKUP_FORCE;
                    if (!TestUtil.isEmptyOrNull(this.password)) {
                        this.backupStatus |= PASSWORD_PRESET;
                    }
                    if (!TestUtil.isEmptyOrNull(serverName)) {
                        this.backupStatus |= SERVER_PRESET;
                    }
                }
            }

            if (appRestrictions.isSafeRestoreEnabled()) {
                this.identity = appRestrictions.getSafeRestoreId();
                if (TestUtil.isEmptyOrNull(this.identity)) {
                    this.restoreStatus = RESTORE_ENABLE;
                    if (!TestUtil.isEmptyOrNull(serverName)) {
                        this.restoreStatus |= SERVER_PRESET;
                    }
                } else {
                    this.restoreStatus = RESTORE_FORCE;
                    if (!TestUtil.isEmptyOrNull(password)) {
                        this.restoreStatus |= PASSWORD_PRESET;
                    }
                    if (!TestUtil.isEmptyOrNull(serverName)) {
                        this.restoreStatus |= SERVER_PRESET;
                    }
                }
            } else { // false
                // disabled
                this.restoreStatus = RESTORE_DISABLE;
            }
        }
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return identity to restore
     */
    public String getIdentity() {
        return this.identity;
    }

    public String getPassword() {
        return this.password;
    }

    /*****/

    public ThreemaSafeServerInfo getServerInfo() {
        return new ThreemaSafeServerInfo(this.serverName, this.serverUsername, this.serverPassword);
    }

    public boolean isRestoreExpertSettingsDisabled() {
        return (this.restoreStatus & RESTORE_FORCE) == RESTORE_FORCE ||
            (this.restoreStatus == RESTORE_ENABLE_SERVER_PRESET);
    }

    public boolean isBackupExpertSettingsDisabled() {
        return (this.backupStatus & BACKUP_FORCE) == BACKUP_FORCE ||
            (this.backupStatus == BACKUP_ENABLE_SERVER_PRESET);
    }

    public boolean isRestoreForced() {
        return (this.restoreStatus & RESTORE_FORCE) == RESTORE_FORCE;
    }

    public boolean isSkipRestorePasswordEntryDialog() {
        return (this.restoreStatus & PASSWORD_PRESET) == PASSWORD_PRESET;
    }

    public boolean isBackupPasswordPreset() {
        return (this.backupStatus & PASSWORD_PRESET) == PASSWORD_PRESET;
    }

    public boolean isRestoreDisabled() {
        return this.restoreStatus == RESTORE_DISABLE;
    }

    public boolean isBackupForced() {
        return (this.backupStatus & BACKUP_FORCE) == BACKUP_FORCE;
    }

    public boolean isBackupDisabled() {
        return this.backupStatus == BACKUP_DISABLE;
    }

    public boolean isBackupAdminDisabled() {
        return this.backupStatus == BACKUP_DISABLE || isBackupPasswordPreset();
    }

    public boolean isSkipBackupPasswordEntry() {
        return isBackupAdminDisabled();
    }

    private String hash() {
        String result =
            Integer.toHexString(this.backupStatus) +
                Integer.toHexString(this.restoreStatus) +
                identity +
                password +
                serverName +
                serverUsername +
                serverPassword;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(result.getBytes());
            return Base32.encode(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            //
        }
        return "";
    }

    public void saveConfig(PreferenceService preferenceService) {
        if (ConfigUtils.isWorkRestricted()) {
            preferenceService.setThreemaSafeMDMConfig(hash());
        }
    }

    public boolean hasChanged(PreferenceService preferenceService) {
        if (ConfigUtils.isWorkRestricted()) {
            String oldHash = preferenceService.getThreemaSafeMDMConfig();
            return !hash().equals(oldHash);
        }
        return false;
    }
}
