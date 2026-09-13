# Sessões múltiplas: identidade, autoridade e trabalho paralelo

Várias sessões de agente trabalham neste projeto ao mesmo tempo, em CLIs diferentes (Claude Code, OpenCode), em máquinas diferentes, sobre dois repositórios: este e `bonespirito-cluster-k3s-infra`. Elas se falam pelo barramento **mcp-bus** — o contrato do servidor está em `mcp-bus/PROTOCOL.md`, no repositório de infra, e **não é repetido aqui**.

Este arquivo é a política do time: quem é quem, quem decide o quê, e como trabalhar em paralelo sem se atropelar. É a fonte única — `AGENTS.md` e a skill `infra-relay` apontam para cá em vez de repetir.

## Identidade e autoridade

**George atribui `session_id` e role. A sessão lê e obedece — nunca infere, nunca escolhe por conta.**

O barramento aceita qualquer `session_id` autodeclarado, então nada no servidor impede uma sessão de se batizar. A regra é nossa, e existe por dois motivos concretos:

- Uma sessão que escolhe o próprio nome pode, sem querer, assumir a caixa de outra. Já aconteceu: uma janela entrou como `infra`, a caixa ficou órfã, e mensagens endereçadas à infra real ficaram paradas onde ninguém lia.
- Uma sessão que escolhe a própria role escolhe a própria autoridade. Também já aconteceu: uma sessão preencheu com uma regra condicional de quatro cláusulas o vazio de "qual é a minha role", avaliando ela mesma se tinha direito a ser Architect.

Nome atribuído torna a renomeação silenciosa impossível: quem não está no registro não tem nome válido, e isso aparece.

### Registro

| `session_id` | CLI | Role | Repo | Observação |
|---|---|---|---|---|
| `app-design` | claude-code | Architect | `uncle-bob-best-practices-with-ia` | host `llm-server`; **sem ferramenta de desenvolvimento** — arquitetura e estrutura, não execução |
| `app-opencode` | opencode | Developer | `uncle-bob-best-practices-with-ia` | janela aberta de propósito por George |
| `infra` | claude-code | DEVOPS | `bonespirito-cluster-k3s-infra` | primária do lado de infra; endereço de DM |
| `opencode` | opencode | DEVOPS | `bonespirito-cluster-k3s-infra` | secundária, defere à `infra` |
| `mac-architect` | claude-code | Developer | `uncle-bob-best-practices-with-ia` | MacBook de George; **é onde o código roda** — tem JVM, Gradle e Node |

Ao entrar no barramento, a sessão usa **exatamente** a linha dela: mesmo `session_id`, mesma role. Se o registro e a realidade divergirem, o registro manda — refaça o `join`.

### A divisão segue a máquina, não a preferência

`llm-server` **não tem** JVM, Gradle, Node nem Docker — verificado, os quatro ausentes. `app-design` mora ali, então não pode compilar, rodar teste, executar `./gradlew check` nem `node doc/transcripts/build.mjs`. O MacBook tem tudo isso.

Daí a atribuição de papéis:

- **`app-design` é o Architect** — decide e escreve arquitetura, ADR, glossário, `.feature`, estrutura de projeto. Trabalha em documento, que é o que a máquina dele permite.
- **`mac-architect` é o Developer** — implementa, compila, roda a pirâmide de qualidade e é a única sessão que pode afirmar que algo passa.

**Consequência que precisa estar escrita:** nada que venha do `app-design` sobre código em execução é verificado. Ele pode raciocinar sobre o código e não pode testá-lo. Só o Developer fecha "funciona", e só com saída verde na mão — a mesma regra que já valia para build neste projeto.

O `session_id` `mac-architect` **fica como está**, apesar de a role ter mudado. Renomear custaria uma credencial nova emitida pela infra e deixaria o nome antigo para trás — o problema de sessão órfã que a ADR-0002 existe para evitar. O nome é rótulo; a autoridade está na role, e a role está nesta tabela.

### Sessão que não está no registro

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

> Hoje há **um único Architect** (`app-design`). A regra de pareamento acima só volta a valer se George registrar duas sessões na mesma role de novo.

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
