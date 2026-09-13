# George atribui `session_id` e role das sessões; a sessão não escolhe nem infere

O projeto passou a ser trabalhado por várias sessões de agente ao mesmo tempo — Claude Code e OpenCode, em máquinas diferentes, sobre este repositório e o de infra — coordenadas pelo barramento mcp-bus.

O barramento aceita **`session_id` autodeclarado**: quem chama `join` escolhe o próprio nome e a própria role. É uma decisão de desenho do servidor (não há identidade por sessão; o token é único para o barramento inteiro), e não vai mudar tão cedo.

Decidimos que, **neste projeto, `session_id` e role são atribuídos por George e registrados em `docs/agents/multi-session.md`**. A sessão lê a linha dela e obedece; não escolhe, não infere a partir do ambiente, e não deduz autoridade do roster.

## O que motivou

Dois incidentes no mesmo dia, ambos consequência de o vazio ser preenchido pela própria sessão:

Uma janela entrou no squad como `infra` por engano. A caixa ficou órfã — ninguém a lia — e mensagens endereçadas à infra real pararam ali, incluindo o aviso de um PR. Só apareceu quando um `send` voltou com `pending_for` para um nome que já não existia.

Uma sessão foi corrigida duas vezes sobre qual era a sua role e, na segunda correção, transformou a regra numa condicional de quatro cláusulas que ela mesma avaliava — inclusive a cláusula "não existir outro Architect". Ou seja, a sessão passou a decidir se tinha direito à própria autoridade, consultando um roster que muda no meio da conversa.

Nenhum dos dois foi erro de execução. Os dois foram a mesma lacuna: **ninguém tinha decidido de quem era a decisão.**

## Por que atribuição humana, e não uma regra derivada do ambiente

A alternativa natural era derivar identidade do ambiente — `host` + repo + CLI. Ela quebra no caso real deste projeto: duas sessões no mesmo host, mesmo repositório e mesmo CLI é exatamente a configuração que já existe. Regra derivada só é determinística enquanto o ambiente for discriminante, e o nosso não é.

A outra alternativa era deixar a sessão escolher e resolver conflito depois. É o estado que produziu os dois incidentes.

## Consequência aceita: fricção na abertura de sessão

Uma janela nova não tem role até George registrá-la, e isso vai acontecer fora de hora — sessões são abertas quando são necessárias, não quando o registro está pronto.

Aceitamos que a sessão não registrada **entre no barramento sem role**, se anuncie e peça registro. Ela lê, conversa e aparece no `list_sessions`; não pega trabalho de fila de role nem fecha decisão. Preferimos isso a mantê-la fora do barramento: sessão fora é invisível, e descobrir tarde que ela existe foi outro problema do mesmo dia.

## Efeito colateral desejado

Nome atribuído torna renomeação silenciosa impossível. Uma sessão que não está no registro não tem nome válido, e isso é visível no roster em vez de virar caixa órfã. Resolve pelo lado barato — política, não código — um defeito conhecido do barramento: identidade autodeclarada e mutável.

Não resolve o defeito irmão: como todas as sessões empurram para o Codeberg com o mesmo token, todo PR sai como `torgge`. A mitigação é convenção (dono no nome da branch e no corpo do PR), está em `docs/agents/multi-session.md`, e é deliberadamente mais fraca — identidade real por sessão no Codeberg exigiria multiplicar credenciais, o que tem custo próprio.

Status: superseded in part by 0003

---

## Revisão — 2026-09-13

A [ADR 0003](0003-identidade-de-sessao-e-role-mais-repo.md) substitui o **mecanismo** desta decisão, não o princípio.

O que sai: George mantendo à mão uma tabela de `session_id` e role. O `session_id` passa a ser gerado por sessão, e a identidade de trabalho vira `role` + `kind` + `repo`.

**O que fica, e é o que importava aqui: a sessão não decide a própria autoridade.** Antes isso era garantido por uma tabela escrita antes; agora é garantido pelo servidor propor a role por regra e um humano confirmar na tela do Allow.

Os dois incidentes que motivaram esta ADR continuam cobertos pela 0003. A caixa órfã não se repete porque nome deixou de ser endereço — endereçar é por `(role, repo)`. E a cláusula "não existir outro Architect", que uma sessão chegou a avaliar sozinha, passa a ser avaliada pelo servidor, que é quem tem o roster.

Esta ADR continua valendo **até o cutover** do mecanismo novo.

