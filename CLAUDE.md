# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## O que é este repo

Repo de conteúdo, não de aplicação. Contém uma transcrição ASR bruta de uma entrevista (Uncle Bob Martin sobre fundamentos de software na era de IA) e um script Node que a transforma em markdown limpo e legível.

Não há `package.json`, gerenciador de pacotes, testes ou pipeline de build — `build.mjs` roda direto com Node (sem dependências externas, só `node:fs`).

## Comando

```
node doc/transcripts/build.mjs
```

Lê `doc/transcripts/transcript-1.txt`, escreve `doc/transcripts/uncle-bob-software-fundamentals-ai.md`. Idempotente — pode rodar quantas vezes precisar após editar `build.mjs`.

## Arquitetura do pipeline (`doc/transcripts/build.mjs`)

O script resolve três problemas do transcript bruto, nessa ordem:

1. **Parse de turnos** — `transcript-1.txt` tem timestamps `[MM:SS]` por linha, com marcador `>>` da ASR indicando troca de falante. Esse marcador é inconsistente/incompleto no source, então o parse por `>>` só produz `units` brutas (blocos de texto por timestamp), não a atribuição final de falante.

2. **Atribuição manual de falante (`SEG`)** — como a ASR erra a troca de turno, a atribuição real é hardcoded no objeto `SEG`, indexado pelo número sequencial da unit (1-based, na ordem em que aparecem no arquivo). Cada entrada é `["H"]` ou `["B"]` (Host / Uncle Bob), opcionalmente seguida de splits internos `["outroFalante", "texto-âncora onde a troca acontece dentro da mesma unit"]` quando a ASR juntou dois falantes num único bloco sem marcar `>>`.
   - **Se o transcript de origem mudar, `SEG` quebra silenciosamente** — os índices são posicionais. Qualquer edição em `transcript-1.txt` que insira/remova uma unit exige revisar `SEG` inteiro.

3. **Limpeza de texto (`clean`, `REPLAC`, `fixNames`, `fixCrap`)** — normaliza erros de ASR (nomes próprios, jargão técnico como "cyclatic" → "cyclomatic", repetições, pontuação solta, `[uh]`/`[um]`). `REPLAC` é uma lista de find-and-replace específicas para este transcript — não é um limpador genérico reutilizável em outro arquivo sem revisão.

4. **Seccionamento (`SECTION`)** — mapeia faixas de tempo (em minutos) para títulos de seção em PT-BR, usado para inserir headers `##` no markdown final conforme o timestamp avança.

Saída final: markdown com header + metadata da fonte, segmentado por seção temporal, falas mescladas quando consecutivas do mesmo falante.

## Ao editar `build.mjs`

- Mudanças em `SEG` ou `SECTION` são acopladas ao conteúdo específico de `transcript-1.txt` — não são lógica genérica. Confirme contra o `.txt` bruto ao alterar índices.
- Novas correções de ASR (nomes errados, jargão) vão em `REPLAC`, seguindo o padrão `[regex, substituição]`.

## Agent skills

### Issue tracker

Codeberg Issues (Forgejo), repo privado — toda operação exige credencial. Dois transportes: `tea` CLI (workstation do George) ou API REST do Forgejo com token (demais ambientes). See `docs/agents/issue-tracker.md`.

### Triage labels

Labels padrão do mattpocock/skills, sem renomear (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Multi-context, escopado em `app/` (não na raiz — raiz também tem `doc/transcripts/`, sem relação com o domínio Order Processing). See `docs/agents/domain.md`.
