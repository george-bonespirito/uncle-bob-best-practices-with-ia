#!/usr/bin/env python3
"""PreToolUse(Edit): avisa ao editar ADR existente pra nao reescrever corpo, so anexar.

Nao bloqueia -- heuristica de "reescrita vs append" nao da pra confirmar com
seguranca so com old_string/new_string do Edit. Fica so como lembrete visivel,
mesmo padrao do lembrete de tea issues create em settings.json.
Falha ABERTA: qualquer erro aqui vira "sem opiniao", nunca trava o Edit.
"""
import json, os, re, sys

ADR_PATH = re.compile(r"app/(docs/adr|[^/]+/docs/adr)/\d+.*\.md$")


def main():
    payload = json.load(sys.stdin)
    file_path = (payload.get("tool_input") or {}).get("file_path") or ""
    if not ADR_PATH.search(file_path.replace(os.sep, "/")):
        return
    if not os.path.isfile(file_path):
        return  # ADR nova, nao existente -- nao e revisao, nada a avisar
    print(json.dumps({
        "systemMessage": (
            "Lembrete: ADR existente e revisada por append, nao reescrita. "
            "Corpo original fica intacto; mudanca de decisao vira secao "
            "'## Revisao (data)' no fim do arquivo. Reversao de decisao "
            "tambem fica no repo, com Status: superseded by NNNN."
        )
    }))


if __name__ == "__main__":
    main()
