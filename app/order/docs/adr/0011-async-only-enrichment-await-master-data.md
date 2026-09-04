# Enriquecimento exclusivamente assíncrono: o import não consulta ninguém, e cadastro faltando faz o Order esperar

Supersede [ADR order/0009](./0009-validate-customer-at-import-degrade-on-outage.md) e [ADR order/0010](./0010-in-memory-customer-existence-cache.md), tomadas horas antes desta.

A 0009 introduziu validação síncrona de `customer_id` no import — uma chamada REST do MS Order ao MS Customers antes de criar Orders — e a 0010 pôs um cache em memória na frente dela para reduzir o volume de chamadas. Era o único acoplamento HTTP entre o MS Order e um microsserviço de dado em todo o sistema, e existia para não iniciar workflow com dado quebrado.

Decidimos **reverter as duas**: o MS Order não consulta nenhum microsserviço de dado de forma síncrona, e não mantém cache de dado de outro contexto.

## O que o import valida

Só o que dá pra verificar dentro do próprio arquivo: dado obrigatório ausente, duplicata de `(source_system_code, source_order_number)`, e o `Import Fingerprint` (que recusa o reenvio do mesmo arquivo com `409` — decidido na 0009 e **preservado**, por não ter relação com acoplamento). Existência de `customer_id` e de `sku` não é verificada aqui.

## O que acontece quando o cadastro não existe

A Processing Step descobre. `Customer Not Found` (ou o equivalente para produto) faz a task falhar no Orkes, e o Order vai para o status **`AWAITING_MASTER_DATA`** — visível na própria tabela `orders`, não escondido no estado do workflow.

A partir daí:

- **Ops cadastra o dado faltante** no MS dono e **reexecuta a task** no Conductor (*retry from failed task*), partindo só da task que falhou — as demais tasks do sub-workflow já aplicaram seu resultado, e reexecutá-las republicaria resultado que o `resultId` descartaria como duplicata: inofensivo, mas ruído.
- **A espera não tem prazo.** O Order fica em `AWAITING_MASTER_DATA` indefinidamente, sem compensar nem cancelar.

## Por que a espera pode ser indefinida

Porque nada foi reservado ainda. Numa versão anterior deste design, as Processing Steps rodavam concorrentes, e um Order travado esperando cadastro poderia estar segurando uma reserva de estoque e um hold de crédito por tempo indeterminado — estoque preso num pedido parado é venda perdida em outro pedido, e isso exigiria um prazo máximo com compensação automática.

Com o workflow sequencial por pedido ([ADR raiz 0008](../../../docs/adr/0008-sequential-processing-steps-per-order.md)), a Data Enrichment é a primeira Processing Step: se ela trava, Credit Validation e Stock Validation nem começaram. Não há nada preso, então não há custo em esperar, e o Order continua vendável no momento em que o cadastro aparecer. As duas decisões se sustentam mutuamente — se o workflow voltar a ser concorrente, este ponto precisa ser revisto.

## Por que reverter a validação síncrona

A 0009 estava resolvendo o problema certo pelo caminho errado. O objetivo — não deixar um pedido andar com dado inexistente — continua valendo; o que muda é que ele passa a ser garantido pela própria Processing Step, que é o lugar onde a informação é usada, em vez de por uma pré-checagem que precisava:

- abrir a primeira dependência HTTP do MS Order sobre um microsserviço de dado, num sistema cujo princípio é integração por Kafka;
- carregar um cache com política própria (o que entra, o que não expira, o que nunca é cacheado) para tornar essa dependência tolerável;
- distinguir "não existe" de "não consegui perguntar" e degradar de forma diferente em cada caso — regra correta, mas que só existia por causa da chamada síncrona.

Custo aceito: um arquivo com muitas linhas de `customer_id`/`sku` errados cria muitos Orders travados esperando cadastro, cada um exigindo ação manual, em vez de virar linhas de erro num relatório de import. É trabalho de ops em vez de perda de venda — o pedido não é recusado, só espera. `AWAITING_MASTER_DATA` como status próprio existe justamente para que essa fila de trabalho seja consultável.

Consequência boa: nenhum microsserviço de dado fora do ar afeta o import. Antes, indisponibilidade do Customers exigia uma regra de degradação explícita; agora o import nem sabe que os outros contextos existem.

**Retomada automática foi rejeitada.** Fazer o cadastro publicar `CustomerRegistered`/`ProductRegistered` e um consumidor casar isso com workflows travados exigiria eventos de ciclo de vida, que o [`CONTEXT.md` do Customers](../../../customers/CONTEXT.md) rejeita explicitamente por não terem consumidor. Automatizar depois é uma mudança grande, não um ajuste — mas o caminho manual não impede.

Status: accepted
