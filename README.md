# uncle-bob-best-practices-with-ia

Repositório pessoal de George Bonespirito, hospedado no [Codeberg](https://codeberg.org/torgge/uncle-bob-best-practices-with-ia) (não no GitHub).

Contém **duas coisas sem relação entre si**. Vale saber disso antes de navegar, porque a estrutura de diretórios não deixa óbvio:

| Diretório | O que é |
|---|---|
| `doc/transcripts/` | Transcrição de uma entrevista com Uncle Bob sobre fundamentos de software na era de IA, mais um script Node que limpa o texto bruto da ASR |
| `app/` | Design de um sistema de referência de supply chain — **Order Processing** — nascido daquela entrevista |

## Estado atual

Linguagem dos **9 bounded contexts**: resolvida — cada um tem `app/<contexto>/CONTEXT.md`. Item 5 da seção 7 do PRD, fechado.

Implementação começou em `app/`: Gradle Kotlin DSL (`app/build.gradle.kts`, `app/gradlew`), módulos `:order` e `:notifications` (Kotlin + Gherkin). Os outros 7 contextos ainda são spec/ADR/glossário — sem `src/`.

Não há `Dockerfile` nem `compose.yaml`, então não existe comando de build documentado a partir deste repositório. **Build e teste rodam na máquina do Developer**, que é a única com JVM, Gradle e Node — ver [`docs/agents/multi-session.md`](docs/agents/multi-session.md).

## Como clonar

```bash
# HTTPS
git clone https://codeberg.org/torgge/uncle-bob-best-practices-with-ia.git

# SSH
git clone git@codeberg.org:torgge/uncle-bob-best-practices-with-ia.git
```

Pipeline de transcrição — sem `package.json`, só `node:fs`, idempotente:

```bash
node doc/transcripts/build.mjs
```

Lê `doc/transcripts/transcript-1.txt`, escreve `doc/transcripts/uncle-bob-software-fundamentals-ai.md`.

O Gradle em `app/` existe (`:order`, `:notifications`), e quem o executa é a sessão Developer, no MacBook. A sessão Architect, no `llm-server`, não tem ferramenta de build — então nada que venha dela sobre código em execução está verificado.

## Por onde começar a ler

1. **[`app/docs/specs/order-processing-prd.md`](app/docs/specs/order-processing-prd.md)** — o documento central. A **seção 7 ("Partes cinzentas")** é o tracker vivo do que está resolvido e do que falta; é o melhor lugar para retomar o trabalho depois de um tempo longe.
2. **[`app/CONTEXT-MAP.md`](app/CONTEXT-MAP.md)** — os 9 bounded contexts do sistema e como se relacionam.
3. **`app/<contexto>/CONTEXT.md`** — glossário do contexto (linguagem ubíqua, termos a evitar). Os 9 existem; mapa em `CONTEXT-MAP.md`.

## Como o design é organizado

```
app/
├── CONTEXT-MAP.md              mapa dos 9 contextos
├── docs/{adr,specs,diagrams}/  sistema inteiro; C4 em .drawio.svg
├── <contexto>/CONTEXT.md       glossário — os 9
├── order/                      código Gradle + Gherkin + ADRs locais
└── notifications/              código Gradle + Gherkin + ADRs locais
```

Decisão que vale para o sistema todo vai em `app/docs/adr/`; decisão local a um contexto vai em `app/<contexto>/docs/adr/`. Os diagramas são SVG com o XML do draw.io embutido — abrem no navegador e continuam editáveis no draw.io.

## Convenções

- **Commits:** Conventional Commits com emoji (`📚 docs:`, `🐛 fix:`, `✨ feat:`).
- **Branches:** prefixo por tipo (`docs/`, `feature/`, `fix/`), sempre a partir de `main`. Nunca commit direto em `main`.
- **PRs:** squash-merge, um assunto por PR.
- **ADRs revertidas não são apagadas.** Quando uma decisão é superseded, a ADR original continua no repositório com uma nota explicando o que mudou e por quê — o motivo da mudança de ideia costuma ser mais útil que a decisão final sozinha. As ADRs `order/0009` e `order/0010` são o exemplo vivo disso.
- **Revisão de ADR existente é anexada, não reescrita.** Quando o contexto muda e uma decisão antiga precisa ser lida de outro jeito, entra uma nota de revisão datada no fim do arquivo.

## Infraestrutura

O sistema é desenhado para rodar num cluster k3s on-premises gerenciado por ArgoCD, num repositório separado ([`bonespirito-cluster-k3s-infra`](https://codeberg.org/torgge/bonespirito-cluster-k3s-infra)). Postgres, Kafka e Apicurio Registry já rodam lá; o Conductor ainda não. As decisões de infra vivem naquele repositório, não neste.

## Licença

[MIT](LICENSE).

O conteúdo de `doc/transcripts/` é transcrição de uma entrevista pública de terceiros, incluída aqui como material de estudo; a licença MIT cobre o código e a documentação deste repositório, não a fala transcrita.

## Para agentes

[`CLAUDE.md`](CLAUDE.md) tem as instruções de trabalho neste repositório — arquitetura do pipeline de transcrição, convenções de domínio, e as skills configuradas em `.claude/skills/`.
