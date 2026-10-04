package com.bonespirito.order

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Regras de arquitetura do contexto Order, checadas no `./gradlew check`.
 *
 * - `domain.policy` não depende de Spring nem de JPA (ADR order/0006). A regra
 *   é sobre `domain.policy`, não sobre `domain` inteiro — o agregado usa JPA de
 *   propósito.
 * - Camadas (ADR 0009 de sistema): `application` não depende de `infra`, e
 *   `domain` não depende de `application` nem de `infra`. A dependência aponta
 *   para dentro: infra → application → domain.
 * - `application` também não depende do mecanismo de persistência (Spring Data,
 *   JPA, Hibernate, JDBC): pela ADR 0009 o port é interface pura em
 *   `application.port` e o adapter `Jpa*` mora em `infra.persistence`. Sem
 *   exceção.
 */
class OrderArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.bonespirito.order")

    @Test
    fun `domain policy nao depende de Spring nem de JPA`() {
        noClasses()
            .that().resideInAPackage("..domain.policy..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
            .check(classes)
    }

    @Test
    fun `application nao depende de infra`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infra..")
            .because("ADR 0009: application declara o port, infra implementa o adapter")
            .check(classes)
    }

    @Test
    fun `domain nao depende de application`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .because("ADR 0009: a dependência aponta para dentro, domain é o centro")
            .check(classes)
    }

    @Test
    fun `domain nao depende de infra`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infra..")
            .because("ADR 0009: a dependência aponta para dentro, domain é o centro")
            .check(classes)
    }

    @Test
    fun `application nao depende do mecanismo de persistencia`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(*PERSISTENCIA)
            .because("ADR 0009: port é interface pura; Spring Data/JPA ficam no adapter Jpa* em infra")
            .check(classes)
    }

    private companion object {
        val PERSISTENCIA = arrayOf(
            "org.springframework.data..",
            "jakarta.persistence..",
            "org.hibernate..",
            "java.sql..",
            "javax.sql..",
        )
    }
}
