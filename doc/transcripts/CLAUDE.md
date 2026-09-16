# CLAUDE.md — doc/transcripts/

Guia do pipeline de transcrição. Carrega só quando se trabalha sob `doc/transcripts/`.

## Comando

```
node doc/transcripts/build.mjs
```

Lê `doc/transcripts/transcript-1.txt`, escreve `doc/transcripts/uncle-bob-software-fundamentals-ai.md`. Idempotente — pode rodar quantas vezes precisar após editar `build.mjs`.

Não há `package.json`, gerenciador de pacotes, testes ou pipeline de build — `build.mjs` roda direto com Node (sem dependências externas, só `node:fs`).

## Gotchas do pipeline (`doc/transcripts/build.mjs`)

- **`SEG` (atribuição manual de falante) quebra silenciosamente se o transcript de origem mudar** — os índices são posicionais (1-based, na ordem das units no arquivo). Qualquer edição em `transcript-1.txt` que insira/remova uma unit exige revisar `SEG` inteiro.
- **`REPLAC` não é um limpador genérico** — é uma lista de find-and-replace específicas para este transcript; não reutilizar em outro arquivo sem revisão.

## Ao editar `build.mjs`

- Mudanças em `SEG` ou `SECTION` são acopladas ao conteúdo específico de `transcript-1.txt` — não são lógica genérica. Confirme contra o `.txt` bruto ao alterar índices.
- Novas correções de ASR (nomes errados, jargão) vão em `REPLAC`, seguindo o padrão `[regex, substituição]`.
