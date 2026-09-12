# Invoice (Faturamento)

Contexto dono do documento de faturamento do pedido. Executa a Invoice Generation — a última Processing Step, e o ponto em que o pedido deixa de ser reversível pelo sistema.

## Language

**Invoice**:
Documento que registra a venda de um Order: número, data de emissão e os itens atendidos com seus valores. **Documento interno** — este sistema não emite nota fiscal perante autoridade fiscal.
_Avoid_: nota fiscal (sugere NF-e e obrigação legal, que não existem aqui), fatura de cobrança, boleto, DANFE

**Invoice Number**:
Identidade da Invoice. **Monotônico, com buracos tolerados**: um Order cancelado depois de numerar deixa lacuna, e isso é aceito. Garantir contiguidade exigiria serializar todas as emissões e mataria a concorrência entre instâncias do MS Order.
_Avoid_: numeração fiscal, série, sequencial contíguo, numeração sem buraco

**Invoice Generation**:
A Processing Step que este contexto executa. Além de emitir, ela é o **ponto de congelamento** do pedido: reconfere o [Product Status](../products/CONTEXT.md) antes de emitir ([ADR order/0012](../order/docs/adr/0012-partial-fulfillment-for-inactive-products.md)) e dispara a [Hold Conversion](../contabil/CONTEXT.md) no Contabil ([ADR order/0015](../order/docs/adr/0015-credit-hold-lifecycle.md)). É **hard-blocking**: falha cancela o Order e compensa crédito e estoque.
_Avoid_: emissão (sozinho, esconde as duas outras responsabilidades), faturamento como sinônimo de cobrança

**Invoiced Items**:
Os Order Items `FULFILLED` no momento da emissão — nem todos os itens do pedido. Item que virou `NOT_FULFILLED` na reverificação de status sai da Invoice, e o valor faturado é o [Order Total](../order/CONTEXT.md) daquele momento.
_Avoid_: itens do pedido (inclui os não atendidos), todos os itens

**Invoice Cancellation**:
Não existe. A Invoice é **terminal**: uma vez emitida, não é cancelada nem reemitida. Cancelar reabriria tudo que a emissão congela — status de produto, preço, hold convertido — e exigiria regra de estorno própria. Seria funcionalidade nova, não detalhe deste contexto.
_Avoid_: estorno, carta de correção, reemissão, nota de devolução

**Tax**:
Não existe. Nenhum imposto é calculado, destacado ou recolhido. Se um dia entrar, é decisão de escopo do sistema, não deste contexto.
_Avoid_: ICMS, imposto, tributo, base de cálculo, alíquota

**Fiscal Authority**:
Não existe. Não há SEFAZ, XML, assinatura digital, autorização de uso nem contingência. Termo listado porque "nota fiscal" no PRD sugere o contrário, e a suposição errada custaria caro.
_Avoid_: SEFAZ, NF-e, XML, autorização, contingência, certificado digital
