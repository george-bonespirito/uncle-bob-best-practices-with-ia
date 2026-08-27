# Dead letter topic por tópico de resultado, para falha de consumo em MS Order

O retry do Conductor (3 tentativas, backoff exponencial — Integration Spec de resultado) cobre falha na **task** de uma Processing Step, e já tem destino definido: falha permanente dispara a Saga de compensação (ADR raiz 0002). Isso não cobre um problema diferente: MS Order falhar ao **consumir/processar** uma mensagem de resultado já publicada (schema inválido, `orderId` inexistente, exceção aplicando no Postgres). Sem tratamento, uma mensagem assim trava a partition inteira, porque o Kafka processa em ordem e não confirma o offset até a mensagem ser processada com sucesso.

Decidimos: cada um dos 7 tópicos de resultado (ADR raiz 0004) ganha um tópico `.dlt` correspondente (ex: `order.stock-validation.result.dlt`). O consumer de MS Order distingue dois tipos de falha:

- **Transitória** (timeout de conexão com Postgres, indisponibilidade momentânea): retry curto, poucas tentativas, backoff curto, antes de desistir.
- **Permanente** (schema Avro inválido, `orderId` que não existe no Order): vai direto pro DLT correspondente, sem retry — repetir a mesma mensagem malformada não muda o resultado.

Resolução de mensagem no DLT fica manual, fora de escopo automatizar nesta rodada — mesmo padrão já aceito para o estado `COMPENSATION_FAILED` (ADR raiz 0002): o projeto aceita estados terminais que exigem intervenção humana, em vez de desenhar reprocessamento automático para casos que deveriam ser raros.

Alternativa rejeitada: sem DLT, só logar e pular a mensagem. Mais simples, mas perde a mensagem — sem DLT não há como investigar ou reprocessar depois de corrigido o bug/dado.

Status: accepted
