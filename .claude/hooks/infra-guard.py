#!/usr/bin/env python3
"""PreToolUse(Bash): bloqueia escrita git no repo de infra (dominio do claude-infra).

Decide por caminho resolvido + subcomando, nao por substring do nome do repo.
Falha ABERTA de proposito: qualquer erro aqui vira "sem opiniao", nunca trava o Bash.
Erros vao pra ~/.claude/hook-errors.log (nao pra /dev/null) pra falha nao ser silenciosa.

LIMITE CONHECIDO: escrita feita por dentro de um script externo (`bash deploy.sh`)
nao e detectada -- resolver exigiria executar o script, e um guard que executa o que
deveria vigiar e pior que o furo. Cobertura real: comandos `git` visiveis no comando.
"""
import json, os, shlex, subprocess, sys, glob as globmod

PROTECTED = "/home/torgge/bonespirito-cluster-k3s-infra"

# subcomandos que escrevem no repo. leitura (log, show, diff, status, ...) nao entra aqui.
WRITE = {
    "commit", "push", "add", "rm", "mv", "merge", "rebase", "reset", "revert",
    "cherry-pick", "am", "apply", "restore", "switch", "checkout", "tag",
    "stash", "clean", "gc", "prune", "update-ref", "filter-branch", "notes",
    "submodule", "worktree", "config", "remote",
}
SEPS = ("&&", "||", ";", "|", "\n")


def resolve(path, cwd):
    """expande ~, $VAR e glob; devolve caminho absoluto real ou None."""
    p = os.path.expanduser(os.path.expandvars(path))
    if not os.path.isabs(p):
        p = os.path.join(cwd, p)
    hits = globmod.glob(p)
    if hits:
        p = hits[0]
    return os.path.realpath(p)


def toplevel(path):
    """raiz do repo git que contem path, ou None."""
    if not os.path.isdir(path):
        path = os.path.dirname(path)
    r = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        capture_output=True, text=True, cwd=path, timeout=5,
    )
    if r.returncode != 0:
        return None
    return os.path.realpath(r.stdout.strip())


def split_segments(cmd):
    out, buf = [], ""
    i = 0
    while i < len(cmd):
        for s in SEPS:
            if cmd.startswith(s, i):
                out.append(buf); buf = ""; i += len(s); break
        else:
            buf += cmd[i]; i += 1
    out.append(buf)
    return [s.strip() for s in out if s.strip()]


def analyze(cmd, cwd):
    """devolve True se algum segmento escreve no repo protegido."""
    cur = cwd
    for seg in split_segments(cmd):
        try:
            toks = shlex.split(seg)
        except ValueError:
            continue
        if not toks:
            continue
        # rastreia cd dentro da mesma chamada
        if toks[0] == "cd":
            cur = resolve(toks[1], cur) if len(toks) > 1 else os.path.expanduser("~")
            continue
        if os.path.basename(toks[0]) != "git":
            continue
        target, sub = cur, None
        j = 1
        while j < len(toks):
            t = toks[j]
            if t == "-C" and j + 1 < len(toks):
                target = resolve(toks[j + 1], cur); j += 2; continue
            if t in ("-c", "--exec-path", "--git-dir", "--work-tree") and j + 1 < len(toks):
                j += 2; continue
            if t.startswith("-"):
                j += 1; continue
            sub = t; break
        if sub in WRITE and toplevel(target) == os.path.realpath(PROTECTED):
            return True
    return False


def main():
    payload = json.load(sys.stdin)
    cmd = (payload.get("tool_input") or {}).get("command") or ""
    cwd = payload.get("cwd") or os.getcwd()
    if analyze(cmd, cwd):
        print(json.dumps({"hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": (
                "Bloqueado: escrita git no repo de infra (bonespirito-cluster-k3s-infra) "
                "- dominio do claude-infra, nao desta sessao. Relay a proposta via "
                "SendMessage em vez de escrever aqui."
            ),
        }}))


if __name__ == "__main__":
    main()
