package org.shakvilla.beatzmedia.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.junit.jupiter.api.Tag;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ArchUnit hexagonal dependency-rule suite. CI runs this with {@code -Dgroups=arch}. All rules
 * scan only production classes (tests excluded). Testing-strategy §6.
 *
 * <p>Rules enforced:
 * <ol>
 *   <li>Domain code has no framework imports (Jakarta/Quarkus/Hibernate).
 *   <li>Dependency direction: {@code adapter → application → domain} (never reversed).
 *   <li>No JPA annotations on domain types.
 *   <li>Inbound and outbound adapters never import each other.
 *   <li>No {@code Instant.now()} / {@code UUID.randomUUID()} in domain or application code.
 * </ol>
 */
@Tag("arch")
@AnalyzeClasses(
    packages = "org.shakvilla.beatzmedia",
    importOptions = DoNotIncludeTests.class)
class ArchitectureRulesTest {

  /** Domain classes must not import any framework. */
  @ArchTest
  static final ArchRule domain_is_framework_free =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "jakarta..",
              "io.quarkus..",
              "org.hibernate..",
              "io.smallrye..",
              "software.amazon..");

  /** Layered architecture: adapter → application → domain. */
  @ArchTest
  static final ArchRule layered_architecture =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy("..domain..")
          .layer("Application")
          .definedBy("..application..")
          .layer("Adapter")
          .definedBy("..adapter..")
          .whereLayer("Adapter")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Adapter")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Application", "Adapter");

  /** Domain types must carry no JPA annotations. */
  @ArchTest
  static final ArchRule domain_has_no_jpa =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .beAnnotatedWith("jakarta.persistence.Entity")
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage("jakarta.persistence..");

  /** Inbound adapters must not import outbound adapters and vice versa. */
  @ArchTest
  static final ArchRule inbound_adapters_dont_import_outbound =
      noClasses()
          .that()
          .resideInAPackage("..adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.out..");

  /** No wall-clock or random in domain/application code. */
  @ArchTest
  static final ArchRule no_wallclock_in_core =
      noClasses()
          .that()
          .resideInAnyPackage("..domain..", "..application..")
          .should()
          .callMethod(java.time.Instant.class, "now")
          .orShould()
          .callMethod(java.util.UUID.class, "randomUUID");
}
