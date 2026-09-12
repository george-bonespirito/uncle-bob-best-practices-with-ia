# Contabil (Crédito do Cliente)

Contexto dono do limite de crédito de cada cliente e dos compromissos feitos contra ele. Executa a Credit Validation do Order — a etapa que decide se o cliente pode comprar aquele valor antes de o pedido consumir estoque.

## Language

**Credit Limit**:
Valor máximo que um Customer pode ter comprometido ao mesmo tempo. Cadastro deste contexto, por cliente.
_Avoid_: limite (sem qualificar), crédito (sem qualificar — pode ser limite, disponível ou hold), score

**Available Credit**:
`Credit Limit` menos os Credit Holds ativos e a dívida já convertida. É contra este número que a Credit Validation decide, e é **derivado** — pelo mesmo motivo do Stock: dois números que deveriam concordar sempre acabam discordando.
_Avoid_: saldo, crédito livre, limite disponível como campo armazenado

**Credit Hold**:
Compromisso de parte do Available Credit com um Order específico. Tem identidade própria (`creditHoldId`), que é o que a compensação recebe para liberar. Enquanto existe, o valor sai do Available Credit sem virar dívida.
_Avoid_: bloqueio, pré-autorização, reserva de crédito (use Hold, que é o termo do contrato Kafka), lançamento

**Credit Validation**:
A Processing Step do Order que este contexto executa: dado um Order e o [Order Total](../order/CONTEXT.md), cria um Credit Hold ou recusa. É **hard-blocking** — `REJECTED` cancela o pedido.
_Avoid_: análise de crédito (implica julgamento além do limite, que não existe aqui), aprovação, consulta de limite

**Credit Rejected**:
Available Credit insuficiente para o Order Total. Cancela o pedido, não espera. A razão de não esperar é que o pedido travado seguraria estoque reservado por tempo indefinido, esperando o cliente pagar — ver [ADR order/0015](../order/docs/adr/0015-credit-hold-lifecycle.md).
_Avoid_: crédito negado, cliente inadimplente (é causa possível, não o estado), bloqueado

**Credit Account Not Found**:
O Customer não tem cadastro de crédito neste contexto — diferente de ter cadastro com Available Credit zero, que é Credit Rejected. Cadastro ausente **não cancela**: trava em `AWAITING_MASTER_DATA` até a conta existir, o mesmo caminho de `Product Not Found`, `Price Not Found` e `Stock Record Not Found`.
_Avoid_: cliente sem crédito (ambíguo entre as duas coisas), limite zero, conta inexistente como recusa

**Hold Conversion**:
Transformação de um Credit Hold em dívida real, disparada pela emissão da nota fiscal. Fecha o ciclo: o valor sai do Available Credit de forma permanente, em vez de continuar como compromisso reversível.
_Avoid_: baixa, efetivação, liquidação (é o pagamento, que não existe neste escopo), faturamento (é o evento que dispara, não a conversão)

**Hold Release**:
Liberação de um Credit Hold, devolvendo o valor ao Available Credit. Acontece **só** por ação compensatória explícita (`release-credit-hold`), identificada pelo `creditHoldId`; liberar um id já liberado é no-op. **Não há expiração automática**, pelo mesmo motivo do Stock: hold preso alguém percebe, hold que volta sozinho vira venda sem crédito.
_Avoid_: estorno, cancelamento de hold, TTL, expiração

**Payment**:
Não existe aqui. Recebimento, baixa de título e inadimplência estão fora do escopo deste sistema. Este contexto conhece limite, holds e dívida convertida — não sabe se alguém pagou.
_Avoid_: pagamento, recebimento, baixa, cobrança, inadimplência

**Credit Score**:
Não existe. O limite é cadastrado, não calculado — não há política de risco, análise comportamental nem limite dinâmico. Se um dia houver, a Credit Validation continua igual: ela compara valor com Available Credit e nada mais.
_Avoid_: score, rating, política de crédito, análise de risco
