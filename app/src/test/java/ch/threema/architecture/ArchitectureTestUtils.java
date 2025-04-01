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

import com.google.common.base.Predicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

import java.util.regex.Pattern;

import static com.google.common.base.Predicates.not;

public class ArchitectureTestUtils {

    static final Predicate<String> UNIT_TEST_PATTERN = input -> {
        if (input == null) return false;
        return Pattern.compile(".*/build/intermediates/[^/]*/[^/]*UnitTest/.*").matcher(input).matches();
    };
    static final Predicate<String> NOT_UNIT_TEST_PATTERN = not(UNIT_TEST_PATTERN);

    /**
     * Ignore class files that stem from the unit test folder.
     */
    static final class DoNotIncludeAndroidTests implements ImportOption {
        @Override
        public boolean includes(Location location) {
            return NOT_UNIT_TEST_PATTERN.apply(location.toString());
        }
    }
}
