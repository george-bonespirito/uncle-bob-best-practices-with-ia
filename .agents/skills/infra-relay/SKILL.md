---
name: infra-relay
description: Protocol for coordinating with the infra session (devops.infra) over the torgge/agent-bus channel — NDJSON outboxes in git, single writer per file, local cursor by last seen id. Load before checking the bus, sending any cross-repo message, or answering questions about that protocol.
user-invocable: false
---

# infra-relay

**Quem é quem, quem decide o quê:** [`docs/agents/multi-session.md`](../../../docs/agents/multi-session.md) e [`docs/adr/0003`](../../../docs/adr/0003-identidade-de-sessao-e-role-mais-repo.md). Se esta skill e o agent-bus divergirem, o agent-bus manda — lá o código (NDJSON) é a verdade compartilhada.

O canal do time é o repositório **[`torgge/agent-bus`](https://codeberg.org/torgge/agent-bus)**: uma outbox NDJSON por papel, em git, escritor único por arquivo, cursor por último `id` visto. Decisão: [ADR-001 do agent-bus](https://codeberg.org/torgge/agent-bus/src/branch/main/docs/adr/ADR-001.md). O transporte anterior (mcp-bus, MCP Streamable HTTP em `mcp.lab.bonespirito.com`) foi descartado em 2026-09-19; o servidor não existe mais e nenhuma ferramenta dele é chamável — `join`/`poll`/`send`/`ack` dessa época são histórico, não instrução.

## Onde estão as coisas

Clones lado a lado no MacBook. De dentro deste repo, o caminho relativo é `../agent-bus`:

- Outboxes: `../agent-bus/bus/<papel>.ndjson` — uma por papel.
- Meu cursor: `../agent-bus/bus/cursors/dev.uncle-bob.json` — **local, não versionado** (está no `.gitignore`). Nasce ausente (= posição zero) na primeira leitura. Não confundir com `cursors/architect.json`, que é dele, no llm-server.
- O Syncthing (ADR-003) replica `bus/` entre MacBook e llm-server **exceto `cursors/`** — é por isso que mensagem nova já está em disco quando alguém manda eu olhar. O que faltava não era infra, era instrução de quando olhar — é o que as seções seguintes cobrem.

## Protocolo (resumo; canônico: `../agent-bus/bus/README.md`)

Uma linha JSON por evento: `id`, `from`, `to`, `type`, `payload`, `ts`.

- `id` = `<papel>-<seq>`, sequência monotônica **por remetente**, emitida por quem escreve — é o que torna o cursor decidível sem relógio comum entre as máquinas.
- `from` e o prefixo do `id` são o nome da outbox em que a linha está. Linha que erra isso é lixo; a já commitada **não se reescreve** — correção é append de errata na própria outbox.
- `to` = nome da outbox do destinatário. O legado `to: devops` é lido pelo `devops.infra`; ninguém novo emite esse alias.
- `type`: `task` | `done` | `blocked` | `report`. Tipo extra só entra com ADR.
- `ts` é informativo, **nunca** critério de ordem.

## Ler

Cursor é o último `id` visto **por arquivo de origem — nunca offset de byte**. Byte offset morre na primeira concorrência real (merge do git move a posição, Syncthing deixa `*.sync-conflict-*` ao lado).

Ler é, em `../agent-bus/bus/`:

1. Para cada outbox alheia, abrir o arquivo, descartar tudo até o `id` registrado para ela no cursor (cursor ausente ou origem ausente = posição zero).
2. Processar o resto filtrando `to` = `dev.uncle-bob` (este endereço recebe do architect e do devops.infra; FYI do devops.infra sobre pushes no repo dele não exige resposta — ver Política abaixo).
3. Só então gravar o novo último `id` de cada origem em `cursors/dev.uncle-bob.json`.

Nunca apagar o cursor de um leitor vivo — ele reprocessa a outbox inteira.

## Escrever

- Escrevo **só** em `bus/dev.uncle-bob.ndjson`, a minha outbox. Nunca no arquivo de outro papel — falar com alguém é appendar **na própria** outbox com `to` preenchido, e quem me lê lê **a minha**.
- Ninguém trunca, reescreve ou remove linha, nem toca arquivo alheio.
- `task` pertence ao `to` até um `done` ou `blocked` com o **mesmo id** — e a resposta sai na minha outbox, não na do architect.
- **Nunca credencial ou segredo no bus.** NDJSON versionado em git não é canal de segredo — o devops.infra já recusou transitar PAT por aqui exatamente por isso.

### Commit e push

Exceção da ADR-002 do agent-bus: append em `bus/*.ndjson` vai **direto para `main`**, sem branch e sem PR — escritor único por arquivo significa que não há conflito pra um PR mediar. Sempre:

1. `git pull` antes do push (o architect e o devops.infra commitam dos clones deles).
2. **Uma mensagem por commit**, id no assunto: `📨 chore(bus): dev.uncle-bob-0000NN — <sumário>`.
3. Nada fora de `bus/*.ndjson` nesse commit — commit misturado perde a exceção.

## Quando olhar o bus

Nada acorda esta sessão — leitura é pull, sempre. Três gatilhos, o 2 é o que paga:

1. **Início de sessão.**
2. **Antes de fechar qualquer decisão com consequência pro outro repo** — escrever ou revisar ADR, abrir PR, finalizar spec. A caixa já pode ter o `blocked` ou a correção que muda o que eu ia registrar.
3. **Antes de mandar mensagem pro outro repo** — a resposta já pode estar na caixa.

## Duas sessões neste repo

Se uma segunda sessão dev aparecer no MacBook, as duas compartilham `dev.uncle-bob.ndjson` como Manager/Executor (`kind` desempata; regra em `multi-session.md`). **Só o Manager appenda** — o Executor não escreve no canal. O endereço é único qualquer que seja o clima de sessão.

## Política (sobrevive íntegra ao descarte do mcp-bus)

**Esta sessão é o único ponto de contato do usuário.** Quando trabalho de infra for preciso:

1. Ler o bus primeiro — a resposta pode já estar na caixa.
2. Mensagem na minha outbox (`to: devops.infra`), enquadrada como vindo do usuário quando veio dele — "George pediu X" — não como iniciativa própria.
3. Reportar de volta quando a resposta chegar. Nunca fabricar nem prever o que a infra vai fazer — esperar a resposta real.

**O architect delega o quê; o devops.infra decide o como.** Direção e prioridade podem ser despachadas, inclusive com `ref` pro repo de infra. Implementação, ordem técnica e o que entra naquele repo ficam com ele. Pedidos de `como` vão como proposta, não como orientação.

**Nunca mandar o usuário falar com a sessão de infra diretamente.** Já foi tentado e corrigido explicitamente. Exceção: ele **pode** falar direto se escolher — é sessão dele também, não é erro pra sinalizar nem desencorajar; se ele mencionar que falou, só sincronizo estado.

**FYI de infra só merece resposta se algo do lado da aplicação depender dele** — e aí vira proposta, como qualquer pedido cross-repo.

## Fronteira de domínio

Nunca escrever ou commitar no repo de infra (`../bonespirito-cluster-k3s-infra`), incluindo o clone local — leitura corrente apenas. Esse repo é domínio do `devops.infra`. Decisão com consequência pra lá sai daqui como proposta; a execução é chamada dele.
