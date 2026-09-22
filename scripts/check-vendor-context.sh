#!/bin/sh
# Guarda do padrão da ADR-005: nenhum arquivo de contexto de vendor, e nenhuma
# referência viva a um.
#
# UMA implementação, dois gatilhos. O hook de pre-commit chama --staged; o
# pipeline (.woodpecker/vendor-context.yaml) chama --all. Duplicar a regra em
# YAML recriaria exatamente a doença que a ADR-005 trata.
#
# FONTE CANÔNICA: esta cópia, no agent-bus. Os repos filhos carregam cópias
# executáveis porque cada um roda sozinho no seu clone, e não há pacote
# compartilhado entre três repos independentes — a cópia é inevitável, envelhecer
# em silêncio não é. Divergindo, esta ganha. Nada confere isso automaticamente:
# um check das cópias seria um quarto lugar com a mesma regra (agent-bus#34).
#
#   --staged   (default) escopo do commit: existência no disco + referência nos
#              arquivos staged. É o modo do hook.
#   --all      repositório inteiro: existência + referência em todo arquivo
#              versionado. É o modo de auditoria e o que um CI chamaria.
#
# Limite que não é conserto de ninguém: `CLAUDE.local.md` não é versionado, então
# **só o modo --staged, rodando na máquina do dev, o alcança**. CI prova o
# repositório, não o disco de alguém (ADR-005, revisão de 2026-09-21).

set -eu
mode="${1:---staged}"
allow=".vendor-context-allow"
fail=0

# Referência em contexto de caminho ou link — nunca a string solta.
ref_re='\]\([^)]*CLAUDE\.md|/CLAUDE\.md|`[^`]*CLAUDE\.md'

isento() {
  # $1 = caminho
  [ -f "$allow" ] && grep -qxF "$1" "$allow" && return 0
  # Mensagem de outbox é registro do que foi dito: um despacho sobre esta
  # migração cita os nomes por necessidade. Isenção estreita, só .ndjson sob bus/.
  case "$1" in bus/*.ndjson) return 0 ;; esac
  return 1
}

echo "  [vendor-context] modo $mode"

# 1. Existência dos três nomes que desligam o AGENTS.md, em qualquer nível.
#    Sempre no disco, nos dois modos: é o que pega o CLAUDE.local.md.
achados=$(find . -path ./.git -prune -o \
  \( -name 'CLAUDE.md' -o -name 'CLAUDE.local.md' \) -type f -print 2>/dev/null || true)
if [ -n "$achados" ]; then
  echo "  [vendor-context] ERRO: arquivo de contexto de vendor presente:"
  echo "$achados" | sed 's/^/    /'
  echo "    O Claude Code varre 4 diretórios ancestrais por CLAUDE.md,"
  echo "    .claude/CLAUDE.md e CLAUDE.local.md e, achando um, IGNORA o AGENTS.md."
  echo "    Apague. Não ignore no git: o arquivo continua no disco, continua"
  echo "    desligando o contexto, e fica invisível para revisão (ADR-005)."
  fail=1
fi

# 2. Referência viva.
case "$mode" in
  --staged) alvos=$(git diff --cached --name-only --diff-filter=ACM || true) ;;
  --all)    alvos=$(git ls-files || true) ;;
  *) echo "  uso: $0 [--staged|--all]" >&2; exit 2 ;;
esac

for f in $alvos; do
  [ -f "$f" ] || continue
  isento "$f" && continue
  hits=$(grep -nE "$ref_re" "$f" 2>/dev/null || true)
  if [ -n "$hits" ]; then
    echo "  [vendor-context] ERRO: referência viva a CLAUDE.md em $f:"
    echo "$hits" | sed 's/^/    /'
    echo "    Aponte para AGENTS.md, ou registre a exceção em $allow com o motivo."
    fail=1
  fi
done

[ "$fail" -eq 0 ] || { echo "  [vendor-context] recusado."; exit 1; }
echo "  [vendor-context] ok."
