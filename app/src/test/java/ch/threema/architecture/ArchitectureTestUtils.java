package ch.threema.architecture;

import com.google.common.base.Predicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

import java.util.regex.Pattern;

import static com.google.common.base.Predicates.not;

public class ArchitectureTestUtils {

    private static final Pattern javaUnitTestPattern = Pattern.compile(".*/build/intermediates/[^/]*/[^/]*UnitTest/.*");
    private static final Pattern kotlinUnitTestPattern = Pattern.compile(".*/build/tmp/kotlin-classes/[^/]*UnitTest/.*");

    static final Predicate<String> UNIT_TEST_PATTERN = input -> {
        if (input == null) return false;
        return javaUnitTestPattern.matcher(input).matches() || kotlinUnitTestPattern.matcher(input).matches();
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
