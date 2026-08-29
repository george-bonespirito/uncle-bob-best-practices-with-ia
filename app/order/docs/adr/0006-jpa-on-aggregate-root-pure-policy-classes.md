# JPA direto em Order/OrderItem; decisões de domínio isoladas em classes puras

O C4 L3 mostrava `Order`/`OrderItem` como "JPA, aggregate root" dentro da camada "Domínio" — mas a regra original do ArchUnit (herdada do plano descartado, Task Manager, nunca reconfirmada pro domínio real) dizia que `domain` não pode depender de `jakarta.persistence`. Nunca resolvido até agora.

Decidimos: `Order`/`OrderItem` levam anotação JPA direto (`@Entity`, `@Id`, `@Version`, `@OneToMany`, `@Embedded` nos snapshots) — sem entidade JPA separada + mapper. Simples, sem boilerplate extra, consistente com a preferência do usuário por layered simples sobre hexagonal/clean sem justificativa real.

Em troca, as áreas de decisão de negócio identificadas como alvo de mutation testing (ADR order/0005) ficam isoladas em classes/funções **sem** import de `jakarta.persistence` nem `org.springframework.*`, em `domain.policy`:

- `OrderStatusTransition` — transições válidas de `Order Status` (área 1) e avaliação de `Hard-blocking Step` (área 2)
- `CompensationPolicy` — decisão de compensar (área 3): dado um step que falhou, precisa de `Compensating Action` ou não
- Detecção de duplicata de `source_order_number` (área 5) — função pura, dado o par `(source_system_code, source_order_number)` contra o conjunto já importado

**Nota honesta sobre a área 4 (conflito de `version`/optimistic locking):** não teve extração limpa pra `domain.policy`. O conflito em si (`0 linhas afetadas` no `UPDATE ... WHERE version=?`) só existe no contexto de uma execução JPA/SQL real — é comportamento mediado pelo framework (`OptimisticLockException`), não lógica de negócio isolável. O que sobra de "decisão pura" ali é só a contagem de tentativas de retry — pequeno demais pra justificar uma classe própria; fica como método no repositório/serviço de aplicação, fora do escopo de `domain.policy`.

**Regra ArchUnit revisada** (substitui a original): não é mais "`domain` inteiro não depende de JPA/Spring" — é `noClasses().that().resideInAPackage("..domain.policy..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")`, escopada só ao pacote de decisões puras.

Alternativa rejeitada: manter a regra original, `Order`/`OrderItem` como classe de domínio pura + `OrderJpaEntity` separada + mapper. Mais fiel a hexagonal, mas sem justificativa real neste projeto (não há múltiplos backends de persistência, não há reuso do domínio fora deste MS) — só adicionaria boilerplate.

Status: accepted
