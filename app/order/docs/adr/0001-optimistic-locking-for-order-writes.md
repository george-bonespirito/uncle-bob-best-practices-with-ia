# Optimistic locking (version) para escritas concorrentes no Order

Cinco Processing Steps independentes (Data Enrichment, Credit Validation, Stock Validation, Route Calculation, Invoice Generation) modificam o mesmo Order concorrentemente, cada uma escrevendo um subconjunto de campos. O mecanismo anterior do usuário (lock distribuído via Redis) serializa as etapas, mas depende de infraestrutura externa e de gestão de TTL/renovação do lock.

Decidimos usar optimistic locking — coluna `version` no Order + `UPDATE ... WHERE id=? AND version=?` condicional, com retry no conflito (recarrega o Order já com as mudanças de outras etapas, reaplica só a própria mudança) — em vez de:

- **`SELECT ... FOR UPDATE SKIP LOCKED`** — semanticamente errado aqui. SKIP LOCKED pula a linha já travada em vez de esperar ou reagir; uma etapa concorrente simplesmente deixaria de aplicar sua mudança ao encontrar o Order travado por outra etapa.
- **`SELECT ... FOR UPDATE` (sem skip)** — corrigiria o lost update, mas serializa as 5 etapas do mesmo Order, eliminando o paralelismo entre elas.
- **UPDATE parcial por coluna sem lock nenhum** — também resolveria (Postgres serializa no nível de linha sozinho), mas exige que todo repositório trabalhe em updates granulares em vez do padrão load-mutate-save. Optimistic locking permite manter esse padrão com uma trava de segurança e retry explícito — mais didático pra demonstrar tratamento de conflito, que é um dos objetivos de aprendizado deste projeto.

**Nota de escopo — cobre `order_items` também:** o Order é um agregado (aggregate root) com itens de linha em `order_items` (N produtos por pedido). Escritas em itens (ex: Product/Pricing enrichment por item) usam o mesmo `version` do Order — não existe `version` por item. Qualquer mudança, seja no Order ou em algum `order_item`, incrementa o `version` do agregado inteiro. Mantém uma única fronteira de concorrência, consistente com o resto desta ADR — não abrimos uma segunda dimensão de lost update dentro dos itens.

Status: accepted
