# Padrão de observabilidade: quarentena em vez de recusa, três labels, PII proibida por nome

A infra entregou log agregado (Alloy + Loki, retenção D-1, ADR-019 do repositório de infra) e apontou o que aquilo **não** entrega: padrão. O Loki é armazenamento; o contrato são as convenções do OTel, e quem as impõe é o Collector no caminho. Trocar Loki por Elastic ou ClickHouse não mudaria nada — o que decide é o que as aplicações são obrigadas a emitir.

Isso é decisão da aplicação, e está aqui. O padrão operável fica em [`observability-standard.md`](../specs/observability-standard.md).

## Quarentena, não recusa

A recomendação da infra era o Collector **recusar** o que viesse sem `service.name`, `service.version` e `deployment.environment` — o argumento sendo que preencher com default silencioso produz lixo bem formatado, que é pior que nada.

O diagnóstico está certo e o remédio não. **Recusar significa perder log exatamente quando algo está mal configurado** — durante um deploy ruim, uma variável de ambiente que não chegou, um serviço novo mal instrumentado. É o momento em que o log vale mais.

Decidimos **desviar para um stream de quarentena**, marcado como inválido. Mantém o incentivo inteiro — o que está fora do padrão não polui o fluxo bom e aparece como erro — sem destruir evidência. Com D-1 e o volume atual, custa um stream.

Descartar dado porque ele está mal formado é a decisão que se lamenta no meio de um incidente.

## Três labels indexados — e a primeira versão desta ADR errou aqui

A recomendação da infra era indexar `service.name`, `deployment.environment` e `k8s.namespace.name`. Esta ADR, na primeira versão, cortou o namespace com o argumento de que ele duplicaria o ambiente **enquanto cada ambiente fosse um namespace**.

A premissa estava invertida, e a infra respondeu com a contagem em vez do argumento: **8 namespaces, 1 ambiente**. A relação é o oposto — um ambiente, muitos namespaces. `k8s.namespace.name` não duplica nada; é o único label que separa workload de workload hoje, e é por ele que se consulta. Quem é constante é `deployment.environment`.

Ficam os três. O ambiente constante custa quase nada (um label com um valor multiplica a cardinalidade por um) e deixa o eixo pronto para um segundo cluster.

Fica registrado que o erro foi meu e o método foi dela: o ônus de justificar um label é de quem o propõe, e ela o cumpriu medindo em vez de argumentando. O gatilho que eu havia escrito — "revisa-se quando houver mais de um namespace por ambiente" — disparou imediatamente, o que é o comportamento desejado de um gatilho e não uma desculpa para a decisão errada.

## PII proibida por nome, não por princípio

"Não logue dado sensível" não impede nada. A lista impede.

Nossos glossários já definem exatamente o que não pode sair: o `customer_snapshot` carrega nome legal, `Document` (CPF/CNPJ) e `Delivery Address`; o `WhatsApp Phone` vive no Customers e nunca vai para snapshot justamente por ser dado de contato.

O padrão nomeia esses campos e manda referenciar por `customer_id` e `order_id`. A regra vale inclusive em exceção — mensagem de erro que serializa o agregado inteiro é o caminho mais comum do vazamento, e é silencioso, porque ninguém escreveu `log.debug(customer.document)`.

Isso é decisão que só a aplicação pode tomar: a infra não sabe quais campos são pessoais.

O Collector acrescenta uma rede de segurança — mascara padrões de CPF/CNPJ, trocando por `[REDACTED-DOC]` em vez de descartar a linha. Descartar seria aplicar ao contrário o argumento da quarentena: destruiria a evidência justamente quando alguém logou o que não devia.

**A rede é rasa e está escrita como rasa.** Nome legal, endereço e telefone não têm formato reconhecível por regex, então ela pega um dos quatro campos. A infra fez questão de dizer isso, e tem razão: rede de segurança que ninguém entende vira falsa segurança.

## Auditoria não depende do Loki

A retenção D-1 está **provada**, não prometida — a infra verificou que uma janela de 30–48h atrás devolve zero linhas.

Decidimos que **nenhum evento de domínio existe apenas como linha de log**. Mudança de status de Order, falha de compensação e Rejected Order Row já vivem no Postgres, com backup; é lá que sobrevivem.

A alternativa seria aumentar a retenção do que fosse auditoria, e ela inverte a responsabilidade: transformaria uma decisão de domínio ("isto precisa ser reconstituível") num parâmetro de infraestrutura, onde ninguém que conhece o domínio olha.

Consequência que aceitamos: se a resposta a "onde isso fica registrado?" for só o Loki, o desenho está errado, e a correção é no domínio — não na retenção.

## O que este padrão não decidia na primeira versão

Métricas de domínio ficaram abertas: o padrão dizia como emitir, não o que medir. Resolvido na revisão abaixo.

Status: accepted

---

## Revisão — 2026-09-12: o que medir

O critério veio antes da lista: **uma métrica só existe se alguém faz algo diferente ao vê-la.** Sem isso, a lista cresce sozinha e termina em painel que ninguém abre. Cada métrica passa a carregar, na definição, o problema que denuncia e a ação esperada — a [spec](../specs/observability-standard.md) tem a tabela.

Seis métricas. Três vinham do tracker (fila `order_update_event`, conflito de `version`, Orders em `AWAITING_MASTER_DATA`) e três foram acrescentadas por serem o mesmo argumento aplicado a casos que ninguém tinha listado:

- **`COMPENSATION_FAILED`** é fila manual da mesma natureza do `AWAITING_MASTER_DATA`, e mais grave: há crédito e estoque presos sem dono.
- **Profundidade dos `.dlt`** — mensagem que nenhum retry salvou some sem ninguém ver, que é o motivo de o DLT existir em vez do descarte.
- **Taxa de item `NOT_FULFILLED`** é a menos óbvia. Atendimento parcial é silencioso por desenho ([ADR order/0012](../../order/docs/adr/0012-partial-fulfillment-for-inactive-products.md)): o pedido segue e o cliente recebe só um aviso genérico. Taxa alta significa **catálogo sujo** — SKUs descontinuados continuam chegando nas planilhas —, e o problema é da origem, não do pedido. Sem a métrica, descobriríamos pelo cliente reclamando.

**Cardinalidade:** nenhuma métrica leva `order_id`, `sku` ou `customer_id` como label. É o mesmo erro que derrubou o label de pod na coleta de log, agora em métrica, onde cada série persiste no tempo.

**Métrica de negócio fica de fora** — pedidos por dia, faturamento, ticket médio. É BI, com outro consumidor e outro ciclo de vida, e arrastaria agregação e PII para um canal que este padrão restringiu de propósito.

---

## Revisão — 2026-09-15: a sétima métrica, e o título que tinha ficado na versão errada

Duas correções de registro. Nenhuma decisão nova.

**A spec tem sete métricas; a revisão acima diz seis.** `Orders em INVOICE_FAILED` entrou na tabela de [`observability-standard.md`](../specs/observability-standard.md) junto com o status que a [ADR order/0019](../../order/docs/adr/0019-invoice-failed-holds-until-human-action.md) criou, e não passou por aqui — a spec andou à frente do registro. A métrica é o mesmo critério aplicado ao caso novo, e por isso é admitida em vez de removida: é fila manual com **crédito e estoque presos**, e a ação é reprocessar a Invoice ou cancelar o Order. Fica registrada agora.

O padrão que isto expõe é o de sempre por aqui: decisão que entra direto na spec sem passar pela ADR deixa as duas discordando, e quem lê a ADR conta seis.

**O título desta ADR dizia "dois labels".** A seção "Três labels indexados" narra que a primeira versão errou e conclui que ficam os três; o título tinha ficado na versão anterior à correção. Ajustado para "três labels" — a narrativa do erro continua no corpo, que é onde ela vale, e esta nota registra o ajuste em vez de deixá-lo silencioso.

