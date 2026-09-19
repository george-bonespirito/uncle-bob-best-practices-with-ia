# Cada sessão de agente trabalha em worktree git isolado, não no checkout compartilhado

`multi-session.md` já registra o incidente: duas sessões editaram o mesmo arquivo no mesmo dia sem saber uma da outra. A mitigação hoje é só convenção — branch nomeada `tipo/role/descrição`, e "não toque na branch de outra sessão; se colidir, fale pelo barramento e reconcilie do seu lado". Isso depende de toda sessão lembrar e seguir a regra; nenhum mecanismo impede a colisão, só o aviso depois dela.

A origem desta ADR é externa: avaliação do repositório [`unclebob/swarm-forge`](https://github.com/unclebob/swarm-forge), a pedido do George. Lá, cada role roda em worktree git isolado com sessão tmux própria — nenhuma role escreve no checkout de outra, estruturalmente, não por disciplina.

Decidimos: **cada sessão de agente que edita arquivos neste repo faz isso num worktree git próprio**, não no checkout principal compartilhado entre sessões.

## O que isso resolve

Elimina a classe de erro, não a mitiga. Duas sessões não podem pisar no mesmo arquivo de checkout se cada uma tem o seu — a colisão vira só a de sempre em merge (branches divergentes), que já tem ferramenta pra resolver, em vez de escrita concorrente no mesmo diretório de trabalho.

## O que isso não é

**Não é identidade de sessão.** A [ADR 0003](0003-identidade-de-sessao-e-role-mais-repo.md) decide *quem* uma sessão é (`role` + `kind` + `repo`, endereçado pelo `session_id` emitido no `join`). Esta ADR decide *onde* ela escreve. Os dois eixos são independentes: uma sessão `DEVELOPER` continua se identificando como sempre no mcp-bus; só o diretório em que ela roda `git` muda.

**Não adota o resto do swarm-forge.** O handoff por arquivo (`outbox`/`inbox`/`completed`), o daemon de entrega e a corrente linear de roles (`specifier → coder → refactorer → architect`) não se encaixam aqui: este projeto já tem mcp-bus funcionando para comunicação, e o modelo de delegação é `ARCHITECT` como hub que distribui entre roles plurais (ADR 0003), não uma corrente fixa. Só o isolamento de workspace por agente é extraído — o resto foi avaliado e descartado por não corresponder à topologia já em uso aqui.

## O que esta ADR não decide

**Mecanismo.** Como cada sessão provisiona seu worktree, se `llm-server` e o MacBook precisam do mesmo tratamento (o primeiro só escreve doc/ADR; o segundo compila), como worktrees obsoletos são podados, e se algum equivalente a tmux é necessário aqui — tudo isso fica para quem executa. O harness usado por sessões Claude Code já expõe `EnterWorktree`/`ExitWorktree` como mecanismo candidato; confirmar se serve fica para a implementação, não para este registro.

**Convenção de branch.** `multi-session.md` continua sendo a política de nomeação (`tipo/role/descrição`); isolamento de worktree é ortogonal a isso e não substitui a regra de branch.

## Alternativas rejeitadas

**Manter só convenção (status quo).** Já falhou uma vez de forma documentada; depender de toda sessão lembrar de avisar antes de tocar em arquivo alheio não escala com o número de sessões simultâneas que este squad já tem hoje (quatro, por `list_sessions`).

**Importar o protocolo de handoff completo do swarm-forge.** Substituiria um barramento que já funciona (mcp-bus, com auditoria, exclusividade de role no servidor, backlog) por um mecanismo de arquivo+daemon novo, sem ganho claro sobre o que já está em produção.

Status: proposed

---

## Nota 2026-09-19: a alternativa rejeitada aqui foi parcialmente adotada

Esta ADR rejeitou "importar o protocolo de handoff completo do swarm-forge" com o
argumento de que substituiria **"um barramento que já funciona (mcp-bus, com
auditoria, exclusividade de role no servidor, backlog)"** por arquivo + daemon.

Esse argumento caiu: o mcp-bus foi descartado
([ADR-001 do `agent-bus`](https://codeberg.org/torgge/agent-bus/src/branch/main/docs/adr/ADR-001.md)),
por entrega e ownership — `send` devolvia `sent: true` com a mensagem parada numa
caixa que ninguém abria. O canal novo é justamente handoff por arquivo: uma outbox
NDJSON por papel, em git.

Não é a adoção do swarm-forge inteiro, e a diferença importa. O que entrou foi o
handoff por arquivo com **escritor único por arquivo** e cursor por último `id`
visto. O que continua fora: o daemon de entrega, e a corrente linear de roles
(`specifier → coder → refactorer → architect`) — a topologia daqui segue sendo o
ARCHITECT como hub, não uma corrente fixa.

O que esta ADR decide — worktree isolado por sessão — não muda: é sobre onde a
sessão escreve, não sobre por onde ela fala. Continua `proposed`.
