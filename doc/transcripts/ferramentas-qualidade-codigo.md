# Ferramentas de qualidade de código citadas no vídeo

> Fonte: "Uncle Bob on Software Fundamentals in the Age of AI" — <https://www.youtube.com/watch?v=zcLPGC-tvgk> (56:39).
> Transcrição completa: `doc/transcripts/uncle-bob-software-fundamentals-ai.md`.

## Ferramentas

1. **CRAP score** — combina cobertura de teste + complexidade ciclomática numa fórmula; pontua "quão ruim" é a função. Threshold humano: <4; para agentes: 6, talvez 8.
2. **Mutation testing** — muta operadores (negação, `<`→`>`, `==`→`!=`), roda a suíte inteira esperando falha; mutante sobrevivente = buraco de teste, precisa morrer.
3. **Architecture viewer** — diagrama UML dos módulos + dependências, clicável, com drill-down até o código. Feito pelos próprios agentes, para verificar estrutura sem ler tudo.
4. **Dependency checker determinístico** — spec file declarando qual módulo pode depender de qual; checker roda no fim, agente corrige violações (inverte dependência, insere interface, quebra módulo).
5. **Gherkin** (given/when/then) — teste de aceitação de alto nível, gerado pelo agente "specifier".
6. **QA procedure** — teste de sistema executável pela UI, do ponto de vista humano.

## Princípio unificador

Ferramentas determinísticas em vez de steering por prompt: não somem no meio da janela de contexto e colocam o agente em loop até conformar.
