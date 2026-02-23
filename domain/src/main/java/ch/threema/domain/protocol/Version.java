package ch.threema.domain.protocol;

public class Version {

    private static final String VERSION = "0.2";

    public String getVersionNumber() {
        return VERSION;
    }

    public String getVersionString() {
        return VERSION + "J";
    }

    public String getFullVersionString() {
        return VERSION + ";J;;;" + System.getProperty("java.version");
    }

    public String getArchitecture() {
        return System.getProperty("os.arch");
    }
}
