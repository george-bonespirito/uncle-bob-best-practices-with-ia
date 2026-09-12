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

Só **dois**: `service.name` e `deployment.environment`.

Todo o resto — `trace_id`, `span_id`, `k8s.pod.name`, `k8s.namespace.name` — vai para structured metadata, onde é consultável sem custo de índice.

`k8s.namespace.name` fica **fora do índice** enquanto namespace e ambiente forem a mesma coisa; um label que duplica outro paga cardinalidade sem informar. Se houver mais de um namespace por ambiente, a decisão se revisa.

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

## Auditoria não vive em log

A retenção é **D-1** — verificado em produção: janela de 30–48h atrás devolve zero linhas.

**Nenhum evento de domínio existe apenas como linha de log.** Mudança de status de Order, falha de compensação e linha rejeitada no import já têm lugar no Postgres (`orders.status`, `order_update_event`, Rejected Order Row) e é lá que sobrevivem, com backup real.

Log serve para operação e diagnóstico. Se a resposta a "onde isso fica registrado?" for só o Loki, o desenho está errado e a correção é no domínio, não na retenção.

## Trace

Amostragem **100%** enquanto o volume não doer. Amostrar antes de medir é otimizar o que ainda não se conhece.

Gatilho para revisitar: quando o volume diário de trace se aproximar do que a retenção D-1 aguenta sem pressão de disco — a infra mede e avisa; não é decisão da aplicação.

As portas OTLP (4317/4318) já estão abertas. No dia em que a primeira app emitir, não há mudança de infra a fazer.
