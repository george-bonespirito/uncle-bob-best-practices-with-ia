package com.bonespirito.order

import com.bonespirito.order.application.port.ImportBatchRepository
import com.bonespirito.order.application.port.OrderRepository
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
 *   `Jpa*` mora em `infra`. Hoje os repositories restantes de `application.port`
 *   estendem `JpaRepository` direto — violação que já existia quando a regra
 *   entrou. Ficam como exceção nomeada em [EXCECOES_PERSISTENCIA]; corrigir é
 *   outro PR, não este.
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

        private const val MOTIVO_JPA_REPOSITORY =
            "port estende JpaRepository (Spring Data) em vez de interface pura com adapter Jpa* " +
                "em infra (ADR 0009); já existia quando a regra entrou, corrige em PR próprio"

        /** Classe → motivo. Violação que já existia no código; não corrigida neste PR. */
        val EXCECOES_PERSISTENCIA: Map<Class<*>, String> = mapOf(
            ImportBatchRepository::class.java to MOTIVO_JPA_REPOSITORY,
            OrderRepository::class.java to MOTIVO_JPA_REPOSITORY,
        )
    }
}
