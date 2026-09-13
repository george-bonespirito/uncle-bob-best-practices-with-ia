# Invoice é documento interno e terminal, com numeração que aceita buracos

Três decisões sobre o faturamento, tomadas juntas porque a primeira delimita as outras duas.

## Documento interno, não nota fiscal

O PRD usa "nota fiscal", e isso sugere emissão perante autoridade fiscal. Decidimos que **não**: a Invoice é documento interno, com número, data e itens faturados.

Emitir NF-e de verdade traz SEFAZ, XML assinado, cálculo de imposto, contingência, carta de correção e nota de devolução — um domínio inteiro, maior que o resto deste sistema junto, e nada no PRD o pede.

O glossário lista `Tax` e `Fiscal Authority` como termos que **não existem**, em vez de omiti-los. Omitir deixaria a suposição errada de pé, e ela é cara: quem assumisse NF-e desenharia numeração contígua, série, e um fluxo de cancelamento que não existe.

## Falha cancela e compensa

A Invoice Generation é a última etapa, e nesse ponto já há crédito em hold e estoque reservado. Ela é **hard-blocking**, como as demais: falha permanente cancela o Order e compensa os dois.

A alternativa — retentar indefinidamente com o pedido em `INVOICING` — travaria recurso de terceiros por uma falha nossa. É o mesmo argumento que sustentou o cancelamento por crédito insuficiente na [ADR 0015](0015-credit-hold-lifecycle.md).

A compensação funciona sem caso especial: a Hold Conversion acontece **na** emissão, então uma emissão que falhou deixa o hold como hold, e `release-credit-hold` o libera normalmente.

## Numeração monotônica, buracos tolerados

O `Invoice Number` cresce e pode ter lacunas — um Order cancelado depois de numerar deixa um buraco, e isso é aceito.

Garantir contiguidade exigiria serializar todas as emissões, o que mataria a concorrência entre instâncias do MS Order — o ponto central do desenho, sustentado pela ADR order/0003 e pela topologia de partições do Kafka.

Contiguidade só é obrigação perante autoridade fiscal, que a primeira decisão tirou de escopo. Trocar o pilar de concorrência do sistema por uma propriedade que ninguém exige seria inverter a prioridade.

## Terminal

Uma Invoice emitida não é cancelada nem reemitida.

Cancelar reabriria tudo que a emissão congela: o Product Status reverificado, o preço congelado no enriquecimento, o Credit Hold já convertido em dívida. Cada um desses tem regra própria de congelamento, e desfazê-los exigiria uma regra de estorno para cada.

Isso é funcionalidade nova — com decisão de negócio sobre devolução, crédito ao cliente e reversão de estoque —, não um detalhe deste contexto.

Status: accepted

## Revisão (2026-09-12) — falha da Invoice não cancela automaticamente

A seção **Falha cancela e compensa** fica superseded by [ADR order/0019](./0019-invoice-failed-holds-until-human-action.md): retry automático até um teto, depois `INVOICE_FAILED` com crédito e estoque presos, e só Cancellation humana dispara compensação. Documento interno, numeração com buracos e Invoice emitida terminal **não mudam**.
