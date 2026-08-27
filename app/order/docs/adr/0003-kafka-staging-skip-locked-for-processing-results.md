# Kafka + staging table + SKIP LOCKED para aplicar resultado das Processing Steps

Depois que cada um dos 7 MS de dado termina sua Processing Step (via worker-polling do Orkes, ver ADR raiz 0001), o resultado precisa chegar no MS Order e ser aplicado ao Order correspondente.

Decidimos: os 7 MS publicam o resultado num único tópico Kafka. MS Order roda múltiplas instâncias no mesmo consumer group, consumindo esse tópico — o tópico é deliberadamente **não particionado por `order_id`**, para que mensagens do mesmo Order possam cair em partitions diferentes e ser processadas por instâncias diferentes ao mesmo tempo (Kafka garante exclusividade só dentro de uma partition, não entre partitions — particionar por `order_id` eliminaria a concorrência real que este projeto existe para exercitar).

O consumer de cada instância não aplica o resultado direto no Order — ele só grava a mensagem numa tabela de staging (`order_update_event`), rápido, sem lógica de negócio, liberando o commit do offset Kafka cedo. Um pool de workers em virtual threads, dentro de cada instância, reivindica linhas pendentes de `order_update_event` via `SELECT ... FOR UPDATE SKIP LOCKED` e aplica cada uma no Order com optimistic locking (ADR 0001 do contexto Order).

Isso dá duas camadas de concorrência real, deliberadamente:
1. **Entre instâncias de MS Order**, via partitions do Kafka não alinhadas a `order_id`.
2. **Entre workers dentro de cada instância**, via `SKIP LOCKED` na fila de staging.

Alternativa rejeitada: consumer aplica o resultado direto no Order (só optimistic locking, sem staging). Mais simples — menos uma tabela, menos um pool de workers — mas elimina `SKIP LOCKED` do design inteiro, contrariando o objetivo original do projeto de testar essa cláusula na prática.

Trade-off aceito: mais um componente (tabela de staging + pool de workers) do que uma aplicação direta teria. Aceitável porque testar `SKIP LOCKED` de verdade é um objetivo explícito deste projeto, não um acidente de implementação.

Status: accepted
