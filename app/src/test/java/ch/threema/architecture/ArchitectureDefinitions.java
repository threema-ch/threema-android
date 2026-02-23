package ch.threema.architecture;

import androidx.annotation.NonNull;

import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

public class ArchitectureDefinitions {
    private ArchitectureDefinitions() {
    }

    public static final String THREEMA_ROOT_PACKAGE = "ch.threema";
    private static final String THREEMA_ROOT_PACKAGE_DOT = THREEMA_ROOT_PACKAGE + ".";

    // Layer names
    public static final String
        ANDROID = "android",
        ANNOTATION = "annotation",
        APP = "app",
        BASE = "base",
        DATA = "data",
        DOMAIN = "domain",
        COMMON = "common",
        LOCALCRYPTO = "localcrypto",
        LOGGING = "logging",
        STORAGE = "storage";

    // Layer packages
    public static final String
        PACKAGE_ANDROID = THREEMA_ROOT_PACKAGE_DOT + ANDROID,
        PACKAGE_ANNOTATION = THREEMA_ROOT_PACKAGE_DOT + ANNOTATION,
        PACKAGE_APP = THREEMA_ROOT_PACKAGE_DOT + APP,
        PACKAGE_BASE = THREEMA_ROOT_PACKAGE_DOT + BASE,
        PACKAGE_DATA = THREEMA_ROOT_PACKAGE_DOT + DATA,
        PACKAGE_DOMAIN = THREEMA_ROOT_PACKAGE_DOT + DOMAIN,
        PACKAGE_COMMON = THREEMA_ROOT_PACKAGE_DOT + COMMON,
        PACKAGE_LOCALCRYPTO = THREEMA_ROOT_PACKAGE_DOT + LOCALCRYPTO,
        PACKAGE_LOGGING = THREEMA_ROOT_PACKAGE_DOT + LOGGING,
        PACKAGE_STORAGE = THREEMA_ROOT_PACKAGE_DOT + STORAGE;


    public static @NonNull Architectures.LayeredArchitecture getLayeredArchitecture() {
        return layeredArchitecture()
            .layer(APP).definedBy(PACKAGE_APP + "..")
            .layer(STORAGE).definedBy(PACKAGE_STORAGE + "..")
            .layer(LOCALCRYPTO).definedBy(PACKAGE_LOCALCRYPTO + "..")
            .layer(DATA).definedBy(PACKAGE_DATA + "..")
            .layer(DOMAIN).definedBy(PACKAGE_DOMAIN + "..")
            .layer(ANDROID).definedBy(PACKAGE_ANDROID + "..")
            .layer(COMMON).definedBy(PACKAGE_COMMON + "..")
            .layer(BASE).definedBy(PACKAGE_BASE + "..")
            .layer(LOGGING).definedBy(PACKAGE_LOGGING + "..")
            .layer(ANNOTATION).definedBy(PACKAGE_ANNOTATION + "..");
    }
}
