---
name: quality-gate
description: Roda a pirâmide de qualidade real (detekt, testes, jacoco coverage, ArchUnit, pitest mutation) por subprojeto Gradle antes de abrir PR. Use antes de qualquer PR no domínio Order Processing (app/), ou quando o usuário pedir pra rodar o quality gate / a pirâmide de teste.
user-invocable: false
---

# quality-gate

Fecha o loop TDD + Quality Gate do `CLAUDE.md` raiz: "Qualquer layer vermelho → não abre PR." Este skill roda o gate real e reporta números medidos — nunca declare cobertura, threshold de mutation ou "passou" sem ter rodado isto primeiro (Verification Protocol).

## Caveat conhecido: golden rule container-first

`CLAUDE.md` exige `docker compose run --rm <svc> ./gradlew check` — mas este repo **não tem `compose.yaml` nem `Dockerfile`** hoje. Isso é um blocker pré-existente, não algo pra este skill resolver sozinho.

- Se `compose.yaml` existir no momento do uso: rode via `docker compose run --rm <svc> ./gradlew check`, um serviço por subprojeto.
- Se não existir: **avise o usuário explicitamente** que o gate vai rodar no host (`./gradlew check`), fora do container, violando a golden rule — não rode em silêncio como se fosse o caminho normal. Pergunte se quer que o compose seja criado primeiro (fora do escopo deste skill) ou se aceita a exceção pra esta rodada.

## Subprojetos Gradle hoje

Só `order` e `notifications` têm `build.gradle.kts` (ver `app/settings.gradle.kts`). `customers` ainda não é módulo Gradle — não rode `check` nele até que exista.

## O que `check` já cobre (config em `app/build.gradle.kts`)

- **detekt** — `buildUponDefaultConfig` + `config/detekt/detekt.yml` custom
- **testes** (JUnit 5 + Cucumber + MockK) via `useJUnitPlatform()`
- **jacoco coverage** — gate: LINE ≥ 80%, BRANCH ≥ 70% (exclui `*Application*`), falha o `check` se não bater
- **ArchUnit** — roda como teste normal (procure classes `*ArchitectureTest`)

Pitest **não** está no `check` (caro, de propósito). Rodar separado:

```
./gradlew :order:pitest
./gradlew :notifications:pitest
```

Threshold: 80% mutation em `domain.policy.*` (ver ADR `order/0005`).

## Passos

1. Confirmar com o usuário se roda `docker compose run` (se compose existir) ou local (com aviso do caveat acima).
2. Rodar `./gradlew check` (ou via docker) por subprojeto — não rodar do root sem escopo, cada MS tem seu próprio ciclo.
3. Se algum layer falhar (detekt, teste, coverage), reportar a falha específica (arquivo:linha, regra, ou % medido vs threshold) — **não prosseguir pro PR**.
4. Se `check` passar e o PR envolver `domain.policy.*`, rodar `pitest` também e reportar o mutation score medido.
5. Reportar só números que você mediu nesta execução — nunca reaproveitar número de uma rodada anterior sem re-rodar.
