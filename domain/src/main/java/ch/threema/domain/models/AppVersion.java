package ch.threema.domain.models;

import ch.threema.domain.protocol.Version;

public class AppVersion extends Version {
    private final String appVersionNumber;
    private final String appPlatformCode;
    private final String appLanguage;
    private final String appCountry;
    private final String appSystemModel;
    private final String appSystemVersion;

    /**
     * Create an app version object.
     *
     * @param appVersionNumber version number, a short string in the format major.minor (e.g. "1.0")
     * @param appPlatformCode  platform code, single letter (A = Android, I = iPhone, Q = Desktop/Web, J = Generic Java)
     * @param appLanguage      language code, ISO 639-1 (e.g. "de", "en")
     * @param appCountry       country code, ISO 3166-1 (e.g. "CH", "DE", "US")
     * @param appSystemModel   smartphone model string
     * @param appSystemVersion operating system version string
     */
    public AppVersion(String appVersionNumber, String appPlatformCode, String appLanguage, String appCountry, String appSystemModel, String appSystemVersion) {
        this.appVersionNumber = appVersionNumber;
        this.appPlatformCode = appPlatformCode;
        this.appLanguage = appLanguage;
        this.appCountry = appCountry;
        this.appSystemModel = appSystemModel;
        this.appSystemVersion = appSystemVersion;
    }

    @Override
    public String getVersionNumber() {
        return appVersionNumber;
    }

    /**
     * Return the short version: Version;PlatformCode
     */
    @Override
    public String getVersionString() {
        return appVersionNumber + appPlatformCode;
    }

    /**
     * Return the full version. Used in the CSP client-info extension payload.
     * <p>
     * Format: `<app-version>;<platform>;<lang>/<country-code>;<device-model>;<os-version>`
     */
    @Override
    public String getFullVersionString() {
        return appVersionNumber.replace(";", "_") + ";"
            + appPlatformCode.replace(";", "_") + ";"
            + appLanguage.replace(";", "_") + "/"
            + appCountry.replace(";", "_") + ";"
            + appSystemModel.replace(";", "_") + ";"
            + appSystemVersion.replace(";", "_");
    }
}
