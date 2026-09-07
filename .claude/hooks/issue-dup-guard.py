#!/usr/bin/env python3
"""Lembra o check-before-create ao criar uma issue pela API do Forgejo.

Substitui o gatilho antigo, que casava com `tea issues create` — o `tea` nao e
mais usado neste projeto, entao aquele hook nunca mais dispararia e a regra
ficaria sem lembrete.

Dispara em POST para .../issues, e nao em POST para .../issues/<n>/comments:
comentar numa issue existente e justamente o oposto de criar uma duplicata.
"""
import json
import re
import sys

try:
    command = (json.load(sys.stdin).get("tool_input") or {}).get("command") or ""
except Exception:
    sys.exit(0)

is_post = re.search(r"-X\s*POST|--request\s+POST", command) is not None
# `/issues` como fim do caminho — `/issues/12/comments` nao casa.
# Sem exigir a URL literal: a doc usa `$API/issues`, com o host na variavel.
creates_issue = re.search(r"/issues(?![/\w])", command) is not None

if is_post and creates_issue:
    print(json.dumps({
        "systemMessage": "Lembrete: procure uma issue existente antes de criar, pra evitar duplicata.",
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "allow",
            "additionalContext": (
                "Antes de criar esta issue, confirme que nao existe uma similar: "
                "GET $API/issues?state=open&q=<termo relevante> "
                "(ou &labels=wayfinder:map se for um mapa). "
                "q= e substring de titulo e corpo, entao tente mais de um fragmento."
            ),
        },
    }))
