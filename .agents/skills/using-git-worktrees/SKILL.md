---
name: using-git-worktrees
description: Use quando começar trabalho de feature que precisa de isolamento do workspace atual, ou antes de executar planos de implementação — garante um workspace isolado via mecanismo nativo do harness ou fallback git worktree. Base: skill homônima de terceiro (superpowers), adaptada neste repo — vendor-neutra e com ramo Gradle.
user-invocable: false
---

# Using Git Worktrees

Garanta que o trabalho aconteça num workspace isolado. Prefira o mecanismo
nativo do harness se ele existir; caia pro `git worktree` manual só quando não
houver nativo.

**Princípio central:** detecte isolamento existente primeiro. Depois mecanismo
nativo. Depois fallback git. Nunca brigue com o harness.

Neste repo, o motivador é concreto: múltiplas sessões dividem o mesmo clone no
MacBook (`dev.uncle-bob`), e um commit já caiu na branch errada por isso —
ver [ADR 0004](../../../docs/adr/0004-worktree-isolado-por-sessao.md).

**Anuncie no início:** "Estou usando a skill using-git-worktrees pra isolar o workspace."

## Passo 0: Detectar isolamento existente

**Antes de criar qualquer coisa, confira se você JÁ está num workspace isolado.**

```bash
GIT_DIR=$(cd "$(git rev-parse --git-dir)" 2>/dev/null && pwd -P)
GIT_COMMON=$(cd "$(git rev-parse --git-common-dir)" 2>/dev/null && pwd -P)
BRANCH=$(git branch --show-current)
```

**Guarda de submódulo:** `GIT_DIR != GIT_COMMON` também é verdade dentro de
submódulos git. Antes de concluir "já estou num worktree", confira:

```bash
# Se retorna um path, você está num submódulo, não num worktree — trate como repo normal
git rev-parse --show-superproject-working-tree 2>/dev/null
```

**Se `GIT_DIR != GIT_COMMON` (e não for submódulo):** você já está num linked
worktree. Pule pro Passo 2 (Setup do projeto). NÃO crie outro worktree.
Reporte com o estado da branch (em branch, ou detached HEAD gerenciado por
fora — nesse caso a branch só entra no fechamento).

**Se `GIT_DIR == GIT_COMMON` (ou for submódulo):** você está num checkout
normal. Se o usuário ainda não indicou preferência por worktree nas
instruções, peça consentimento antes de criar. Preferência já declarada se
honra sem perguntar; recusa do usuário = trabalhe no lugar e pule pro Passo 2.

## Passo 1: Criar o workspace isolado

**Dois mecanismos, nesta ordem.**

### 1a. Mecanismo nativo do harness (preferido)

Verifique se o harness em que você está rodando já oferece um mecanismo
nativo de criação de worktree — alguma tool, comando ou flag com esse nome ou
efeito. Se existir, use-o e pule pro Passo 2.

Mecanismos nativos cuidam de diretório, criação de branch e cleanup sozinhos.
Usar `git worktree add` quando o harness já oferece um cria estado-fantasma
que o harness não enxerga nem gerencia.

Só prossiga pro 1b se **não houver** mecanismo nativo disponível. O catálogo
de tools do seu harness pode mudar entre sessões — confira de novo, não
presuma ausência de memória.

### 1b. Fallback: git worktree manual

**Use só se o 1a não se aplicar.** Crie o worktree com git puro.

#### Seleção de diretório

Ordem de prioridade — preferência explícita do usuário sempre ganha do estado
do filesystem:

1. **Verifique suas instruções** por uma preferência declarada de diretório de
   worktree. Se existir, use-a sem perguntar.
2. **Diretório project-local existente:**
   ```bash
   ls -d .worktrees 2>/dev/null   # Preferido (hidden)
   ls -d worktrees 2>/dev/null    # Alternativa
   ```
   Se encontrado, use-o. Se ambos existirem, `.worktrees` ganha.
3. **Sem outra orientação:** default `.worktrees/` na raiz do projeto (é o
   default deste repo).

#### Verificação de segurança (diretórios project-local)

**OBRIGATÓRIO: verifique que o diretório é ignorado antes de criar o worktree:**

```bash
git check-ignore -q .worktrees 2>/dev/null || git check-ignore -q worktrees 2>/dev/null
```

**Se NÃO for ignorado:** adicione ao `.gitignore`, commite a mudança, e só
então prossiga.

**Por que é crítico:** impede commitar o conteúdo do worktree no repositório.

#### Criar o worktree

```bash
path="$LOCATION/$BRANCH_NAME"

git worktree add "$path" -b "$BRANCH_NAME"
cd "$path"
```

Neste repo, branch name segue `<tipo>/<role>/<slug>` (ex.:
`docs/developer/using-git-worktrees`).

**Fallback de sandbox:** se `git worktree add` falhar com erro de permissão
(negação de sandbox), avise o usuário que o sandbox bloqueou a criação e que
você segue no diretório corrente. Rode setup e baseline no lugar.

## Passo 2: Setup do projeto

Detecte e rode o setup apropriado:

```bash
# Node.js
if [ -f package.json ]; then npm install; fi

# Rust
if [ -f Cargo.toml ]; then cargo build; fi

# Python
if [ -f requirements.txt ]; then pip install -r requirements.txt; fi
if [ -f pyproject.toml ]; then poetry install; fi

# Go
if [ -f go.mod ]; then go mod download; fi

# Gradle/Kotlin (este repo, app/): container-first, nunca no host
if [ -f gradlew ] || [ -f app/gradlew ] || [ -f app/compose.yaml ]; then
  docker compose -f app/compose.yaml run --rm test   # entra no container Gradle
fi
```

## Passo 3: Verificar baseline limpa

Rode os testes pra garantir workspace limpo antes de tocar em código:

```bash
# Use o comando apropriado ao projeto
npm test / cargo test / pytest / go test ./...

# Gradle/Kotlin (este repo): o serviço `test` do compose já roda
# ./gradlew check --no-daemon dentro do container
docker compose -f app/compose.yaml run --rm test
```

**Se nenhum gerenciador de pacotes foi reconhecido** (nenhum dos arquivos
acima existe no projeto): **anuncie explicitamente que não fez baseline** —
nunca pule em silêncio fingindo que rodou. Baseline ausente é informação do
relatório, não detalhe omitido.

**Se testes falharem:** reporte as falhas e pergunte se prossegue ou
investiga.

**Se testes passarem:** reporte pronto.

### Relatório

```
Worktree pronto em <caminho-completo>
Testes passando (<N> testes, 0 falhas) [ou: baseline não executada — nenhum gerenciador reconhecido]
Pronto pra implementar <feature-name>
```

## Referência rápida

| Situação | Ação |
|----------|------|
| Já em linked worktree | Pula criação (Passo 0) |
| Num submódulo | Trata como repo normal (guarda do Passo 0) |
| Harness tem mecanismo nativo de worktree | Usa ele (Passo 1a) |
| Sem mecanismo nativo | Fallback git worktree (Passo 1b) |
| `.worktrees/` existe | Usa (verifique ignore) |
| `worktrees/` existe | Usa (verifique ignore) |
| Ambos existem | `.worktrees/` ganha |
| Nenhum existe | Confira instruções, senão default `.worktrees/` |
| Diretório não ignorado | Adiciona ao `.gitignore` + commita |
| Erro de permissão ao criar | Fallback sandbox, trabalha no lugar |
| Testes falham no baseline | Reporta falhas + pergunta |
| Sem package.json/Cargo.toml/... | Anuncia "baseline não executada", nunca pula em silêncio |
| Gradle/Kotlin | Container: `docker compose -f app/compose.yaml run --rm test` |

## Racionalizações comuns

| Desculpa | Realidade |
|----------|-----------|
| "Obviamente não estou num worktree, nem preciso checar" | Rode o Passo 0. Isolamento criado pelo harness e submódulos enganam o olho; os comandos de detecção dirimem a dúvida. |
| "`git worktree add` é mais rápido que caçar mecanismo nativo" | O mecanismo nativo é dono de diretório, branch e cleanup. Burlar é o erro nº 1 — cria estado-fantasma que o harness não enxerga nem gerencia. |
| "O diretório de worktree certamente já está ignorado" | Rode `git check-ignore`. Um diretório não ignorado commita a árvore inteira dentro do repo. |
| "Qualquer nome de diretório serve" | Instrução explícita bate diretório project-local existente, que bate o default `.worktrees/`. |
| "O workspace é fresco, baseline fica pra depois" | Baseline suja torna ambígua qualquer falha posterior. Rode agora; prosseguir com falha é decisão do humano. |
