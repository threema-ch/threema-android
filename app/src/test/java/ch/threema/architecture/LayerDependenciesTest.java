package ch.threema.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.runner.RunWith;

import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.DatabaseMigrationFailedException;
import ch.threema.app.exceptions.DatabaseMigrationLockedException;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.stores.IdentityProvider;
import ch.threema.app.utils.ListReader;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.data.models.GroupModel;
import ch.threema.data.repositories.ModelRepositories;
import ch.threema.data.storage.SqliteDatabaseBackend;
import ch.threema.logging.LogBackendFactoryImpl;
import ch.threema.logging.backend.DebugLogFileBackend;
import ch.threema.storage.DatabaseNonceStore;
import ch.threema.storage.DatabaseService;
import ch.threema.storage.factories.BallotModelFactory;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.factories.GroupMessageModelFactory;
import ch.threema.storage.factories.RejectedGroupMessageFactory;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.data.media.FileDataModel;

import static ch.threema.architecture.ArchitectureDefinitions.APP;
import static ch.threema.architecture.ArchitectureDefinitions.BASE;
import static ch.threema.architecture.ArchitectureDefinitions.DATA;
import static ch.threema.architecture.ArchitectureDefinitions.DOMAIN;
import static ch.threema.architecture.ArchitectureDefinitions.LOCALCRYPTO;
import static ch.threema.architecture.ArchitectureDefinitions.LOGGING;
import static ch.threema.architecture.ArchitectureDefinitions.STORAGE;
import static ch.threema.architecture.ArchitectureDefinitions.THREEMA_ROOT_PACKAGE;
import static ch.threema.architecture.ArchitectureDefinitions.getLayeredArchitecture;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = THREEMA_ROOT_PACKAGE, importOptions = {ArchitectureTestUtils.DoNotIncludeAndroidTests.class})
public class LayerDependenciesTest {
    @ArchTest
    public static final ArchRule appLayerAccess = getLayeredArchitecture()
        .whereLayer(APP).mayNotBeAccessedByAnyLayer()
        // Storage layer may access services and utils
        .ignoreDependency(
            nameMatching("ch\\.threema\\.storage\\..*"),
            nameMatching("ch\\.threema\\.app\\.services\\..*")
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.storage\\..*"),
            nameMatching("ch\\.threema\\.app\\.preference\\.service\\..*")
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.storage\\..*"),
            nameMatching("ch\\.threema\\.app\\.utils\\..*")
        )
        // Data layer may access listeners, utils, multi-device, and reflection tasks
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.managers\\..*")
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.listeners\\..*")
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.utils\\..*")
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.multidevice\\..*")
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.tasks\\..*")
        )
        // TODO(ANDR-4361): Remove this
        // Data layer needs to access "ThreemaApplication.getServiceManager()" to get old service
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.ThreemaApplication.*")
        )
        // TODO(ANDR-4361): Remove this
        // Data layer needs to access old services to keep caches in sync
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\..*"),
            nameMatching("ch\\.threema\\.app\\.services\\..*")
        )
        // TODO(ANDR-3325): Remove
        .ignoreDependency(
            nameMatching("ch\\.threema\\.data\\.repositories\\.EmojiReactionsRepository"),
            nameMatching("ch\\.threema\\.app\\.emojis\\.EmojiUtil")
        )
        .ignoreDependency(
            nameMatching("ch\\.threema\\.localcrypto\\.LocalCryptoFeatureModule.*"),
            nameMatching("ch\\.threema\\.app\\..*")
        )
        .ignoreDependency(DatabaseService.class, DatabaseMigrationFailedException.class)
        .ignoreDependency(DatabaseService.class, DatabaseMigrationLockedException.class)
        .ignoreDependency(DatabaseNonceStore.class, DatabaseMigrationFailedException.class)
        .ignoreDependency(ConversationModel.class, MessageReceiver.class)
        .ignoreDependency(ConversationModel.class, GroupMessageReceiver.class)
        .ignoreDependency(ConversationModel.class, DistributionListMessageReceiver.class)
        .ignoreDependency(ConversationModel.class, ContactMessageReceiver.class)
        .ignoreDependency(BallotModelFactory.class, ContactMessageReceiver.class)
        .ignoreDependency(BallotModelFactory.class, GroupMessageReceiver.class)
        .ignoreDependency(BallotModelFactory.class, MessageReceiver.class)
        .ignoreDependency(FileDataModel.class, ListReader.class)
        .ignoreDependency(LogBackendFactoryImpl.class, BuildConfig.class)
        .ignoreDependency(LogBackendFactoryImpl.class, BuildFlavor.class)
        .ignoreDependency(LogBackendFactoryImpl.class, BuildFlavor.Companion.class)
        .ignoreDependency(LogBackendFactoryImpl.class, ThreemaApplication.class)
        .ignoreDependency(LogBackendFactoryImpl.class, ThreemaApplication.Companion.class)
        .ignoreDependency(DebugLogFileBackend.Companion.class, HandlerExecutor.class)
        .ignoreDependency(DebugLogFileBackend.class, HandlerExecutor.class)
        .ignoreDependency(ModelRepositories.class, IdentityProvider.class)
        .ignoreDependency(SqliteDatabaseBackend.class, IdentityProvider.class)
        .ignoreDependency(DatabaseService.class, IdentityProvider.class)
        .ignoreDependency(ContactModelFactory.class, IdentityProvider.class)
        .ignoreDependency(nameMatching("ch\\.threema\\.storage\\.StorageModuleKt.*"), nameMatching("ch\\.threema\\.app\\.stores\\.IdentityProvider"));

    @ArchTest
    public static final ArchRule dataLayerAccess = getLayeredArchitecture()
        .whereLayer(DATA).mayOnlyBeAccessedByLayers(APP, STORAGE)
        .ignoreDependency(GroupMessageModelFactory.class, GroupModel.class)
        .ignoreDependency(RejectedGroupMessageFactory.class, GroupModel.class)
        .ignoreDependency(ConversationModel.class, GroupModel.class);

    @ArchTest
    public static final ArchRule storageLayerAccess = getLayeredArchitecture()
        .whereLayer(STORAGE).mayOnlyBeAccessedByLayers(APP, DATA);

    @ArchTest
    public static final ArchRule localcryptoLayerAccess = getLayeredArchitecture()
        .whereLayer(LOCALCRYPTO).mayOnlyBeAccessedByLayers(APP, DATA, STORAGE);

    @ArchTest
    public static final ArchRule domainLayerAccess = getLayeredArchitecture()
        .whereLayer(DOMAIN).mayOnlyBeAccessedByLayers(APP, DATA, STORAGE, LOCALCRYPTO);

    @ArchTest
    public static final ArchRule baseLayerAccess = getLayeredArchitecture()
        .whereLayer(BASE).mayOnlyBeAccessedByLayers(APP, DATA, STORAGE, LOCALCRYPTO, DOMAIN,
            LOGGING);

}
