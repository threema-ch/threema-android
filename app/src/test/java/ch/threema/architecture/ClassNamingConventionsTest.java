/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import androidx.lifecycle.ViewModel;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;

import static ch.threema.architecture.ArchitectureDefinitions.THREEMA_ROOT_PACKAGE;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = THREEMA_ROOT_PACKAGE)
public class ClassNamingConventionsTest {
    @ArchTest
    public static final ArchRule viewModelImplementations = classes().that().areAssignableTo(ViewModel.class)
        .should().haveSimpleNameEndingWith("ViewModel");

    @ArchTest
    public static final ArchRule viewModelName = classes().that().haveSimpleNameEndingWith("ViewModel")
        .should().beAssignableTo(ViewModel.class);


    @ArchTest
    public static final ArchRule abstractMessageSpecialization =
        classes().that().areAssignableTo(AbstractMessage.class)
            .should().haveSimpleNameEndingWith("Message");
}
