package com.bonespirito.order

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Assertions.assertTrue
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
 *   JPA, Hibernate, JDBC): pela ADR 0009 o port é interface pura e o adapter
 *   `Jpa*` mora em `infra`. Os três repositories de `application.port` já
 *   são interface pura; [EXCECOES_PERSISTENCIA] ficou vazia.
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
    fun `application nao depende do mecanismo de persistencia, fora as excecoes nomeadas`() {
        noClasses()
            .that().resideInAPackage("..application..")
            .and().doNotBelongToAnyOf(*EXCECOES_PERSISTENCIA.keys.toTypedArray())
            .should().dependOnClassesThat().resideInAnyPackage(*PERSISTENCIA)
            .because("ADR 0009: port é interface pura; Spring Data/JPA ficam no adapter Jpa* em infra")
            .check(classes)
    }

    /**
     * Exceção que deixou de violar sai da lista: quando um repository virar
     * interface pura com adapter em `infra`, este teste falha e pede a remoção
     * da entrada — a exceção não fica esquecida cobrindo código novo.
     */
    @Test
    fun `cada excecao nomeada ainda viola a regra de persistencia`() {
        EXCECOES_PERSISTENCIA.keys.forEach { excecao ->
            val dependeDePersistencia = classes.get(excecao).directDependenciesFromSelf
                .any { dep -> dep.targetClass.estaEmPersistencia() }
            assertTrue(dependeDePersistencia) {
                "${excecao.simpleName} não depende mais de Spring Data/JPA — remova de EXCECOES_PERSISTENCIA"
            }
        }
    }

    private fun JavaClass.estaEmPersistencia(): Boolean =
        PERSISTENCIA.any { pkg -> packageName.startsWith(pkg.removeSuffix("..")) }

    private companion object {
        val PERSISTENCIA = arrayOf(
            "org.springframework.data..",
            "jakarta.persistence..",
            "org.hibernate..",
            "java.sql..",
            "javax.sql..",
        )

        /** Classe → motivo. Violação que já existia no código; não corrigida neste PR. */
        val EXCECOES_PERSISTENCIA: Map<Class<*>, String> = emptyMap()
    }
}
