# Padrão de observabilidade: quarentena em vez de recusa, dois labels, PII proibida por nome

A infra entregou log agregado (Alloy + Loki, retenção D-1, ADR-019 do repositório de infra) e apontou o que aquilo **não** entrega: padrão. O Loki é armazenamento; o contrato são as convenções do OTel, e quem as impõe é o Collector no caminho. Trocar Loki por Elastic ou ClickHouse não mudaria nada — o que decide é o que as aplicações são obrigadas a emitir.

Isso é decisão da aplicação, e está aqui. O padrão operável fica em [`observability-standard.md`](../specs/observability-standard.md).

## Quarentena, não recusa

A recomendação da infra era o Collector **recusar** o que viesse sem `service.name`, `service.version` e `deployment.environment` — o argumento sendo que preencher com default silencioso produz lixo bem formatado, que é pior que nada.

O diagnóstico está certo e o remédio não. **Recusar significa perder log exatamente quando algo está mal configurado** — durante um deploy ruim, uma variável de ambiente que não chegou, um serviço novo mal instrumentado. É o momento em que o log vale mais.

Decidimos **desviar para um stream de quarentena**, marcado como inválido. Mantém o incentivo inteiro — o que está fora do padrão não polui o fluxo bom e aparece como erro — sem destruir evidência. Com D-1 e o volume atual, custa um stream.

Descartar dado porque ele está mal formado é a decisão que se lamenta no meio de um incidente.

## Dois labels indexados, não três

A infra propôs indexar `service.name`, `deployment.environment` e `k8s.namespace.name`.

Os dois primeiros são o eixo real de consulta. O terceiro só informa se namespace e ambiente forem coisas diferentes — e enquanto cada ambiente for um namespace, ele duplica informação e paga cardinalidade por nada.

O ônus é de justificar cada label, não de recusá-lo: a própria infra apanhou disso montando a coleta, quando o coletor injetava um label com nome de pod e criaria um stream novo a cada restart. A decisão se revisa no dia em que houver mais de um namespace por ambiente.

## PII proibida por nome, não por princípio

"Não logue dado sensível" não impede nada. A lista impede.

Nossos glossários já definem exatamente o que não pode sair: o `customer_snapshot` carrega nome legal, `Document` (CPF/CNPJ) e `Delivery Address`; o `WhatsApp Phone` vive no Customers e nunca vai para snapshot justamente por ser dado de contato.

O padrão nomeia esses campos e manda referenciar por `customer_id` e `order_id`. A regra vale inclusive em exceção — mensagem de erro que serializa o agregado inteiro é o caminho mais comum do vazamento, e é silencioso, porque ninguém escreveu `log.debug(customer.document)`.

Isso é decisão que só a aplicação pode tomar: a infra não sabe quais campos são pessoais.

## Auditoria não depende do Loki

A retenção D-1 está **provada**, não prometida — a infra verificou que uma janela de 30–48h atrás devolve zero linhas.

Decidimos que **nenhum evento de domínio existe apenas como linha de log**. Mudança de status de Order, falha de compensação e Rejected Order Row já vivem no Postgres, com backup; é lá que sobrevivem.

A alternativa seria aumentar a retenção do que fosse auditoria, e ela inverte a responsabilidade: transformaria uma decisão de domínio ("isto precisa ser reconstituível") num parâmetro de infraestrutura, onde ninguém que conhece o domínio olha.

Consequência que aceitamos: se a resposta a "onde isso fica registrado?" for só o Loki, o desenho está errado, e a correção é no domínio — não na retenção.

## O que não decidimos aqui

Métricas de domínio expostas via OpenTelemetry (gray area #22 do PRD) continuam abertas — profundidade da fila `order_update_event`, taxa de conflito de `version`, contagem de Orders em `AWAITING_MASTER_DATA`. Este padrão diz como emitir; não diz o que medir.

Status: accepted
