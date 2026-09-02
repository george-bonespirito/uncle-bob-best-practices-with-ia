# Cache de existência de cliente em memória no MS Order, não Valkey

A validação de `customer_id` no import ([ADR order/0009](./0009-validate-customer-at-import-degrade-on-outage.md)) põe o MS Customers no caminho de um arquivo de até ~50.000 linhas. Uma consulta por linha é inviável, e mesmo consultando só os IDs distintos por lote, imports sucessivos repetem os mesmos clientes.

Consideramos **Valkey** como cache compartilhado entre instâncias do MS Order, e **rejeitamos**: o ganho não paga um quarto componente stateful no cluster (hoje: Postgres, Kafka, Apicurio). Import não é caminho de request de usuário final — roda algumas vezes por dia, tolera latência, e o dado cacheado é a coisa mais barata que existe (um booleano por `customer_id`). Cache local por instância desperdiça alguma chamada quando o load balancer troca de pod; isso é irrelevante nessa ordem de grandeza.

Decidimos um **`Customer Registry Cache` em memória, local a cada instância do MS Order**, com três restrições que são a parte importante desta decisão:

- **Guarda existência e nada mais.** Nunca nome, documento, endereço ou qualquer dado de cliente — isso é papel do `Snapshot`, que é permanente e vem da Data Enrichment. Um cache com dado de cliente seria uma segunda fonte de verdade divergindo em silêncio.
- **Nunca guarda status.** O cadastro do Customers não tem estado ([`app/customers/CONTEXT.md`](../../../customers/CONTEXT.md) registra `Customer Status` como termo explicitamente inexistente). Cachear um status aqui reintroduziria pela porta dos fundos uma regra de negócio que não pertence a este contexto.
- **Só entrada positiva.** Miss e resposta negativa vão sempre ao MS Customers. Cachear negativo faria cliente recém-cadastrado permanecer invisível ao import por tempo indeterminado — falha silenciosa e difícil de diagnosticar. Entrada positiva não expira: existência não deixa de valer, salvo remoção do cadastro, e essa corrida é coberta pela Customer Enrichment, que valida de verdade.

**Consequência não óbvia:** o glossário do contexto Order proibia a palavra "cache" no termo `Snapshot` justamente porque Snapshot não invalida nem expira. Agora existe um cache literal no mesmo contexto, com semântica oposta. Os dois termos foram escritos lado a lado no `CONTEXT.md` para que a distinção não dependa de quem leu qual arquivo primeiro.

**Reversibilidade:** trocar por Valkey depois é barato se a interface do cache for uma porta do domínio e não uma chamada direta a uma estrutura de dados. Se o volume mudar de ordem de grandeza, revisitar — a decisão aqui é sobre o volume de hoje, não sobre Valkey ser ruim.

Status: accepted
