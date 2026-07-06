package com.streamtube.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Makes the layering rules executable — until now they lived only in module javadoc/comments.
 * Analyzed from the API test classpath, which sees domain, application and infrastructure (the
 * worker module is not on it, so its classes are simply absent, not exempt).
 */
@AnalyzeClasses(
    packages = "com.streamtube",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domainIsFrameworkFree =
      classes()
          .that()
          .resideInAPackage("com.streamtube.domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("com.streamtube.domain..", "java..")
          .because("the domain layer must stay free of frameworks and outer layers");

  @ArchTest
  static final ArchRule applicationStaysOutOfWebAndPersistence =
      noClasses()
          .that()
          .resideInAPackage("com.streamtube.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.streamtube.infrastructure..",
              "com.streamtube.api..",
              "com.streamtube.worker..",
              "org.springframework.web..",
              "jakarta.persistence..",
              "jakarta.servlet..")
          .because(
              "use cases orchestrate domain ports only; wiring annotations (@Service/@Transactional)"
                  + " are the accepted exception, web and persistence are not");

  @ArchTest
  static final ArchRule infrastructureDoesNotDependOnBootstraps =
      noClasses()
          .that()
          .resideInAPackage("com.streamtube.infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.streamtube.api..", "com.streamtube.worker..")
          .because("adapters must not reach into the applications that assemble them");
}
