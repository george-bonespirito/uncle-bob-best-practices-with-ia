#!/usr/bin/env python3
"""Stop: avisa se spec/ADR/CONTEXT mudaram sem o diagrama correspondente.

Regra de workflow: diagrama e documentacao, nao ilustracao. Spec ou ADR que
muda sem o diagrama deixa o .drawio.svg mentindo, e ninguem revisa SVG.

Nao bloqueia -- so lista o que ficou para tras. Falha ABERTA.
"""
import json, subprocess, sys

# arquivo tocado -> diagramas que provavelmente precisam acompanhar
MAP = [
    ("app/CONTEXT-MAP.md",        ["c4-l1-system-context", "c4-l2-containers"]),
    ("app/docs/specs/",           ["c4-l2-containers", "project-roadmap"]),
    ("app/docs/adr/",             ["c4-l2-containers", "project-roadmap"]),
    ("/CONTEXT.md",               ["c4-l1-system-context", "project-roadmap"]),
    ("app/order/docs/adr/",       ["c4-l3-order-components", "project-roadmap"]),
]


def main():
    try:
        out = subprocess.run(
            ["git", "status", "--porcelain"], capture_output=True, text=True, timeout=5
        ).stdout
    except Exception:
        return
    changed = [l[3:].strip() for l in out.splitlines() if l.strip()]
    if not changed:
        return
    touched_diagrams = {c for c in changed if ".drawio.svg" in c}
    pending = set()
    for path in changed:
        if ".drawio.svg" in path:
            continue
        for prefix, diagrams in MAP:
            if prefix in path:
                for d in diagrams:
                    if not any(d in t for t in touched_diagrams):
                        pending.add(d)
    if not pending:
        return
    print(json.dumps({
        "systemMessage": (
            "Diagramas possivelmente desatualizados por esta mudanca: "
            + ", ".join(sorted(pending))
            + ". Regra de workflow: diagrama acompanha a decisao no mesmo PR. "
            "Se nao se aplica, diga por que no corpo do PR."
        )
    }))


if __name__ == "__main__":
    main()
