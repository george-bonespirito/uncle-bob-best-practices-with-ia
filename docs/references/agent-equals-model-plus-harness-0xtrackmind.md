# Agent = Model + Harness — @0xTrackmind

**Fonte:** https://x.com/0xtrackmind/status/2113644871529418857  
**Data:** 23 Out 2026  
**Autor:** @0xTrackmind

## Tese central

**Agent = Model + Harness.**

O modelo raciocina e propõe a próxima ação. O *harness* é tudo o mais: o que ele enxerga, quais ferramentas pode usar, o que lembra entre sessões, o que conta como "done", o que é proibido e o que acontece quando algo quebra.

A maioria dos problemas de agentes **não** se resolve reescrevendo o prompt ou trocando de modelo. Se resolve no sistema em volta.

## Diagnóstico rápido — qual camada está quebrada?

| Sintoma | Camada | Ação |
|---------|--------|------|
| Não entendeu o pedido | Prompt | Reescrever instrução |
| Fluente mas errado/desatualizado | Contexto | Dar a informação certa, não mais informação |
| Diz "done" sem prova, faz coisa perigosa, esquece entre sessões | Harness | Falta estrutura ao redor do modelo |
| Fica preso repetindo | Loop | Falta stop condition / mudança de abordagem |
| Vários agentes se atropelam | Graph | Falta design de coordenação |

## Os 7 failure modes + fix de harness

### 1. "Fez um trabalho diferente do que eu pedi"
**Fix:** Contrato antes de começar, com linha **"Done when"** explícita e verificável.

Template curto:
- Goal
- Constraints
- Deliverable
- Done when: (testes passam, evento dispara, etc.)
- Needs approval: (o que toca produção)

### 2. "Perdeu no codebase"
**Fix:** Mapa curto, não manual.

Um arquivo raiz (AGENTS.md / CLAUDE.md) que aponta onde as coisas vivem. O território fica nos arquivos ligados. Contexto é *attention budget*, não storage.

### 3. "Disse que terminou, mas não terminou"
**Fix:** Evidência como gate.

"Done" é outra saída do modelo. Substitua por checks que o ambiente consegue rodar (testes, linter, script que imprime `GATE: PASS`). Separar builder do verifier.

### 4. "Fez algo que eu nunca aprovei"
**Fix:** Permissões fora do modelo.

- Reversível (ler, testar, editar branch): deixa rodar
- Visível pro mundo (deploy, enviar): exige aprovação
- Difícil de reverter (deletar dados, rotacionar credenciais): lane fechada

### 5. "Fica tentando a mesma coisa"
**Fix:** Classificar a falha + cap de retries.

Timeout → pausa. Bad args → repara tool call. Test failure → muda o código, não o teste. Mesma falha 2x sem mudança → stop. Cap de 3 tentativas + orçamento.

### 6. "Esqueceu o que decidimos"
**Fix:** Estado fora da conversa.

Arquivo de estado pequeno: passo atual, decisões e porquê, artefatos, riscos abertos, próxima ação. Nova sessão lê o estado, não re-conta a história.

### 7. "Cometeu o mesmo erro duas vezes"
**Fix:** Transformar lição em infraestrutura.

Explicar → checklist → check automatizado → enforcement. Cada erro recorrente sobe um degrau nessa escada até o ambiente lembrar sozinho.

## Níveis de harness

- **Level 0:** prompt + modelo
- **Level 1:** contrato + mapa curto + tools bem definidos
- **Level 2:** estado persistente + checks reais + loop limitado
- **Level 3:** permissões, traces, recovery, human approval gates

Adicione camada só depois de ver a falha que ela previne. Mantenha o harness menor que o problema que controla.

## Checklist antes de dar autonomia real

- [ ] "Done" definido antes de começar?
- [ ] Consegue achar o contexto certo sem carregar tudo?
- [ ] Toda tool tem propósito claro e mensagem de erro útil?
- [ ] Decisões fora do chat?
- [ ] Conclusão exige evidência?
- [ ] Check feito por algo diferente do builder?
- [ ] Ações arriscadas bloqueadas por política, não por frase?
- [ ] Todo loop tem retry cap e budget?
- [ ] Run resume depois de crash?
- [ ] Cada falha deixa uma regra, teste ou check atrás?

## Alinhamento com este repositório

Este artigo reforça práticas já presentes aqui:

- Mapa de contexto (`AGENTS.md`, `CONTEXT.md` por bounded context)
- Evidência antes de "done" (quality gates, detekt, testes, ArchUnit, Pitest)
- Skills e hooks de enforcement
- Estado e ADRs versionados (não ficam só no chat)
- Multi-sessão e agent-bus para coordenação

Vale ler o thread original — tem templates prontos para colar.
