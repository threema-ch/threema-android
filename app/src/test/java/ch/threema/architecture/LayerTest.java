/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_ANNOTATION;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_APP;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_BASE;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_DATA;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_DOMAIN;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_LOCALCRYPTO;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_LOGGING;
import static ch.threema.architecture.ArchitectureDefinitions.PACKAGE_STORAGE;
import static ch.threema.architecture.ArchitectureDefinitions.THREEMA_ROOT_PACKAGE;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = THREEMA_ROOT_PACKAGE, importOptions = { ArchitectureTestUtils.DoNotIncludeAndroidTests.class })
public class LayerTest {
	@ArchTest
	public static final ArchRule classesInPredefinedLayers = classes().should().resideInAnyPackage(
		PACKAGE_ANNOTATION,
		PACKAGE_APP + "..",
		PACKAGE_BASE + "..",
		PACKAGE_DATA + "..",
		PACKAGE_DOMAIN + "..",
		PACKAGE_LOCALCRYPTO + "..",
		PACKAGE_LOGGING + "..",
		PACKAGE_STORAGE + ".."
	).orShould().resideInAnyPackage(
		"ch.threema.protobuf..",
		"ch.threema.webrtc..",
		"ch.threema.taskmanager..",
		"ch.threema.testhelpers.."
	);

}
