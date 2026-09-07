# Squash-merge como única estratégia de merge, imposta pelo repo e não pela disciplina

Este é o primeiro ADR de **processo do repositório**, e inaugura `docs/adr/` na raiz. Decisões de domínio continuam em `app/docs/adr/` (sistema) e `app/<context>/docs/adr/` (contexto) — a separação existe porque `app/` é escopo do design de Order Processing, e estratégia de merge não é assunto de domínio.

O `AGENTS.md` já dizia "squash-merge PRs, one subject per PR". Era convenção escrita, não regra imposta: no PR #15 a `main` recebeu um merge commit com três commits intermediários, sem que nada reclamasse. Convenção que depende de alguém lembrar na hora do clique falha exatamente quando o contexto está mais carregado.

Decidimos **desabilitar merge commit e rebase no repositório do Codeberg, deixando squash como único botão disponível**, e definí-lo como estilo padrão (*Settings → Pull Requests*).

## Por que squash, e não histórico completo

Um PR aqui é **um assunto**. Os commits dentro dele são passos de rascunho — "corrige a nota de PR", "renomeia a skill" — que só fazem sentido lidos em sequência, dentro daquele PR. Na `main`, eles viram ruído: `git log` deixa de responder "o que mudou no projeto" e passa a responder "que teclas foram apertadas".

Isso importa mais do que o normal neste repo porque **o histórico é lido por agentes**. Uma sessão nova reconstrói contexto a partir de `git log` — foi literalmente como esta sessão começou. Um log de assuntos é contexto; um log de passos intermediários é ruído que consome janela sem informar.

## Consequência aceita: granularidade some

`git bisect` passa a apontar para o PR inteiro, não para o commit que quebrou. É aceitável enquanto os PRs forem pequenos e monotemáticos — e essa é justamente a outra metade da convenção ("one subject per PR"). **Se os PRs começarem a crescer, o problema não é a estratégia de merge, é o tamanho do PR.**

Como o corpo do squash é montado a partir dos commits da branch, quem abre o PR deve tratar a **descrição do PR** como o texto que vai sobrar — é ela que vira memória do projeto, não as mensagens intermediárias.

## Por que impor no servidor e não documentar mais forte

Documentação já existia e não bastou. Desabilitar os outros botões torna o caminho errado inalcançável em vez de desaconselhado — o mesmo raciocínio que se aplica a qualquer regra que dependa de vigilância humana repetida.

Custo: a mudança é feita na UI do Codeberg, não versionada aqui. Este ADR é o único registro de que ela existe e do porquê; se alguém reabilitar merge commits, nada neste repo detecta.

Status: accepted
