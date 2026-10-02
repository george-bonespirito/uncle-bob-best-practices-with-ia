package com.bonespirito.products.domain

/**
 * Aggregate root do contexto: item vendável do catálogo, identidade própria
 * (sku), cadastro autônomo. Não existe hierarquia produto-pai/variante —
 * tamanho ou cor, se um dia importarem, serão Products distintos com SKUs
 * distintos (CONTEXT.md).
 *
 * Name e Description são campos diferentes: a linha de pedido mostra Name;
 * o texto longo fica aqui e nunca vai pro Snapshot. Physical Attributes são
 * obrigatórios — Product sem eles é recusa no cadastro.
 */
class Product private constructor(
    val sku: Sku,
    val name: String,
    val description: String,
    val category: String,
    val physicalAttributes: PhysicalAttributes,
    val status: ProductStatus,
) {
    /**
     * Cadastro nasce ACTIVE. INACTIVE não é cadastro — é o Product existente,
     * com cadastro completo e histórico, que deixa de ser vendável: `deactivate`.
     */
    fun deactivate(): Product =
        Product(sku, name, description, category, physicalAttributes, ProductStatus.INACTIVE)

    companion object {
        /**
         * `physicalAttributes` nullable só pra exercitar de fora o cenário
         * "sem nenhum": quem chama sem eles recebe a recusa, não um Product
         * inválido — Product sem Physical Attributes é inútil pro Route.
         */
        fun create(
            sku: Sku,
            name: String,
            description: String,
            category: String,
            physicalAttributes: PhysicalAttributes?,
        ): Product {
            require(name.isNotBlank()) { "name ausente" }
            require(category.isNotBlank()) { "category ausente" }
            requireNotNull(physicalAttributes) { "Physical Attributes são obrigatórios" }
            return Product(sku, name, description, category, physicalAttributes, ProductStatus.ACTIVE)
        }
    }
}
