package org.shakvilla.beatzmedia.commerce.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * INV-1 usage guard: ownership is granted <strong>only</strong> on settlement. No inbound REST
 * resource may depend on the {@code GrantOwnership} use case — the only sanctioned caller is the
 * {@code PaymentSettledSubscriber} (which reacts to {@code PaymentSettled}). Commerce ADD §12.1.
 */
@Tag("unit")
class GrantOwnershipCallerTest {

  @Test
  void noRestResourceCallsGrantOwnership() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("org.shakvilla.beatzmedia");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage("..adapter.in.rest..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName(
                "org.shakvilla.beatzmedia.commerce.application.port.in.GrantOwnership")
            .as("no REST resource may invoke GrantOwnership — grants happen only on settlement (INV-1)");

    rule.check(classes);
  }
}
