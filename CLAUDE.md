# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## O que é este repo

Dois territórios sem relação entre si:

- `doc/transcripts/` — transcrição ASR bruta de uma entrevista (Uncle Bob Martin sobre fundamentos de software na era de IA) e um script Node que a transforma em markdown legível. Detalhes do pipeline em `doc/transcripts/CLAUDE.md` (carrega ao trabalhar lá).
- `app/` — domínio Order Processing, multi-context. See `docs/agents/domain.md` e `app/CONTEXT-MAP.md`.

## Domínio Order Processing (`app/`)

Ponto de entrada: [`app/docs/specs/order-processing-prd.md`](app/docs/specs/order-processing-prd.md) — seção 7 ("Partes cinzentas") é o tracker vivo do que está resolvido vs. pendente, melhor lugar pra retomar trabalho. Depois, [`app/CONTEXT-MAP.md`](app/CONTEXT-MAP.md) lista os 9 bounded contexts e suas relações; cada `app/<contexto>/CONTEXT.md` é o glossário daquele contexto (linguagem ubíqua, termos a evitar) — só existe quando a linguagem foi de fato resolvida (hoje: `order` e `customers`).

```
app/
├── CONTEXT-MAP.md
├── docs/{adr,specs,diagrams}/     decisões e specs de sistema inteiro; diagramas C4 em .drawio.svg editáveis
├── <contexto>/CONTEXT.md          glossário do contexto
├── <contexto>/docs/adr/           decisões locais ao contexto
└── <contexto>/src/test/resources/features/   Gherkin de aceitação
```

Ver `docs/agents/domain.md` para como skills devem consumir esses docs (`app/` é a raiz do domínio, não a raiz do repo git).

Convenções específicas deste domínio, além das do perfil global:
- **ADR revertida não é apagada** — fica no repo com nota do porquê a decisão mudou (ver `order/0009` e `order/0010` como exemplo).
- **Revisão de ADR existente é anexada, não reescrita** — nota de revisão datada no fim do arquivo.
- Infra (k3s on-prem via ArgoCD, Postgres/Kafka/Apicurio) vive em repositório separado (`bonespirito-cluster-k3s-infra`, privado) — decisões de infra não entram aqui.

## Agent skills

### Issue tracker

Codeberg Issues (Forgejo), repo privado — toda operação exige credencial. Dois transportes: `tea` CLI (workstation do George) ou API REST do Forgejo com token (demais ambientes). See `docs/agents/issue-tracker.md`.

### Triage labels

Labels padrão do mattpocock/skills, sem renomear (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Multi-context, escopado em `app/` (não na raiz — raiz também tem `doc/transcripts/`, sem relação com o domínio Order Processing). See `docs/agents/domain.md`.
