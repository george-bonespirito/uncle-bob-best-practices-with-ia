package com.bonespirito.stock

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * ADR order/0006 (espelhada neste contexto): as decisões de domínio vivem em
 * `domain.policy` e não dependem de Spring nem de JPA. `allowEmptyShould`
 * porque a regra precisa passar vazia no skeleton e morder no primeiro dia em
 * que houver classe.
 */
class StockArchitectureTest {

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.bonespirito.stock")

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
