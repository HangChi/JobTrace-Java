package com.jobtrace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.jobtrace.jobmarket.application.JobMarketReadQuery;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.jobtrace", importOptions = ImportOption.DoNotIncludeTests.class)
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

    @ArchTest
    static final ArchRule EXPORT_READ_CONTEXT_HAS_NO_REVERSE_DEPENDENCIES = noClasses()
            .that().resideInAnyPackage("..applications..", "..interviews..", "..analytics..")
            .should().dependOnClassesThat().resideInAPackage("..datatransfer..");

    @ArchTest
    static final ArchRule EXPORT_CONTEXT_DOES_NOT_START_JOBS_OR_IMPORTS = noClasses()
            .that().resideInAPackage("..datatransfer..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..reminders..", "..jobs..", "..email..");

    @ArchTest
    static final ArchRule APPLICATION_READ_DOMAIN_HAS_NO_OUTWARD_DEPENDENCIES = noClasses()
            .that().resideInAPackage("..applications.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..applications.application..",
                    "..applications.infrastructure..",
                    "..applications.web..");

    @ArchTest
    static final ArchRule APPLICATION_READ_USE_CASES_HAVE_NO_ADAPTER_DEPENDENCIES = noClasses()
            .that().resideInAPackage("..applications.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..applications.infrastructure..",
                    "..applications.web..");

    @ArchTest
    static final ArchRule APPLICATION_READ_STORAGE_DOES_NOT_DEPEND_ON_HTTP = noClasses()
            .that().resideInAPackage("..applications.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("..applications.web..");

    @ArchTest
    static final ArchRule APPLICATIONS_DO_NOT_DEPEND_ON_INTERVIEWS_OR_DIALOG = noClasses()
            .that().resideInAPackage("..applications..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..interviews..", "..applicationdialog..");

    @ArchTest
    static final ArchRule INTERVIEWS_DO_NOT_DEPEND_ON_APPLICATIONS_OR_DIALOG = noClasses()
            .that().resideInAPackage("..interviews..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..applications..", "..applicationdialog..");

    @ArchTest
    static final ArchRule DIALOG_HAS_NO_ADAPTER_DEPENDENCIES = noClasses()
            .that().resideInAPackage("..applicationdialog.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..applications.infrastructure..", "..applications.web..",
                    "..interviews.infrastructure..", "..interviews.web..");

    @ArchTest
    static final ArchRule REMINDER_DOMAIN_HAS_NO_OUTWARD_DEPENDENCIES = noClasses()
            .that().resideInAPackage("..reminders.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..reminders.application..", "..reminders.infrastructure..",
                    "..reminders.web..");

    @ArchTest
    static final ArchRule REMINDER_USE_CASES_HAVE_NO_ADAPTER_DEPENDENCIES = noClasses()
            .that().resideInAPackage("..reminders.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..reminders.infrastructure..", "..reminders.web..");

    @ArchTest
    static final ArchRule REMINDER_STORAGE_DOES_NOT_DEPEND_ON_HTTP = noClasses()
            .that().resideInAPackage("..reminders.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("..reminders.web..");

    @ArchTest
    static final ArchRule REMINDERS_DO_NOT_DEPEND_ON_APPLICATIONS_OR_ANALYTICS = noClasses()
            .that().resideInAPackage("..reminders..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..applications..", "..analytics..");

    @ArchTest
    static final ArchRule JOB_MARKET_DOMAIN_HAS_NO_OUTWARD_DEPENDENCIES = noClasses()
            .that().resideInAPackage("..jobmarket.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..jobmarket.application..", "..jobmarket.infrastructure..",
                    "..jobmarket.web..");

    @ArchTest
    static final ArchRule JOB_MARKET_USE_CASES_HAVE_NO_ADAPTER_DEPENDENCIES = noClasses()
            .that().resideInAPackage("..jobmarket.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..jobmarket.infrastructure..", "..jobmarket.web..");

    @ArchTest
    static final ArchRule JOB_MARKET_STORAGE_DOES_NOT_DEPEND_ON_HTTP = noClasses()
            .that().resideInAPackage("..jobmarket.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage("..jobmarket.web..");

    @ArchTest
    static final ArchRule JOB_MARKET_STORAGE_IMPLEMENTS_READ_PORT = classes()
            .that().resideInAPackage("..jobmarket.infrastructure..")
            .and().haveSimpleNameEndingWith("ReadQuery")
            .should().implement(JobMarketReadQuery.class);

    @ArchTest
    static final ArchRule JOB_MARKET_HAS_NO_PRIVATE_CONTEXT_INFRASTRUCTURE_CYCLES = noClasses()
            .that().resideInAPackage("..jobmarket..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..applications.infrastructure..", "..reminders.infrastructure..",
                    "..datatransfer.infrastructure..");
}
