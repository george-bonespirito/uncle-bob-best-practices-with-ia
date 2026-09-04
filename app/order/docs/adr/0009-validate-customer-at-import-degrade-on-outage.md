# Validar `customer_id` no import, recusando linha e não lote — e não recusar nada quando o Customers está fora do ar

Até aqui o import só validava o que dava pra ver dentro do próprio arquivo (dado ausente, duplicata de `source_system_code` + `source_order_number`). Um `customer_id` inexistente só era descoberto lá na frente, pela Processing Step de Data Enrichment — depois de o Order existir, do workflow ter sido disparado no Orkes e de uma falha hard-blocking ter que ser tratada.

Decidimos **validar a existência dos `customer_id` no import**, antes de criar qualquer Order, consultando o MS Customers de forma síncrona e em lote (contrato em [`customers-api-contract.md`](../../../docs/specs/customers-api-contract.md)).

Duas escolhas dentro dessa decisão são o que realmente importa aqui, e nenhuma das duas é óbvia lendo o código:

**1. A recusa é por linha, nunca pelo lote.** `customer_id` inexistente vira `Rejected Order Row` em `import_batch_row_errors`, o resto do arquivo é processado e o Import Batch fecha em `COMPLETED_WITH_ERRORS` — mesmo comportamento que a planilha já tinha pra linha inválida. Alternativa rejeitada: recusar o arquivo inteiro na primeira linha ruim. Recusar 49.999 pedidos bons por causa de um cliente errado é perda de venda, e o objetivo do import em massa é justamente processar o máximo possível.

**2. Indisponibilidade do Customers não recusa nada.** Se a consulta falha por timeout ou erro de infraestrutura, os Orders são criados sem validação e a checagem real acontece na Customer Enrichment, que é hard-blocking e tem retry próprio. Só a resposta negativa explícita do Customers ("este `customer_id` não existe") produz `Rejected Order Row`.

Este segundo ponto é o que mais parece bug pra quem chega depois: a validação existe e mesmo assim é ignorada em erro, de propósito. O raciocínio é que "não existe" e "não consegui perguntar" são fatos diferentes, e tratá-los igual transforma um MS fora do ar em perda de venda — exatamente o que a decisão anterior tentava evitar. É a mesma distinção transitório/permanente da [ADR order/0007](./0007-dlt-exception-classifier.md), aplicada noutro ponto do fluxo. Custo aceito: um Order com cliente inexistente criado durante indisponibilidade queima uma volta de workflow antes de morrer em Data Enrichment.

**Consequência não óbvia:** este é o primeiro acoplamento síncrono entre bounded contexts do sistema — todo o resto é Kafka. O MS Customers passa a estar no caminho crítico do import, ainda que de forma degradável. Registrado como risco no PRD. A frequência das chamadas é reduzida pelo `Customer Registry Cache` — ver [ADR order/0010](./0010-in-memory-customer-existence-cache.md).

**Também nesta decisão:** `Import Fingerprint` (hash do conteúdo do arquivo, `UNIQUE` em `import_batches`) recusa o reenvio do mesmo arquivo com `409` antes de criar Import Batch. É camada distinta da duplicata por `(source_system_code, source_order_number)`, que continua existindo e pega o pedido repetido entre arquivos diferentes.

Status: accepted

**Status: superseded by [ADR order/0011](./0011-async-only-enrichment-await-master-data.md)** — a validação síncrona no import foi revertida: todo enriquecimento passou a ser exclusivamente assíncrono, e cliente/produto inexistente é resolvido travando a task até o cadastro existir, não recusando a linha. O `Import Fingerprint` descrito no fim desta ADR **permanece válido** — não tem relação com acoplamento.
