package com.example.shop;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Fronteiras do monolito modular verificadas em CI: um modulo so conhece
 * o contrato publico (api) do outro, nunca sua implementacao (internal).
 */
@AnalyzeClasses(packages = "com.example.shop", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule ordersDoNotUseCatalogInternals = noClasses()
            .that().resideInAPackage("..orders..")
            .should().dependOnClassesThat().resideInAPackage("..catalog.internal..");

    @ArchTest
    static final ArchRule catalogDoesNotUseOrders = noClasses()
            .that().resideInAPackage("..catalog..")
            .should().dependOnClassesThat().resideInAPackage("..orders..");

    @ArchTest
    static final ArchRule notificationsOnlyKnowTheKafkaContract = noClasses()
            .that().resideInAPackage("..notifications..")
            .should().dependOnClassesThat().resideInAnyPackage("..orders..", "..catalog..");
}
