# CLAUDE.md

## Dois territórios

Sem relação entre si. A árvore do git não deixa isso óbvio.

- `doc/transcripts/` — transcrição ASR + `build.mjs`. Pipeline: `doc/transcripts/CLAUDE.md` (carrega ao trabalhar lá).
- `app/` — Order Processing. **Raiz do domínio é `app/`, não a raiz do git.** Como skills devem ler isso: `docs/agents/domain.md`.

## Retomar `app/`

1. [`app/docs/specs/order-processing-prd.md`](app/docs/specs/order-processing-prd.md) seção 7 ("Partes cinzentas") — tracker vivo.
2. [`app/CONTEXT-MAP.md`](app/CONTEXT-MAP.md) — os 9 contextos e suas relações. Cada um tem glossário em `app/<contexto>/CONTEXT.md`: linguagem ubíqua e termos a evitar, incluindo os que **não existem** e estão listados para não serem assumidos. Detalhe em `docs/agents/domain.md`.

```
app/
├── CONTEXT-MAP.md                            os 9 contextos e suas relações
├── docs/{adr,specs,diagrams}/                decisões e specs de sistema inteiro; C4 em .drawio.svg editáveis
├── <contexto>/CONTEXT.md                     glossário do contexto
├── <contexto>/docs/adr/                      decisões locais ao contexto
├── <contexto>/src/test/resources/features/   Gherkin de aceitação
└── settings.gradle.kts                       módulos com código hoje: :order e :notifications
```

## Convenções que o modelo não deriva

Além do perfil global (`~/.claude/CLAUDE.md`):

- **Diagrama no mesmo PR que a decisão.** Spec, ADR ou `CONTEXT.md` que muda sem o `.drawio.svg` correspondente deixa o diagrama mentindo — SVG ninguém revisa. Os de `app/docs/diagrams/` são XML editável (rótulos no SVG). Hook `.claude/hooks/diagram-guard.py` lista o que ficou para trás ao fim da sessão; **avisa, não bloqueia**.
- **ADR revertida não é apagada** — fica com nota do porquê. Revisão de ADR existente **anexa** no fim, não reescreve. Ex.: `order/0009`, `order/0010`. Skill: `adr-new`.
- **Infra não mora aqui.** Cluster (`bonespirito-cluster-k3s-infra`) é outro repo. Sessões paralelas e o barramento: `docs/agents/multi-session.md`. Skill: `infra-relay`.

## Tracker

Codeberg Issues, repo **público**: GET anônimo; escrita exige token. Transporte: Forgejo REST. O `tea` CLI **não é mais usado**. Labels do mattpocock, **sem renomear**. Ver `docs/agents/issue-tracker.md` e `docs/agents/triage-labels.md`. Skill: `issue-ops`.
