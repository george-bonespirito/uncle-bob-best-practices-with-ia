package com.bonespirito.order

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * ADR order/0006: as decisões de domínio vivem em `domain.policy` e não
 * dependem de Spring nem de JPA. A regra é sobre `domain.policy`, não sobre
 * `domain` inteiro — o agregado usa JPA de propósito.
 *
 * `allowEmptyShould` porque no walking skeleton ainda não existe policy alguma:
 * a regra precisa passar vazia hoje e morder no primeiro dia em que houver classe.
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
            .allowEmptyShould(true)
            .check(classes)
    }
}
