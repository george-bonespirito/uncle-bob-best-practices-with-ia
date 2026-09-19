# A sessão QA fica adiada; quando existir, roda no MacBook em worktree próprio

O barramento novo (`torgge/agent-bus`) tem um canal `qa.ndjson` e a [ADR-001 de lá](https://codeberg.org/torgge/agent-bus/src/branch/main/docs/adr/ADR-001.md) descreve "QA e DevOps no k3s". Ao avisar os papéis sobre o canal novo, duas coisas ficaram evidentes: **nenhuma sessão QA jamais entrou no squad** — o último `list_sessions` antes da queda do mcp-bus mostrava só `ARCHITECT`, `DEVELOPER` e `DEVOPS` — e não estava decidido em que máquina ela rodaria.

## Decisão

**Não levantar sessão QA agora.**

Não é prioridade adiada por falta de tempo; é que não há trabalho para ela. Dos 11 `.feature` do repo, **10 estão `@pending`** e filtrados do run — só `bulk_import.feature` tem implementação atrás. O teste de integração da Saga ponta a ponta está registrado na seção 7 da PRD (item 10) como **bloqueado**, não como "não desenhado": o worker-polling que consome os 7 tópicos de resultado não existe, e desenhar o e2e antes dele seria especulativo.

Uma sessão no barramento sem trabalho não é neutra. Ela aparece no roster, recebe broadcast, e — no desenho antigo — segurava retenção de fila. No desenho novo ela adiciona uma outbox que ninguém escreve e um cursor que ninguém avança.

**Quando existir, roda no MacBook, em worktree próprio.**

A divisão segue a máquina, não a preferência (`docs/agents/multi-session.md`). QA precisa rodar a pirâmide de qualidade para reportar falha com evidência, e isso exige JVM e Gradle:

- **`llm-server` está fora.** Não tem JVM, Gradle, Node nem Docker — os quatro ausentes, verificado. A sessão que roda ali não compila e não testa.
- **k3s está fora.** O cluster roda container, não sessão de agente. Um pod não é onde uma sessão de CLI vive.
- **MacBook é a única máquina com a stack.** É onde o `DEVELOPER` já roda.

Duas sessões na mesma máquina e no mesmo repositório é exatamente o caso que a [ADR 0004](./0004-worktree-isolado-por-sessao.md) trata: **worktree git isolado por sessão**. QA no MacBook sem worktree próprio reintroduz a colisão de checkout que aquela ADR existe para eliminar.

## A alternativa melhor, que está bloqueada

A resposta mais robusta seria "QA roda em qualquer máquina, dentro de um container" — o que tornaria a escolha de máquina irrelevante e liberaria o cluster como opção.

**Ela não está disponível, e a descoberta é desconfortável:** a [ADR raiz 0006](./../../app/docs/adr/0006-dev-loop-and-deployment-topology.md) decidiu que o dev/test loop é `docker compose run --rm app ./gradlew check`, container-first, conectando aos brokers reais do cluster. **Não existe `Dockerfile` nem `compose.yaml` no repositório** — verificado, não deduzido. O loop container-first que aquela ADR declara decidido nunca foi construído, e é justamente o que permitiria QA rodar fora do MacBook.

Registrado como lacuna no tracker. Enquanto não existir, "onde o QA roda" é uma pergunta sobre qual máquina tem JVM instalada, e só há uma.

## Consequências

- O canal `bus/qa.ndjson` existe e fica vazio. Não é defeito: a outbox de escritor único não custa nada parada, e criá-la depois exigiria mudar o protocolo no meio.
- O aviso sobre o canal novo foi enviado ao `qa` de qualquer forma (`architect-000005`), dizendo explicitamente que foi às cegas. Mensagem parada em git não expira — se uma sessão QA entrar um dia, ela acha o histórico.
- **Reportar falha de execução continua impossível para todo mundo menos o `DEVELOPER`.** Isso não é novidade desta ADR, mas passa a estar escrito no lugar em que alguém vai procurar ao planejar QA.
- Se o compose existir, esta decisão deve ser revisitada — a restrição que a sustenta desaparece.

Status: accepted
