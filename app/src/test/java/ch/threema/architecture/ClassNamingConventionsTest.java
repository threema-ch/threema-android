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
