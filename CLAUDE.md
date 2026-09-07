# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## O que é este repo

Dois territórios sem relação entre si:

- `doc/transcripts/` — transcrição ASR bruta de uma entrevista (Uncle Bob Martin sobre fundamentos de software na era de IA) e um script Node que a transforma em markdown legível. Detalhes do pipeline em `doc/transcripts/CLAUDE.md` (carrega ao trabalhar lá).
- `app/` — domínio Order Processing, multi-context. See `docs/agents/domain.md` e `app/CONTEXT-MAP.md`.

## Agent skills

### Issue tracker

Codeberg Issues (Forgejo), repo privado — toda operação exige credencial. Transporte único: API REST do Forgejo com token. O `tea` CLI **não é mais usado neste projeto**. See `docs/agents/issue-tracker.md`.

### Triage labels

Labels padrão do mattpocock/skills, sem renomear (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Multi-context, escopado em `app/` (não na raiz — raiz também tem `doc/transcripts/`, sem relação com o domínio Order Processing). See `docs/agents/domain.md`.
