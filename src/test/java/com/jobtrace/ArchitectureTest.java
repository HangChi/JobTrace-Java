package com.jobtrace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.jobtrace")
class ArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_INDEPENDENT = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "..infrastructure..",
                    "..web..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_WEB = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..web..", "..infrastructure..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule MODULES_HAVE_NO_CYCLES = slices()
            .matching("com.jobtrace.(*)..")
            .should().beFreeOfCycles();
}
