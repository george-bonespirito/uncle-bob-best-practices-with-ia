# Interfaces para use cases e para todo port de infraestrutura, em todo microsserviço

O walking skeleton (commit `d419ecc`) fixou os guardrails de build antes de qualquer regra de negócio. Antes de escrever a primeira fatia real — bulk import, em MS Order — fica em aberto um ponto de convenção que vale pra todo microsserviço do sistema: como a camada de aplicação fala com infraestrutura (banco, Kafka, HTTP síncrono pra outro contexto) e como o caso de uso fica exposto por dentro do próprio serviço.

## Decisão

**Todo caso de uso da camada de aplicação é uma interface, com uma implementação separada.** Ex.: `ImportOrders` (interface) e `ImportOrdersService` (implementação) — não uma classe concreta direto.

**Todo port de infraestrutura — repository, producer/consumer de Kafka, cliente HTTP síncrono pra outro contexto — é uma interface definida na camada de aplicação/domínio, implementada por um adapter na camada de infra.** Ex.: `OrderRepository` (interface, application) e `JpaOrderRepository` (adapter, infra).

Convenção de nome: a interface carrega o nome do conceito de domínio, sem sufixo de tecnologia. O adapter carrega o sufixo que identifica o mecanismo concreto (`Jpa*`, `Kafka*`, `InMemory*`, o nome do MS externo pra client HTTP). A implementação de use case, quando não há mecanismo concreto pra nomear, leva sufixo `Service`.

## Por que

**Dependency Inversion Principle.** A camada de aplicação declara a abstração de que precisa; infraestrutura implementa contra ela. `ImportOrdersService` depende de `OrderRepository`, nunca de `JpaOrderRepository` ou de `EntityManager` diretamente — trocar Postgres por outra coisa, ou trocar Kafka por outro barramento, não vaza pra dentro da regra de negócio.

**Testabilidade sem infraestrutura real.** Teste de `ImportOrdersService` usa um fake/in-memory de `OrderRepository`, não sobe contexto Spring, não sobe Postgres, não faz mock de framework. Teste de código que depende de um caso de uso usa mock/fake da interface do caso de uso, não da implementação concreta — a interface de use case existe para isso, não só pra simetria com os ports de infra.

**Onde isso já ia doer**: a fatia de staging + `SKIP LOCKED` (próxima da fila) precisa de um `integrationTest` com Postgres real pra validar a corrida — mas o teste de regra de negócio do worker que aplica o evento (a lógica de merge, o que fazer em conflito de `version`) não deveria depender de subir Postgres pra rodar. Sem a interface, não dá pra isolar as duas coisas.

## O que isso custa

Dobra o número de arquivos por operação — uma interface a mais pra cada caso de uso e pra cada port de infra, mesmo quando há só uma implementação hoje e nenhum plano concreto de trocar. É a exceção explícita à regra de simple-first do projeto: convenção adotada por decisão deliberada (DIP + testabilidade), não por antecipação de requisito futuro.

## Alternativas rejeitadas

**Classe concreta direto, sem interface, até que uma segunda implementação exista de fato.** Mais simples, menos arquivo. Rejeitada porque o ganho real não é a segunda implementação (que pode nunca vir) — é o teste da regra de negócio poder rodar sem subir a infraestrutura concreta. Esse ganho existe desde a primeira implementação.

## Consequências

- Package layout de cada microsserviço passa a distinguir port (interface, pacote de aplicação/domínio) de adapter (implementação, pacote de infra) — vale revisar a estrutura de `order` e `notifications` ao começar a fatia de bulk import.
- ArchUnit (`OrderArchitectureTest`, `NotificationsArchitectureTest`) é o lugar natural pra travar essa regra depois que a primeira fatia existir: infra não pode ser referenciada fora do seu próprio pacote de adapter, aplicação só referencia interface.
- Vale para todo os 9 contextos do sistema, não só Order/Notifications.

Status: accepted
