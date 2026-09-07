// Toda a convenção (toolchain, guardrails, thresholds) vem do build raiz.
// Este arquivo existe pro que for específico do MS Order.

// Só o Order tem banco próprio (import_batches, orders, order_items) — Notifications
// não persiste nada, só consome CDC e chama Customers via REST.
apply(plugin = "org.jetbrains.kotlin.plugin.jpa")

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
    "implementation"(libs.flyway.core)

    "testRuntimeOnly"(libs.h2)
}
