---
name: adr-new
description: Cria uma nova ADR seguindo as convenções deste repo — numeração sequencial por contexto, revisão anexada (nunca reescrita), reversão marcada com nota do porquê em vez de apagada. Use quando o usuário pedir pra registrar uma decisão de arquitetura, criar/revisar/reverter uma ADR em qualquer contexto de app/.
---

# adr-new

Convenções deste domínio (`CLAUDE.md` raiz), além do formato ADR padrão:

- **ADR revertida não é apagada** — fica no repo, com nota do porquê a decisão mudou. Ver `app/order/docs/adr/0009-*.md` e `0010-*.md` como exemplo real de reversão.
- **Revisão de ADR existente é anexada, não reescrita** — nota de revisão datada no fim do arquivo, o corpo original permanece intacto.

## Onde vive cada ADR

- Decisão de sistema inteiro, vale pros 9 contextos → `app/docs/adr/NNNN-slug.md`
- Decisão local a um contexto → `app/<contexto>/docs/adr/NNNN-slug.md`

Numeração é **sequencial dentro de cada pasta**, não global. Antes de criar, listar a pasta alvo e pegar o próximo número livre:

```
ls app/docs/adr/ | sort              # sistema inteiro
ls app/order/docs/adr/ | sort        # contexto order
ls app/notifications/docs/adr/ | sort
```

## Passos pra criar uma ADR nova

1. Perguntar/confirmar: decisão é de sistema inteiro ou de um contexto específico? Determina a pasta.
2. Listar a pasta, achar `NNNN` livre (próximo depois do maior existente).
3. Ler pelo menos uma ADR existente na mesma pasta pra seguir a estrutura real do repo (título, "Decisão", "Por que", "O que isso custa", "Alternativas rejeitadas", "Consequências", `Status:` no fim) — não usar um template genérico de fora do repo.
4. Escrever a ADR nova completa, com `Status: proposed` ou `accepted` conforme o caso.
5. Se a ADR nova **reverte** uma anterior: editar a ADR anterior só pra marcar status (ex: `Status: superseded by NNNN`) + acrescentar uma seção final `## Revisão (data)` explicando o porquê — nunca remover ou reescrever o corpo original dela.

## Passos pra revisar uma ADR existente (sem reverter)

1. Ler a ADR inteira primeiro.
2. Acrescentar seção no fim: `## Revisão (YYYY-MM-DD)` com o que mudou e por quê.
3. Nunca editar o corpo acima dessa seção.

## O que não fazer

- Não reescrever/deletar conteúdo de ADR existente pra "atualizar" — isso é o padrão errado que este skill existe pra evitar.
- Não numerar global entre contextos diferentes — cada pasta tem sua própria sequência.
- Não inventar seções que não existem no padrão real do repo — copiar a forma de uma ADR vizinha na mesma pasta.
