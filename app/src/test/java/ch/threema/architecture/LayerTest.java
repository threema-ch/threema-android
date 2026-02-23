package ch.threema.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.runner.RunWith;

import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_ANDROID;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_ANNOTATION;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_APP;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_BASE;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_COMMON;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_DATA;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_DOMAIN;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_LOCALCRYPTO;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_LOGGING;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_STORAGE;
import static ch.threema.architecture.ArchitectureDefinitions.THREEMA_ROOT_PACKAGE;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = THREEMA_ROOT_PACKAGE, importOptions = {ArchitectureTestUtils.DoNotIncludeAndroidTests.class})
public class LayerTest {
    /**
     * @noinspection unused
     */
    @ArchTest
    public static final ArchRule classesInPredefinedLayers = classes().should().resideInAnyPackage(
        PACKAGE_ANNOTATION,
        PACKAGE_APP + "..",
        PACKAGE_BASE + "..",
        PACKAGE_DATA + "..",
        PACKAGE_DOMAIN + "..",
        PACKAGE_ANDROID + "..",
        PACKAGE_COMMON + "..",
        PACKAGE_LOCALCRYPTO + "..",
        PACKAGE_LOGGING + "..",
        PACKAGE_STORAGE + ".."
    ).orShould().resideInAnyPackage(
        "ch.threema.protobuf..",
        "ch.threema.webrtc..",
        "ch.threema.taskmanager..",
        "ch.threema.testhelpers..",
        "ch.threema.libthreema.."
    );

}
