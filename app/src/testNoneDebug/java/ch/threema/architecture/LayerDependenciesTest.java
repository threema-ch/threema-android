/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2022 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.runner.RunWith;

import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.DatabaseMigrationFailedException;
import ch.threema.app.exceptions.DatabaseMigrationLockedException;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.utils.ListReader;
import ch.threema.app.utils.ZipUtil;
import ch.threema.app.utils.executor.HandlerExecutor;
import ch.threema.logging.LoggerManager;
import ch.threema.logging.backend.DebugLogFileBackend;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.NonceDatabaseBlobService;
import ch.threema.storage.factories.BallotModelFactory;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.data.media.FileDataModel;

import static ch.threema.architecture.ArchitectureDefinitions.APP;
import static ch.threema.architecture.ArchitectureDefinitions.BASE;
import static ch.threema.architecture.ArchitectureDefinitions.DOMAIN;
import static ch.threema.architecture.ArchitectureDefinitions.LOCALCRYPTO;
import static ch.threema.architecture.ArchitectureDefinitions.STORAGE;
import static ch.threema.architecture.ArchitectureDefinitions.THREEMA_ROOT_PACKAGE;
import static ch.threema.architecture.ArchitectureDefinitions.getLayeredArchitecture;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = THREEMA_ROOT_PACKAGE, importOptions = { ArchitectureTestUtils.DoNotIncludeAndroidTests.class })
public class LayerDependenciesTest {
	@ArchTest
	public static final ArchRule appLayerAccess = getLayeredArchitecture()
		.whereLayer(APP).mayNotBeAccessedByAnyLayer()
		.ignoreDependency(
			nameMatching("ch\\.threema\\.storage\\..*"),
			nameMatching("ch\\.threema\\.app\\.services\\..*")
		)
		.ignoreDependency(
			nameMatching("ch\\.threema\\.storage\\..*"),
			nameMatching("ch\\.threema\\.app\\.utils\\..*")
		)
		.ignoreDependency(DatabaseServiceNew.class, DatabaseMigrationFailedException.class)
		.ignoreDependency(DatabaseServiceNew.class, DatabaseMigrationLockedException.class)
		.ignoreDependency(NonceDatabaseBlobService.class, DatabaseMigrationFailedException.class)
		.ignoreDependency(ConversationModel.class, MessageReceiver.class)
		.ignoreDependency(ConversationModel.class, GroupMessageReceiver.class)
		.ignoreDependency(ConversationModel.class, DistributionListMessageReceiver.class)
		.ignoreDependency(ConversationModel.class, ContactMessageReceiver.class)
		.ignoreDependency(BallotModelFactory.class, ContactMessageReceiver.class)
		.ignoreDependency(BallotModelFactory.class, GroupMessageReceiver.class)
		.ignoreDependency(BallotModelFactory.class, MessageReceiver.class)
		.ignoreDependency(FileDataModel.class, ListReader.class)
		.ignoreDependency(LoggerManager.class, BuildConfig.class)
		.ignoreDependency(DebugLogFileBackend.class, FileService.class)
		.ignoreDependency(DebugLogFileBackend.class, HandlerExecutor.class)
		.ignoreDependency(DebugLogFileBackend.class, ZipUtil.class)
		.ignoreDependency(DebugLogFileBackend.class, ThreemaApplication.class); // TODO(ANDR-1439): Refactor

	@ArchTest
	public static final ArchRule storageLayerAccess = getLayeredArchitecture()
		.whereLayer(STORAGE).mayOnlyBeAccessedByLayers(APP);

	@ArchTest
	public static final ArchRule localcryptoLayerAccess = getLayeredArchitecture()
		.whereLayer(LOCALCRYPTO).mayOnlyBeAccessedByLayers(APP, STORAGE);

	@ArchTest
	public static final ArchRule domainLayerAccess = getLayeredArchitecture()
		.whereLayer(DOMAIN).mayOnlyBeAccessedByLayers(APP, STORAGE);


	@ArchTest
	public static final ArchRule baseLayerAccess = getLayeredArchitecture()
		.whereLayer(BASE).mayOnlyBeAccessedByLayers(APP, STORAGE, LOCALCRYPTO, DOMAIN);

}
