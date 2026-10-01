// Toda a convenção (toolchain, guardrails, thresholds) vem do build raiz.
// Este arquivo existe pro que for específico do MS Order.

// Só o Order tem banco próprio (import_batches, orders, order_items) — Notifications
// não persiste nada, só consome CDC e chama Customers via REST.
apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
apply(plugin = "com.google.cloud.tools.jib")

dependencies {
    "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
    "implementation"(libs.flyway.core)

    "runtimeOnly"(libs.postgresql)
    "runtimeOnly"(libs.flyway.database.postgresql)

    "testRuntimeOnly"(libs.h2)
}

// Publicação da imagem no GHCR, sem daemon (ADR-026 da infra: o cluster não tem
// Docker em lugar nenhum — Jib builda em camadas e empurra via HTTP direto).
// Tag = CI_COMMIT_SHA quando o Woodpecker executa; `dev` fora do CI.
// Credenciais nunca hardcoded: GHCR_USER/GHCR_TOKEN vêm de env (secret do Woodpecker).
configure<com.google.cloud.tools.jib.gradle.JibExtension> {
    from {
        image = "eclipse-temurin:21-jre-alpine"
    }
    container {
        user = "1000:1000"
        ports = listOf("8080")
    }
    to {
        image = "ghcr.io/torgge/ms-order:${System.getenv("CI_COMMIT_SHA") ?: "dev"}"
        val user = System.getenv("GHCR_USER")
        val token = System.getenv("GHCR_TOKEN")
        if (user != null && token != null) {
            auth {
                username = user
                password = token
            }
        }
    }
}
