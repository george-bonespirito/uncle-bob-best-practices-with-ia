# A identidade de uma sessão é `(role, repo)`; o servidor propõe e o humano confirma

O barramento exige `session_id` **pré-cadastrado**: um mapa `{session_id: token}` no Vault, e entrar significa alguém editar o Vault e reiniciar o Deployment, porque `env` de Secret não recarrega em pod vivo.

Numa semana, isso custou: uma sessão fora do squad por dias esperando credencial; um token que vazou num `bash -x` e exigiu rotação coordenada em cinco clientes; e uma janela de migração em que qualquer processo com o token compartilhado entrava declarando qualquer `session_id` — foi assim que a role desta sessão virou `Developer` sem autor registrado.

A raiz é uma assimetria: **o mecanismo caro de operar é o que não expira.** O Bearer estático não tem TTL nenhum; o OAuth, que já está no ar no mesmo servidor, tem — code de 5 min, access de 1h, refresh rotativo. Duas ADRs da infra (014 e 018) declaram "expiração automática de credencial" como não-abordado, cada uma por sua vez.

Decidimos: **sessões ilimitadas, `session_id` dinâmico, e identidade de trabalho dada por `role` + `kind` + `repo`.**

## O que identifica uma sessão

- **`role`** — a função: `Architect`, `DEVOPS`, `Developer`, `QA`, `Product`. É o que define autoridade.
- **`kind`** — o client: `claude-code`, `opencode`, `grok`. Não define autoridade; só desempata Manager.
- **`repo`** — escopa a role.

**O `session_id` deixa de identificar.** É gerado, único por sessão, e existe como handle interno. Se muda a cada vez que a sessão abre, não serve de endereço — então **endereçar passa a ser `(role, repo)`**.

## Exclusividade

`Architect` é única **no squad inteiro**: é quem delega às outras roles, e duas fontes de delegação seriam duas prioridades concorrentes. `Product` é exclusiva por repo. `Developer`, `QA` e `DEVOPS` são plurais.

Isso cria **dois eixos de delegação**, e confundi-los é o erro previsível: Architect delega **entre** roles; Manager delega **dentro** de uma role plural, desempatado por `kind`.

## Architect delega o quê; DEVOPS decide o como

Architect dirige trabalho e prioridade, inclusive no repositório de infra. Implementação, ordem técnica e o que entra naquele repo continuam decisão do Manager de DEVOPS.

A divisão não é cortesia. Várias decisões boas desta semana vieram da infra **recusando** proposta da Architect: o critério de labels indexados foi revertido porque ela trouxe a contagem em vez do argumento, e a máscara de PII substituiu o descarte porque ela aplicou à segurança um raciocínio que a Architect tinha usado para log. Quem opera o cluster enxerga o que quem não o opera não enxerga.

## A sessão continua não escolhendo a própria autoridade

A [ADR 0002](0002-george-atribui-identidade-e-role-das-sessoes.md) decidiu que George atribui `session_id` e role, e o motivo era impedir que a sessão escolhesse a própria autoridade. **Esse motivo continua inteiro; o mecanismo é que muda.**

Antes: tabela mantida à mão. Agora: o servidor **propõe** pela regra e um humano **confirma** na tela do `/authorize`.

Os dois incidentes que motivaram a 0002 continuam cobertos. Não há mais caixa a sequestrar por nome, porque nome não é endereço. E a cláusula "não existir outro Architect" — que uma sessão chegou a avaliar sozinha — passa a ser avaliada pelo servidor.

A leitura preguiçosa desta ADR é "agora a sessão escolhe". É o oposto.

## Regra de atribuição

Geral, com exceção por repositório: a primeira sessão recebe a role exclusiva mais alta ainda vaga (`Architect`, depois `Product`); as seguintes recebem `Developer`.

**A regra considera a máquina, não só a ordem de chegada.** A exceção é chaveada por `repo + host`, porque capacidade decide o que uma sessão pode *afirmar*: o `llm-server` não tem JVM, Gradle nem Node, então quem roda ali não compila e não pode dizer que algo passa. Sem isso, a primeira sessão a conectar pegaria Architect mesmo sendo a máquina que compila — e o Architect seria quem executa, com o Developer incapaz de executar.

## Repo é declarado, não provado

O servidor não tem como verificar de onde a sessão roda.

O pedido OAuth não tem campo para o repositório — os parâmetros são fixos pelo client, e Claude Code ou OpenCode não inventam um. Então **o humano escolhe o repo na tela**, e a sessão declara depois, no `join`, o valor derivado do `git remote`. **Divergência entre os dois é erro.**

Daí vem o único valor real da declaração: ela pega **engano**, não má-fé. Quem tem credencial pode declarar qualquer repositório, e a defesa é o humano na tela, que é LAN-only.

## Participação expira com o grant

Não renovou, saiu do squad.

Isso resolve, sem caso especial, um problema que já congelou a retenção do squad duas vezes: a poda descarta só o que **todos** leram, então um membro parado trava tudo. Com sessões efêmeras e ilimitadas, cada sessão morta viraria um freio permanente — a expiração é o que torna "ilimitado" seguro em vez de aspiracional.

E fecha de graça o que as ADRs 014 e 018 da infra declararam como não-abordado nas duas.

## Tomada de role exclusiva

Role exclusiva presa por sessão morta é oferecida na tela do Allow, explicitamente, com registro em auditoria.

Esperar o grant expirar transformaria uma sessão travada em bloqueio de até uma hora. Tomada automática devolveria à sessão a decisão sobre a própria autoridade, que é o que esta ADR e a 0002 impedem.

## Squad continua sendo o grupo de conversa

Repo escopa autoridade; squad delimita quem se fala. Um squad por repositório cortaria a conversa entre repositórios — que é o caso de uso que fez o barramento existir.

## O que esta ADR não decide

**Mecanismo.** Como gerar o `session_id`, como `(role, repo)` vira endereço no protocolo, onde vive a tabela de exceções, como a expiração remove o membro — tudo isso é decisão da infra, no repositório dela.

**Autorização.** Quem pode remover quem continua fora de escopo, como as três ADRs da infra declaram. Misturar autorização com identidade foi o que fez a ADR-017 delas nascer errada e ser revista em horas.

**Quando vale.** A política entra em vigor no cutover. Até lá o registro de `session_id` continua em `docs/agents/multi-session.md` — ADR não é changelog de deploy, e tirar a fonte antes de existir substituta deixaria dias sem nenhuma.

Status: accepted
