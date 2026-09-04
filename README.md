# uncle-bob-best-practices-with-ia

Repositório pessoal de George Bonespirito, hospedado no [Codeberg](https://codeberg.org/torgge/uncle-bob-best-practices-with-ia) (não no GitHub).

Contém **duas coisas sem relação entre si**. Vale saber disso antes de navegar, porque a estrutura de diretórios não deixa óbvio:

| Diretório | O que é |
|---|---|
| `doc/transcripts/` | Transcrição de uma entrevista com Uncle Bob sobre fundamentos de software na era de IA, mais um script Node que limpa o texto bruto da ASR |
| `app/` | Design de um sistema de referência de supply chain — **Order Processing** — nascido daquela entrevista |

## Estado atual: não há código de aplicação

`app/` é **documentação e especificação**, não implementação. Nenhum serviço foi escrito ainda: não há `build.gradle.kts`, não há Kotlin, não há `Dockerfile`. Os arquivos `.feature` são contratos de aceitação escritos antes do código, sem nada por trás deles ainda.

Isso é deliberado — o projeto está na fase de design (spec antes de implementação), e a decisão de limitar esta rodada a design está registrada no PRD.

## Como clonar

```bash
# HTTPS
git clone https://codeberg.org/torgge/uncle-bob-best-practices-with-ia.git

# SSH
git clone git@codeberg.org:torgge/uncle-bob-best-practices-with-ia.git
```

Não há dependências para instalar. O único comando executável do repositório é:

```bash
node doc/transcripts/build.mjs
```

Ele lê `doc/transcripts/transcript-1.txt` e escreve `doc/transcripts/uncle-bob-software-fundamentals-ai.md`. Sem `package.json`, sem npm install — só `node:fs`. É idempotente.

## Por onde começar a ler

1. **[`app/docs/specs/order-processing-prd.md`](app/docs/specs/order-processing-prd.md)** — o documento central. A **seção 7 ("Partes cinzentas")** é o tracker vivo do que está resolvido e do que falta; é o melhor lugar para retomar o trabalho depois de um tempo longe.
2. **[`app/CONTEXT-MAP.md`](app/CONTEXT-MAP.md)** — os 9 bounded contexts do sistema e como se relacionam.
3. **`app/<contexto>/CONTEXT.md`** — o glossário de cada contexto: a linguagem ubíqua, com os termos a evitar. Só é escrito quando a linguagem daquele contexto foi de fato resolvida.

### O que já tem linguagem resolvida

Dois dos nove contextos: **[Order](app/order/CONTEXT.md)** (o agregado central, o import em massa, a concorrência) e **[Customers](app/customers/CONTEXT.md)**. **Products é o próximo da fila.** Os demais — Pricing, Stock, Contabil, Invoice, Route, Notifications — ainda não têm `CONTEXT.md`, e isso é rastreado como item #5 da seção 7 do PRD.

## Como o design é organizado

```
app/
├── CONTEXT-MAP.md              mapa dos 9 contextos e suas relações
├── docs/
│   ├── adr/                    decisões que valem para o sistema inteiro
│   ├── specs/                  PRD e contratos de integração
│   └── diagrams/               C4 (L1/L2/L3), roadmap, pipeline CI/CD — .drawio.svg editáveis
├── order/
│   ├── CONTEXT.md              glossário do contexto
│   ├── docs/adr/               decisões locais a este contexto
│   └── src/test/resources/features/    Gherkin de aceitação
├── customers/CONTEXT.md
└── notifications/
    ├── docs/adr/
    └── src/test/resources/features/
```

Decisão que vale para o sistema todo vai em `app/docs/adr/`; decisão local a um contexto vai em `app/<contexto>/docs/adr/`. Os diagramas são SVG com o XML do draw.io embutido — abrem no navegador e continuam editáveis no draw.io.

## Convenções

- **Commits:** Conventional Commits com emoji (`📚 docs:`, `🐛 fix:`, `✨ feat:`).
- **Branches:** prefixo por tipo (`docs/`, `feature/`, `fix/`), sempre a partir de `main`. Nunca commit direto em `main`.
- **PRs:** squash-merge, um assunto por PR.
- **ADRs revertidas não são apagadas.** Quando uma decisão é superseded, a ADR original continua no repositório com uma nota explicando o que mudou e por quê — o motivo da mudança de ideia costuma ser mais útil que a decisão final sozinha. As ADRs `order/0009` e `order/0010` são o exemplo vivo disso.
- **Revisão de ADR existente é anexada, não reescrita.** Quando o contexto muda e uma decisão antiga precisa ser lida de outro jeito, entra uma nota de revisão datada no fim do arquivo.

## Infraestrutura

O sistema é desenhado para rodar num cluster k3s on-premises gerenciado por ArgoCD, num repositório separado (`bonespirito-cluster-k3s-infra`, privado). Postgres, Kafka e Apicurio Registry já rodam lá; o Conductor ainda não. As decisões de infra vivem naquele repositório, não neste.

## Para agentes

[`CLAUDE.md`](CLAUDE.md) tem as instruções de trabalho neste repositório — arquitetura do pipeline de transcrição, convenções de domínio, e as skills configuradas em `.claude/skills/`.
