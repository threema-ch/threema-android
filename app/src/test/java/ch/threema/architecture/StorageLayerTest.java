/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_STORAGE;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;

import org.junit.runner.RunWith;

import ch.threema.storage.factories.ModelFactory;
import ch.threema.storage.models.ValidationMessage;
import ch.threema.storage.models.access.Access;
import ch.threema.storage.models.data.DisplayTag;
import ch.threema.storage.models.data.MessageContentsType;
import ch.threema.storage.models.data.MessageDataInterface;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = PACKAGE_STORAGE, importOptions = { ArchitectureTestUtils.DoNotIncludeAndroidTests.class })
public class StorageLayerTest {
	@ArchTest
	public static final ArchRule factoriesNaming =
		classes().that().resideInAPackage(PACKAGE_STORAGE + ".factories..")
			.and().areNotAnonymousClasses()
			.and().areNotInnerClasses()
			.and().areNotNestedClasses()
			.should().haveSimpleNameEndingWith("Factory")
			.orShould().haveSimpleNameEndingWith("FactoryKt");

	@ArchTest
	public static final ArchRule factoriesExtendModelFactory =
		classes().that().resideInAPackage(PACKAGE_STORAGE + ".factories..")
			.and().areNotAnonymousClasses()
			.and().areNotInnerClasses()
			.and().areNotNestedClasses()
			.and().haveSimpleNameEndingWith("Factory") // This excludes kotlin classes as they cannot be checked to be assignable to a java class
			.should().beAssignableTo(ModelFactory.class);

	@ArchTest
	public static final ArchRule modelNaming =
		classes().that().resideInAPackage(PACKAGE_STORAGE + ".models..")
			.and().areNotAnonymousClasses()
			.and().areNotInnerClasses()
			.and().areNotNestedClasses()
			.and().areNotEnums()
			.and().doNotBelongToAnyOf(
				ValidationMessage.class,
				Access.class,
				MessageContentsType.class,
				MessageDataInterface.class,
				MediaMessageDataInterface.class,
				DisplayTag.class
			)
			.should().haveSimpleNameEndingWith("Model");
}
