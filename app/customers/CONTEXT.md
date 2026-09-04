# Customers (Cadastro de Clientes)

Contexto dono do cadastro de clientes do sistema de supply chain: fonte de verdade do nome, documento, endereços de entrega e contato de quem compra. Alimenta a Data Enrichment do Order com o dado que vira Snapshot do lado de lá.

## Language

**Customer**:
Pessoa física ou jurídica que compra. Aggregate root deste contexto — identidade própria (`customer_id`), cadastro autônomo, não é projeção nem cópia de sistema externo. Só é válido com pelo menos um Delivery Address.
_Avoid_: Cliente (sem qualificador), Account, Client, Buyer, Comprador

**Customer Id**:
Identidade do Customer, emitida por este contexto e usada pelo mundo todo pra referenciá-lo — inclusive pela planilha de import do Order, que traz o `customer_id` já resolvido. Não existe código alternativo por sistema de origem.
_Avoid_: customer_code (era o nome na planilha antes de o cadastro virar dono da identidade — não usar mais), código do cliente

**Document**:
Documento legal do Customer — CPF ou CNPJ. Value Object: o formato determina o Customer Type e o dígito verificador é invariante, então um Document inválido não é representável.
_Avoid_: CPF/CNPJ como campos separados, tax id, documento genérico sem validação

**Customer Type**:
Natureza do Customer — `INDIVIDUAL` (CPF) ou `COMPANY` (CNPJ). Derivado do Document, nunca informado à parte: não existe Customer `COMPANY` com CPF.
_Avoid_: pessoa física/jurídica como flag independente do documento, segment, categoria de cliente

**Name**:
Nome legal do Customer — razão social para `COMPANY`, nome civil para `INDIVIDUAL`. Um campo só, o que casa com o Document e o que a nota fiscal exige. Nome fantasia não existe neste contexto.
_Avoid_: nome fantasia, trade name, display name, razão social como campo separado

**Delivery Address**:
Endereço onde o pedido do Customer pode ser entregue. Entidade filha do agregado Customer — nunca existe fora dele. O Customer tem N Delivery Addresses e exatamente um é o Default Delivery Address.
_Avoid_: endereço (sem qualificar entrega), shipping address, location, endereço de cobrança (não existe aqui)

**Default Delivery Address**:
O Delivery Address que a Customer Enrichment copia pro Snapshot do Order quando o pedido não indica outro — e o pedido nunca indica outro hoje. O primeiro endereço cadastrado vira o default; um Customer nunca fica sem um.
_Avoid_: endereço principal, primary address, endereço ativo

**WhatsApp Phone**:
Número de WhatsApp do Customer, canal único de notificação de mudança de status do pedido. Nunca é copiado pro Snapshot do Order: MS Notifications consulta este contexto no momento do envio, pra que troca de número valha imediatamente — ver Customers Lookup abaixo.
_Avoid_: telefone (sem qualificar canal), phone number, contato

**Customer Enrichment**:
A Processing Step do Order que este contexto executa: dado um `customer_id`, devolve Name, Document e Default Delivery Address pro Order gravar como Snapshot. É a única coisa que este contexto faz pelo Order — não valida crédito, não bloqueia pedido, não decide nada sobre o Order.
_Avoid_: Data Enrichment (é o sub-workflow inteiro, que inclui Products e Pricing — Customer Enrichment é só a parte deste contexto), lookup de cliente, consulta de cliente

**Customers Lookup**:
Consulta síncrona a este contexto, fora do fluxo de Processing Step. Consumidor único: MS Notifications, buscando o WhatsApp Phone no momento de enviar a mensagem. O MS Order nunca consulta este contexto de forma síncrona — todo enriquecimento é assíncrono. Contrato em [`customers-api-contract.md`](../docs/specs/customers-api-contract.md).
_Avoid_: API do Customers (genérico), consulta de cliente, customer service

**Customer Not Found**:
O `customer_id` não existe no cadastro. É a única causa de falha de negócio da Customer Enrichment, e não recusa o pedido: a task fica travada até o cliente ser cadastrado e a task reexecutada — ver [ADR order/0011](../order/docs/adr/0011-async-only-enrichment-await-master-data.md). Indisponibilidade deste contexto nunca é Customer Not Found: são coisas distintas e o consumidor precisa distingui-las.
_Avoid_: cliente inválido, customer rejected, erro de enriquecimento, cliente indisponível

**Customer Status**:
Não existe. O cadastro não tem estado — não há cliente bloqueado, inativo ou suspenso, e nenhum consumidor deve inferir um. Um Customer existe ou não existe. Termo listado aqui só pra impedir que reapareça por engano.
_Avoid_: ACTIVE/INACTIVE, cliente bloqueado, situação cadastral
