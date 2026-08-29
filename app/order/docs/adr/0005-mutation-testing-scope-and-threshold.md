# Mutation testing focado em lógica de decisão real, não domain.* inteiro

A decisão original de mutation testing (`targetClasses=domain.*`, 80% de threshold implícito) foi tomada antes do domínio virar Order Processing — era um placeholder genérico do round inicial de guardrails, nunca revisitado contra as classes reais.

Decidimos restringir o alvo do Pitest às áreas com decisão/branch de negócio real, em vez de `domain.*` inteiro. Com a ADR 0006 (JPA no aggregate root, decisões puras em `domain.policy`), o alvo agora tem nome de classe concreto:

1. Transição de `Order Status` — `domain.policy.OrderStatusTransition` (quais transições são válidas — ver glossário)
2. Avaliação de `Hard-blocking Step` — `domain.policy.OrderStatusTransition` (uma falha bloqueia ou não o avanço do workflow)
3. Decisão de compensar — `domain.policy.CompensationPolicy` (Credit Validation/Stock Validation já bem-sucedidas → dispara `Compensating Action`; Enrichment/Route Calculation → não, mesmo falhando)
4. Conflito de `version` no optimistic locking — **fora do escopo de `domain.policy`** (ver nota da ADR 0006: é comportamento mediado pelo JPA, não lógica pura isolável; não é alvo de mutation testing focado, fica coberto pelo teste de integração do repositório)
5. Duplicata de `source_order_number` na importação — `domain.policy` (função de detecção, mesma chave em lote diferente ou no mesmo lote)

Motivo: `domain.*` largo reintroduziria o ruído já documentado (mutantes em `equals`/`hashCode`/`componentN` de `data class`, `when` exaustivo) sem ganho — essas 5 áreas são onde um mutante sobrevivente significa de fato "teste não cobre esse caso de borda", que é o valor real da ferramenta.

Threshold: **80%, número único**, sem split humano/agente (diferente do Detekt, que tem 8/12 de complexidade). Motivo: mutation score mede robustez do teste, não complexidade do código de produção — não há justificativa técnica pra relaxar o threshold só porque o código foi gerado por agente; o teste precisa ser igualmente robusto não importa quem escreveu a implementação.

Alternativa rejeitada: manter `domain.*` inteiro. Mais abrangente, mas reintroduz ruído conhecido sem ganho de sinal proporcional.

Status: accepted
