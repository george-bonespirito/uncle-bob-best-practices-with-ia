# Sessões múltiplas: identidade, autoridade e trabalho paralelo

Várias sessões de agente trabalham neste projeto ao mesmo tempo, em CLIs diferentes (Claude Code, OpenCode), em máquinas diferentes, sobre dois repositórios: este e `bonespirito-cluster-k3s-infra`. Elas se falam pelo barramento **mcp-bus** — o contrato do servidor está em `mcp-bus/PROTOCOL.md`, no repositório de infra, e **não é repetido aqui**.

Este arquivo é a política do time: quem é quem, quem decide o quê, e como trabalhar em paralelo sem se atropelar. É a fonte única — `AGENTS.md` e a skill `infra-relay` apontam para cá em vez de repetir.

## Identidade e autoridade

**Uma sessão é caracterizada por três coisas: `role`, `kind` e `repo`.**

- **`role`** — a função: `Architect`, `DEVOPS`, `Developer`, `QA`, `Product`. É o que define autoridade.
- **`kind`** — o client em que a sessão roda: `claude-code`, `opencode`, `grok`, e o que vier. Não define autoridade; desempata Manager.
- **`repo`** — o repositório em que ela trabalha. Escopa a role.

**O `session_id` não identifica ninguém.** Ele é gerado por sessão, único, e existe só como handle interno — se muda a cada vez que a sessão abre, não serve de endereço. **Endereçar é por `(role, repo)`.**

### A sessão não escolhe a própria autoridade — e continua não escolhendo

A [ADR 0003](../adr/0003-identidade-dinamica-por-role-kind-repo.md) trocou o **mecanismo**, não o princípio. Antes, George mantinha uma tabela de `session_id` e role à mão. Agora o servidor **propõe** a role por regra e um humano **confirma** na tela do `/authorize`.

O motivo da regra antiga continua inteiro: sessão que escolhe a própria role escolhe a própria autoridade, e isso já aconteceu aqui — uma sessão preencheu o vazio de "qual é a minha role" com uma regra condicional de quatro cláusulas que ela mesma avaliava.

### Exclusividade

| Role | Escopo | Quantos |
|---|---|---|
| `Architect` | **squad inteiro** | **uma só** — é quem delega às outras roles |
| `Product` | por repo | uma por repositório |
| `DEVOPS` | por repo | vários, com Manager |
| `Developer` | por repo | vários |
| `QA` | por repo | vários |

### Dois eixos de delegação, e confundi-los custa caro

- **Architect delega _entre_ roles.** É o eixo vertical: dirige o que cada role faz.
- **Manager delega _dentro_ de uma role plural.** É o eixo horizontal: entre dois DEVOPS, quem atende. Determinado por `kind` — `claude-code` acima de `opencode`.

> **Lacuna conhecida:** a ordem de `kind` só conhece `claude-code` e `opencode`. Um terceiro client na mesma role não tem precedência definida — a saída é perguntar a George, não inventar critério.

### Architect delega o quê; DEVOPS decide o como

Architect dirige trabalho e prioridade, inclusive no repositório de infra. **Implementação, ordem técnica e o que entra naquele repo continuam decisão do Manager de DEVOPS.**

A divisão não é cortesia: várias decisões boas vieram da infra recusando ou corrigindo proposta da Architect — a reversão do critério de labels indexados saiu de ela trazer a contagem em vez do argumento, e a máscara de PII em vez do descarte saiu de ela aplicar à segurança um argumento que a Architect tinha usado para log. Quem opera o cluster vê o que quem não o opera não vê.

### Como uma sessão entra

1. O humano escolhe o **repo** na tela do Allow, entre os conhecidos — o pedido OAuth não tem campo para o client declará-lo, e os clients não inventam um. A sessão declara depois, no `join`, o repo derivado do `git remote`; **divergência entre os dois é erro**, e é daí que vem o único valor real da declaração: detectar que o humano clicou no repo errado.
2. O servidor **propõe a role** pela regra: a primeira sessão recebe a role exclusiva mais alta ainda vaga (`Architect`, depois `Product`); as seguintes recebem `Developer`.
3. A regra considera a **máquina**, não só a ordem de chegada. A exceção é chaveada por `repo + host`, porque capacidade decide o que uma sessão pode afirmar: no `uncle-bob-best-practices-with-ia`, sessão no `llm-server` pode ser `Architect`; sessão no MacBook entra como `Developer`, porque é onde o código roda e é a única que pode dizer que algo passa.
4. A tela do `/authorize` mostra role + repo para um humano **confirmar** — e oferece **tomar** uma role exclusiva ocupada por sessão morta, explicitamente, com registro em auditoria.
5. A participação **expira junto com o grant**. Não renovou, saiu do squad.

> **Repo é declarado, não provado.** O servidor não tem como verificar de onde a sessão roda; quem tem credencial pode declarar qualquer repositório. A defesa é o humano na tela do Allow, e a tela é LAN-only. A conferência entre o que o humano escolheu e o que o client derivou não prova nada — pega engano, não má-fé.

### Enquanto o mecanismo não existe

A política acima **vale a partir do cutover**. Até lá, o barramento ainda exige `session_id` pré-cadastrado no Vault, e o registro abaixo continua sendo a fonte — tirá-lo antes deixaria dias sem fonte de verdade nenhuma.

| `session_id` | kind | Role | Repo |
|---|---|---|---|
| `app-design` | claude-code | Architect | `uncle-bob-best-practices-with-ia` |
| `mac-architect` | claude-code | Developer | `uncle-bob-best-practices-with-ia` |
| `infra` | claude-code | DEVOPS | `bonespirito-cluster-k3s-infra` |
| `opencode` | opencode | DEVOPS | `bonespirito-cluster-k3s-infra` |

A convenção de branch usa o `session_id` no segundo segmento (`docs/app-design/slug`). Com id gerado isso deixa de identificar dono — **trocar para a role no cutover**, não antes.

### A divisão segue a máquina, não a preferência

`llm-server` **não tem** JVM, Gradle, Node nem Docker — verificado, os quatro ausentes. `app-design` mora ali, então não pode compilar, rodar teste, executar `./gradlew check` nem `node doc/transcripts/build.mjs`. O MacBook tem tudo isso.

Daí a atribuição de papéis:

- **`app-design` é o Architect** — decide e escreve arquitetura, ADR, glossário, `.feature`, estrutura de projeto. Trabalha em documento, que é o que a máquina dele permite.
- **`mac-architect` é o Developer** — implementa, compila, roda a pirâmide de qualidade e é a única sessão que pode afirmar que algo passa.

**Consequência que precisa estar escrita:** nada que venha do `app-design` sobre código em execução é verificado. Ele pode raciocinar sobre o código e não pode testá-lo. Só o Developer fecha "funciona", e só com saída verde na mão — a mesma regra que já valia para build neste projeto.

O `session_id` `mac-architect` **fica como está**, apesar de a role ter mudado. Renomear custaria uma credencial nova emitida pela infra e deixaria o nome antigo para trás — o problema de sessão órfã que a ADR-0002 existe para evitar. O nome é rótulo; a autoridade está na role, e a role está nesta tabela.

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
docs/app-design/protocolo-multi-sessao
feature/app-opencode/bulk-import
```

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
