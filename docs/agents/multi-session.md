# Sessões múltiplas: identidade, autoridade e trabalho paralelo

Várias sessões de agente trabalham neste projeto ao mesmo tempo, em CLIs diferentes (Claude Code, OpenCode), em máquinas diferentes, sobre dois repositórios: este e `bonespirito-cluster-k3s-infra`. Elas se falam pelo barramento **mcp-bus** — o contrato do servidor está em `mcp-bus/PROTOCOL.md`, no repositório de infra, e **não é repetido aqui**.

Este arquivo é a política do time: quem é quem, quem decide o quê, e como trabalhar em paralelo sem se atropelar. É a fonte única — `AGENTS.md` e a skill `infra-relay` apontam para cá em vez de repetir.

## Identidade e autoridade

**Uma sessão é caracterizada por três coisas: `role`, `kind` e `repo`.**

- **`role`** — a função, enum do servidor em CAIXA-ALTA: `ARCHITECT`, `PRINCIPAL`, `TECH-LEAD`, `TECH-MANAGER`, `PRODUCT-MANAGER` (1 no squad); `DEVELOPER`, `DEVOPS`, `DBA`, `QA` (N). Grafia antiga (`Architect`, `Developer`) recusa.
- **`kind`** — o client: minúscula com hífen (`claude-code`, `opencode`). Aberto; não define autoridade; desempata Manager.
- **`repo`** — o repositório em que ela trabalha. Metadado; **não** é chave de endereço.

**O `session_id` é emitido no `join`:** `{kind}-{ROLE}-{n}` (ex. `claude-code-ARCHITECT-1`). Serve para DM e para o reply. **`to` é `session_id` | `ALL` | Role** — não `(role, repo)`. Contrato: `mcp-bus/PROTOCOL.md` (infra ADR-022).

### A sessão não escolhe a própria autoridade — e continua não escolhendo

A [ADR 0003](../adr/0003-identidade-de-sessao-e-role-mais-repo.md) trocou o **princípio** (sessões ilimitadas, id dinâmico). O **mecanismo** é a ADR-022 da infra: Allow só autoriza o CLI; o `join` declara `kind`/`role`/`repo`; o servidor emite o nome.

O motivo da regra antiga continua inteiro: sessão que escolhe a própria role escolhe a própria autoridade, e isso já aconteceu aqui — uma sessão preencheu o vazio de "qual é a minha role" com uma regra condicional de quatro cláusulas que ela mesma avaliava.

### Exclusividade

| Role | Escopo | Quantos |
|---|---|---|
| `ARCHITECT` | **squad inteiro** | **uma só** — é quem delega às outras roles |
| `PRODUCT-MANAGER` | squad | uma |
| `DEVOPS` | squad | vários, com Manager |
| `DEVELOPER` | squad | vários |
| `QA` | squad | vários |

> **O que garante a exclusividade:** o servidor recusa `join` de uma segunda sessão pedindo role exclusiva enquanto a primeira continuar **membro vivo** — não é só regra do time, é checagem no barramento (confirmado no código da infra, `lib.rs:763-776`, teste `role_exclusiva_recusa_o_segundo`). A role se libera quando quem a detém sai (`leave`), nunca antes de isso. **O sufixo numérico do id emitido (`-1`, `-2`, ...) não indica quantas sessões estão simultaneamente na role** — é um contador de nomes por `(kind, ROLE)` que nunca reusa buraco. Achado real: uma sessão `ARCHITECT` conseguiu `join` e recebeu `claude-code-ARCHITECT-2` porque a `claude-code-ARCHITECT-1` já tinha saído do squad; se ela ainda estivesse dentro, o join teria sido recusado, não renomeado.

### Dois eixos de delegação, e confundi-los custa caro

- **Architect delega _entre_ roles.** É o eixo vertical: dirige o que cada role faz.
- **Manager delega _dentro_ de uma role plural.** É o eixo horizontal: entre dois DEVOPS, quem atende. Determinado por `kind` — `claude-code` acima de `opencode`.

> **Lacuna conhecida:** a ordem de `kind` só conhece `claude-code` e `opencode`. Um terceiro client na mesma role não tem precedência definida — a saída é perguntar a George, não inventar critério.

### Architect delega o quê; DEVOPS decide o como

Architect dirige trabalho e prioridade, inclusive no repositório de infra. **Implementação, ordem técnica e o que entra naquele repo continuam decisão do Manager de DEVOPS.**

A divisão não é cortesia: várias decisões boas vieram da infra recusando ou corrigindo proposta da Architect — a reversão do critério de labels indexados saiu de ela trazer a contagem em vez do argumento, e a máscara de PII em vez do descarte saiu de ela aplicar à segurança um argumento que a Architect tinha usado para log. Quem opera o cluster vê o que quem não o opera não vê.

### Como uma sessão entra (cutover 2026-09-13, ADR-022)

O registro fixo de `session_id` **acabou**. Squad foi zerado.

1. `opencode mcp auth mcp-bus` ou Claude `/mcp` autenticar.
2. George clica **Allow** — só "Autorizar este cliente?".
3. `join(squad=bonespirito, kind=…, role=…, repo=…)` — **sem `session_id`**. O servidor emite, no formato `{kind}-{ROLE}-{n}` (ex.: `claude-code-ARCHITECT-1`).
4. Usar o id emitido em todo `peek`/`send`/`ack` — **e em todo `join` seguinte**.
5. Trabalho de infra: `to: DEVOPS` (fila). DM: o id emitido, lido em `list_sessions`.

Quem entra neste repo: `llm-server` → `role=ARCHITECT`; MacBook que compila → `role=DEVELOPER`. A sessão **declara** isso no `join`; o Allow não escolhe.

> **Só a primeira chamada pode omitir `session_id`.** A partir da segunda, declare o id emitido — omitir de novo devolve `session_id must not be empty`. "Omitir" é modo de uma chamada, não permanente. Achado da sessão de infra ao testar o oposto; a mensagem de erro não distingue "você esqueceu" de "você já tem um".

**Se você perdeu o seu id**, o jeito que funciona hoje é torto: faça um `peek` com um `session_id` qualquer e leia a recusa — `your credential is X` diz quem você é. Funciona porque a conferência de identidade responde com o dono da credencial, mas é **efeito colateral, não contrato**. Um `whoami` foi pedido à infra; até existir, quem automatizar isso está parseando mensagem de erro.

Headless (`claude -p`): token no Vault, rótulo operacional; o `join` também emite o nome.

`claim` é obrigatório quando a Role tem mais de um detentor.

### A divisão segue a máquina, não a preferência

`llm-server` **não tem** JVM, Gradle, Node nem Docker — verificado, os quatro ausentes. A sessão que roda ali não compila, não roda teste, não executa `./gradlew check` nem `node doc/transcripts/build.mjs`. O MacBook tem tudo isso.

Daí a atribuição de papéis — por **máquina**, não por nome de sessão, que hoje é emitido e muda:

- **`ARCHITECT` roda no `llm-server`** — decide e escreve arquitetura, ADR, glossário, `.feature`, estrutura de projeto. Trabalha em documento, que é o que a máquina permite.
- **`DEVELOPER` roda no MacBook** — implementa, compila, roda a pirâmide de qualidade e é a única que pode afirmar que algo passa.

**Consequência que precisa estar escrita:** nada que venha do `ARCHITECT` sobre código em execução é verificado. Ele pode raciocinar sobre o código e não pode testá-lo. Só o Developer fecha "funciona", e só com saída verde na mão — a mesma regra que já valia para build neste projeto.

Esta seção já foi sobre nomes de sessão — qual nome mantinha qual role, e o custo de renomear. **Isso deixou de existir com o id emitido:** o nome não é escolhido, não é estável entre sessões, e não carrega autoridade. A autoridade está na role, e a role é declarada no `join` e conferida contra a credencial.

### Sessão que não está no registro (até o cutover)

Entra no squad **sem role**, se anuncia e pede registro a George.

Ela lê, conversa e é vista no `list_sessions` — o que importa, porque sessão fora do barramento é invisível, e descobrir tarde que ela existe é pior do que vê-la sem papel. Sem role, ela não pega trabalho de fila de role e não fecha decisão.

### Duas sessões na mesma role

Só acontece se George registrar assim de propósito. Nesse caso as duas parear no espírito do XP, e o registro continua sendo a autoridade — o pareamento é desempate, não fonte de poder:

- **Manager** — delega aos outros detentores da mesma role, executa quando julgar melhor, e é **a voz que responde às outras roles**. Quem manda para a role fala com ele, e não precisa saber qual sessão fará o trabalho.
- **Executor** — recebe o que o Manager delega, e **contesta quando vir algo que o Manager não viu**. Manager decide; não monopoliza julgamento.

**O Manager é determinado por `kind`: `claude-code` tem precedência sobre `opencode`.** Decisão de George, registrada também no `PROTOCOL.md` do mcp-bus.

Isso **aposenta o desempate por antiguidade** que este documento trazia antes (menor `joined_at`, empate no menor `session_id`). Dois motivos, e o segundo é o que decide: `joined_at` já se mostrou frágil — a infra quebrou e consertou aquele campo no mesmo dia, e a antiguidade anterior ao `f594f56` mente; e **antiguidade não distingue capacidade, `kind` distingue.** Uma sessão que chegou primeiro não é por isso a que deve decidir.

> **Lacuna conhecida:** a regra não desempata **dois detentores do mesmo `kind`** na mesma role — hoje não acontece, mas aconteceria com duas sessões claude-code na mesma role. Nesse caso não há Manager definido, e a saída é perguntar a George em vez de inventar critério.

George sobrepõe a atribuição quando quiser — a regra existe para o caso comum.

O termo **driver/navigator**, usado aqui antes, saiu: Manager e Executor dizem a mesma coisa com a autoridade explícita, e "driver" sugeria rodízio, que não é o caso.

### Sair do squad não é desconectar o cliente

São coisas diferentes e já foram confundidas uma vez, com custo real:

- **Desconectar o cliente MCP** encerra a conexão da sessão. A caixa dela continua no barramento, o cursor é preservado, e tudo que chegar espera por ela.
- **`leave`** remove o membro. A caixa deixa de existir, o cursor se perde, e quem voltar entra como novo — recebendo o backlog inteiro. Enquanto está fora, a sessão **não recebe nada e não é avisada disso**: um `send` dela devolve `pending_for`, indistinguível de "o alvo ainda não entrou".

Um pedido de "desconecte as sessões X" é ambíguo entre os dois. Quem executar deve confirmar qual dos dois antes de agir, e **anunciar no squad** depois de remover alguém — a auditoria registra quem removeu, mas ninguém olha auditoria sem motivo.

## Trabalho paralelo

Duas sessões já editaram o mesmo arquivo no mesmo dia sem saber uma da outra. As regras abaixo existem para tornar isso visível cedo.

**Branch:** tipo primeiro, sessão no segundo segmento.

```
docs/architect/protocolo-multi-sessao
feature/developer/bulk-import
```

O segundo segmento é a **role**, não o `session_id` — que hoje é emitido e muda entre sessões, o que o tornaria inútil como marca de dono.

Preserva o filtro por tipo que já existe e mostra o dono em `git branch -r`, antes de qualquer PR ser aberto.

**PR nasce `WIP:`** no título. O Forgejo recusa merge de PR marcado assim — é bloqueio, não sinalização. A sessão tira o prefixo quando termina.

**Só George mergeia.** Uma sessão nunca mergeia, nem o próprio PR nem o de outra. Um PR já foi mergeado com um commit enquanto a sessão dona ainda empurrava outros seis, que ficaram órfãos numa branch com PR fechado.

**Corpo do PR identifica a sessão.** Todas usam o mesmo token do Codeberg, então todo PR, commit e merge sai como `torgge` — sem isso não há como saber quem abriu o quê.

**Não toque na branch de outra sessão.** Se o trabalho colide, fale com ela pelo barramento e reconcilie do seu lado.

## Documentação

Conteúdo num lugar só, ponteiros no resto. Num único dia, três documentos deste repo afirmaram coisas falsas por duplicação que derivou: que o repositório era privado, que `re-join` reentregava o backlog, e que `joined_at` era instável por desenho. Nos três casos existia uma cópia do fato em outro lugar, e as cópias envelheceram em ritmos diferentes.

- Contrato do barramento: `mcp-bus/PROTOCOL.md`, no repo de infra, ao lado do código.
- Política do time: este arquivo.
- Tracker: `docs/agents/issue-tracker.md`.

Uma skill ou `AGENTS.md` **aponta**; não copia.

---

## Nota 2026-09-19: o mcp-bus foi descartado

Anexada, não reescrita — a convenção deste repo.

**O barramento deste time deixou de ser o mcp-bus.** O canal normativo agora é o
repositório `torgge/agent-bus`, decidido na
[ADR-001 de lá](https://codeberg.org/torgge/agent-bus/src/branch/main/docs/adr/ADR-001.md)
(mergeada em `4bd8a8e`): uma outbox NDJSON por papel em git, escritor único por
arquivo, cursor por último `id` visto. A remoção dos recursos do mcp-bus do k3s foi
pedida ao DEVOPS; a execução e a ordem são dele.

**Tudo o que este arquivo diz acima sobre o mecanismo do mcp-bus passa a ser
histórico:** `join`/`peek`/`ack`/`claim`/`leave`, o `session_id` emitido no formato
`{kind}-{ROLE}-{n}`, o backlog do primeiro `join`, o cursor no servidor. Nada disso
existe no canal novo. O `PROTOCOL.md` no repo de infra segue sendo o contrato **do
servidor que sai**, não do canal que fica.

O que **continua valendo**, porque nunca dependeu do transporte:

- A identidade é `role` + `kind` + `repo`, e a sessão não escolhe a própria
  autoridade ([ADR 0003](../adr/0003-identidade-de-sessao-e-role-mais-repo.md)).
- Os dois eixos de delegação: Architect delega entre roles, Manager dentro de uma
  role plural.
- Architect delega o quê; DEVOPS decide o como.
- A divisão segue a máquina: `ARCHITECT` no `llm-server`, que não compila;
  `DEVELOPER` no MacBook, o único que pode afirmar que algo passa.
- Branch, `WIP:` no título, e só George mergeia.

**O que se perde com o descarte, e precisa estar escrito em vez de descoberto
depois:** a exclusividade de role era **checada no servidor** — ele recusava o
`join` de um segundo ARCHITECT enquanto o primeiro fosse membro vivo
(`lib.rs:763-776`, teste `role_exclusiva_recusa_o_segundo`). Arquivo em git não
checa nada: o `from` de uma linha NDJSON é autodeclarado. O `claim`, que serializava
role plural, também não tem equivalente. Até haver runtime no `agent-bus`, a
exclusividade de role é **convenção**, e a atribuição por máquina (llm-server =
ARCHITECT, MacBook = DEVELOPER) é o que a torna conferível — não há árbitro.

Pendências conhecidas, nesta ordem: runtime do cursor no `agent-bus`; reescrita da
skill `infra-relay`, que ainda documenta o transporte antigo; e a remoção dos
recursos no cluster, com o DEVOPS.

**QA (2026-09-19):** nenhuma sessão QA jamais entrou no squad, e ela fica **adiada**
— não há trabalho para ela enquanto 10 dos 11 `.feature` estiverem `@pending` e o
e2e da Saga seguir bloqueado. Quando existir, roda **no MacBook, em worktree
próprio** ([ADR 0005](../adr/0005-sessao-qa-adiada-e-roda-no-macbook.md)): é a única
máquina com JVM e Gradle, e duas sessões no mesmo repo e máquina é o caso da
[ADR 0004](../adr/0004-worktree-isolado-por-sessao.md).
