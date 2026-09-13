# Padrão de observabilidade das aplicações

O que todo microsserviço deste sistema é **obrigado** a emitir. Vale para MS Order, MS Notifications e para os 7 MS de dado quando existirem.

A infraestrutura de coleta (Alloy + Loki, retenção D-1) é do repositório de infra e está descrita lá — ver ADR-019 deles. **Este documento não descreve o backend**: o Loki é armazenamento e não garante padrão nenhum. O contrato são as convenções do OTel, e quem as impõe é o Collector no caminho. Trocar o backend não mudaria uma linha daqui.

Decisões e justificativas em [ADR raiz 0009](../adr/0009-observability-standard.md).

## Atributos obrigatórios

Todo sinal (log, trace, métrica) carrega:

| Atributo | Exemplo | Origem |
|---|---|---|
| `service.name` | `ms-order` | nome do Application no ArgoCD, prefixo `ms-` |
| `service.version` | `a1b2c3d` | tag da imagem, que é o SHA do commit |
| `deployment.environment` | `prod` | ambiente do deploy |

**O que chegar sem os três não é descartado: vai para um stream de quarentena**, marcado como inválido. Fora do padrão não polui o fluxo bom e continua visível — perder log porque ele estava mal formado é perder evidência exatamente durante o deploy ruim que a causou.

## Labels indexados

Três: `service.name`, `k8s.namespace.name` e `deployment.environment`.

Todo o resto — `trace_id`, `span_id`, `k8s.pod.name` — vai para structured metadata, onde é consultável sem custo de índice.

`k8s.namespace.name` é o label que **de fato separa** um workload de outro: o cluster tem 8 namespaces e um único ambiente, então é por ele que se consulta. `deployment.environment` é constante hoje (um cluster só), o que o torna barato — um label com um valor multiplica a cardinalidade por um — e já deixa o eixo pronto para o dia em que houver um segundo ambiente.

> **Limite do backend, e ele morde:** structured metadata tem teto de **64 KB e 128 entradas por linha**, e o excesso é **rejeitado com HTTP 400 — não truncado**. Atributo demais não gera log gordo, gera perda de log.

## Formato do log

- **JSON estruturado.** Texto livre continua funcionando e não correlaciona com trace, que é o único motivo de fazer isso.
- **Severidade nos níveis do OTel.**
- **`trace_id` e `span_id` no próprio registro.**

Ao consultar, lembre que ponto vira underscore: `service.name` no OTel é `service_name` no LogQL.

## O que é proibido logar

Nominal, não genérico — regra de "não logue dado sensível" não impede nada:

- **`customer_snapshot` inteiro** — carrega nome legal, documento e endereço de entrega.
- **`Document`** (CPF/CNPJ), em qualquer nível, inclusive `DEBUG`.
- **`Delivery Address`**, completo ou em partes.
- **`WhatsApp Phone`.**

Referencie por `customer_id` e `order_id`, que são identificadores sem conteúdo pessoal. Quem precisar do dado consulta o contexto dono, que é onde o acesso é controlado.

Isto vale inclusive em exceção: mensagem de erro que serializa o agregado inteiro vaza tudo de uma vez, e é o caminho mais comum do vazamento.

### A rede de segurança do Collector é rasa, e não substitui a lista

O Collector **mascara** padrões de CPF/CNPJ no corpo da linha, trocando por `[REDACTED-DOC]`. Mascara, não derruba: descartar a linha destruiria a evidência exatamente no caso em que alguém logou o que não devia — que é quando se quer o resto da linha para achar o código culpado. O `trace_id`, o `service.name`, a mensagem e a stack sobrevivem; perde-se só o número.

**Isso pega um dos quatro campos proibidos.** Nome legal, endereço de entrega e telefone não têm formato reconhecível por expressão regular. A proteção real é a regra por nome de campo acima; o Collector só apara o descuido no formato mais óbvio.

Ler essa máscara como suficiente é o erro que ela convida.

## Auditoria não vive em log

A retenção é **D-1** — verificado em produção: janela de 30–48h atrás devolve zero linhas.

**Nenhum evento de domínio existe apenas como linha de log.** Mudança de status de Order, falha de compensação e linha rejeitada no import já têm lugar no Postgres (`orders.status`, `order_update_event`, Rejected Order Row) e é lá que sobrevivem, com backup real.

Log serve para operação e diagnóstico. Se a resposta a "onde isso fica registrado?" for só o Loki, o desenho está errado e a correção é no domínio, não na retenção.

## Métricas de domínio

**Uma métrica só existe se alguém faz algo diferente ao vê-la.** Cada uma abaixo carrega, na definição, o problema que denuncia e a ação esperada — sem isso vira painel que ninguém abre.

| Métrica | O que denuncia | Ação |
|---|---|---|
| Profundidade da fila `order_update_event` | Consumo mais lento que a produção — resultados de Processing Step chegando mais rápido do que o MS Order aplica | Escalar instâncias do MS Order, ou investigar contenção no `SKIP LOCKED` |
| Taxa de conflito de `version` | Concorrência do enriquecimento mal dimensionada — as 3 tasks da Data Enrichment brigando pelo mesmo agregado | Rever paralelismo do fork-join; conflito alto significa retrabalho, não erro |
| Orders em `AWAITING_MASTER_DATA` | Fila de trabalho **manual** de ops que ninguém está atendendo | Cadastrar o dado faltante; crescimento sustentado significa que o import recebe planilha com cadastro inexistente |
| Orders em `COMPENSATION_FAILED` | Fila manual de mesma natureza, e mais grave: crédito ou estoque presos sem dono | Intervenção manual; nenhum destes se resolve sozinho |
| Profundidade dos tópicos `.dlt` | Mensagem que nenhum retry salvou — resultado de Processing Step ou notificação perdidos | Resolução manual, e investigar a causa comum se concentrar num tópico |
| Taxa de item `NOT_FULFILLED` | **Catálogo sujo**, não problema de pedido: SKUs `INACTIVE` continuam chegando nas planilhas de import | Corrigir na origem, com quem emite a planilha |

A última é a menos óbvia e a que mais economiza tempo: atendimento parcial é silencioso por desenho — o pedido segue, ninguém é avisado do detalhe, e sem esta métrica o problema aparece pelo cliente reclamando.

### Cardinalidade em métrica

**Nenhuma métrica leva `order_id`, `sku` ou `customer_id` como label.** Status do Order (8 valores) e nome do tópico são aceitáveis; identificador de entidade não — o mesmo erro de cardinalidade que derrubou o label de pod na coleta de log, agora em métrica, onde cada série vive no tempo.

### O que não é métrica de domínio

Pedidos por dia, faturamento, ticket médio. É BI: outro consumidor, outro ciclo de vida, e arrastaria agregação de negócio e PII para um canal que este padrão acabou de restringir.

## Trace

Amostragem **100%** enquanto o volume não doer. Amostrar antes de medir é otimizar o que ainda não se conhece.

Gatilho para revisitar: quando o volume diário de trace se aproximar do que a retenção D-1 aguenta sem pressão de disco — a infra mede e avisa; não é decisão da aplicação.

As portas OTLP (4317/4318) já estão abertas. No dia em que a primeira app emitir, não há mudança de infra a fazer.
