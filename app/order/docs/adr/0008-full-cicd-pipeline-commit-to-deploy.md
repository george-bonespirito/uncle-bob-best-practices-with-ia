# Pipeline commit→deploy completo: Woodpecker builda e faz bump do manifesto de infra

ADR raiz 0007 (revisada) decidiu Codeberg Container Registry + Woodpecker CI com runner compartilhado, mas não fechou o laço inteiro: ArgoCD reage a mudança de **Git**, não fica de olho no registry — faltava decidir quem conecta "imagem nova publicada" a "ArgoCD sabe que precisa atualizar".

**Atualização 2026-08-30 (correção do `claude-infra`) — fato abaixo não está mais fechado:** o teste original (`curl` no endpoint de token, `Scope: ""` no JWT anônimo) foi reproduzido pelo `claude-infra`, mas com um passo a mais — usou o token anônimo num request real ao endpoint de manifest e comparou: com token anônimo → `404`; sem token nenhum → `401`. Se o `Scope` vazio negasse acesso, a resposta seria `401`/`403`, não `404` (auth aceita, recurso é que não existe ainda — `torgge/ms-order` nunca foi publicado). Não dá pra confirmar em definitivo sem um pacote público real pra testar pull, mas a evidência não sustenta mais "pull sempre exige autenticação". **`imagePullSecret` deixa de ser requisito fechado e vira "a verificar"**: o `ms-order` sobe sem ele; se o pull falhar com `ImagePullBackOff` e erro de autorização, aí sim entra o Secret (via Vault + ExternalSecret, já em produção no repo de infra, minutos pra montar). Separado disso: mesmo que pull anônimo funcione, fica sujeito a rate limit de tráfego não autenticado do Codeberg — motivo diferente (resiliência, não permissão) pra eventualmente autenticar o pull; decisão distinta, não decidida aqui.

Decidimos o pipeline completo:

1. Commit em `main` (ou PR mergeado) no repo `uncle-bob-best-practices-with-ia`.
2. Woodpecker CI dispara, runner compartilhado do Codeberg (ADR raiz 0007). Roda `./gradlew check` (gate de qualidade — Detekt/JaCoCo/ArchUnit/Cucumber) como pré-condição do build.
3. Builda a imagem Docker do MS Order, **tag = SHA do commit** — não `latest`. Motivo: `latest` é tag mutável, quebraria a garantia "rollback = revert + sync" que já é princípio da própria ADR-001 do repo de infra (reverter um commit do manifesto só rasteia de volta pra uma imagem específica e determinística se a tag identifica um build exato).
4. `docker push` pro Codeberg Container Registry, autenticado com credencial de CI (token/deploy key guardado como secret no Woodpecker deste repo).
5. **O mesmo pipeline Woodpecker** clona `bonespirito-cluster-k3s-infra`, edita a tag da imagem no manifesto do MS Order, commita e dá push nesse repo. Exige uma segunda credencial de CI — deploy key/token com escrita no repo de infra, guardado como secret separado no Woodpecker.
6. ArgoCD (auto-sync + selfHeal já habilitados, ADR-001 do repo de infra) detecta o commit no repo de infra e sincroniza sozinho, sem intervenção manual.
7. kubelet do cluster puxa a imagem do Codeberg Container Registry — sem `imagePullSecret` na primeira tentativa (ver nota acima); se falhar por autorização, Secret entra via External Secrets Operator + Vault (mecanismo real do repo de infra, confirmado pelo dono da infra em 2026-08-30).
8. Pod novo sobe; ArgoCD reporta `Synced`/`Healthy` pra essa Application.

**Atualização de 2026-08-30 — passo 5 não está fechado, é proposta, não decisão:** essa ADR documenta a arquitetura do lado da aplicação (MS Order builda e propõe o bump), mas quem escreve no repo de infra é o `claude-infra` (dono declarado da infraestrutura, ver `app/docs/adr/0007-image-registry-and-ci-topology.md` — a fronteira "esta sessão não escreve no repo de infra" é combinada com o usuário). Repassada a proposta, o `claude-infra` levantou 3 pontos reais antes de aceitar o mecanismo do passo 5 como está:

- A credencial de escrita do repo de infra hoje é superdimensionada (token `admin`/`push` em todos os repos do usuário) — não vai adicionar uma segunda credencial ampla sem escopo definido; precisa de algo restrito a este repositório, possivelmente a um caminho.
- Auto-sync + `selfHeal: true` + `prune: false` significa que um commit do CI vira deploy aplicado em ~3 min, sem gate humano — consequência aceitável, mas precisa estar explícita.
- **`ArgoCD Image Updater` está sendo reavaliado como alternativa real** pelo dono da infra (observa o registry, atualiza a tag sozinho, sem dar acesso de escrita ao CI) — não foi descartado do lado da infra como foi descartado aqui.

Mecanismo final do passo 5 (commit direto do CI vs. Image Updater) fica a critério do `claude-infra`, não desta ADR — atualizar aqui quando ele decidir.

**Atualização 2026-08-30 (2ª rodada):** inclinação atual do `claude-infra` — ainda sujeita a mudar quando ele desenhar a ADR-004 formalmente — é **commit direto**, não Image Updater. Motivo dele: Image Updater escreveria no cluster fora do fluxo de Git, e o valor do repo de infra está em toda mudança ser commit auditável; prefere "pagar em credencial" (escopo estreito: só este repo, idealmente só o caminho do manifesto do `ms-order`) a "pagar em rastreabilidade". Também confirmou explicitamente: o token de push da imagem (registry) e a credencial de escrita no repo de infra são **duas credenciais distintas**, uma não substitui a outra.

Requisitos enviados e aceitos pelo `claude-infra`: imagem `ms-order`, pública, token escopado. ADR-004 está na fila dele — não escrita ainda (prioridade de trabalho é decidida pelo George diretamente com ele, não por esta sessão).

**Requisitos concretos pro `claude-infra` (confirmados com o usuário em 2026-08-30):**
- Nome do repositório de imagem: `ms-order` (`codeberg.org/torgge/ms-order`) — convenção `ms-<nome>` pros outros 8 MS futuros, não reaproveita o nome do repo de docs.
- Visibilidade: pública — consistente com o resto do projeto, sem segredo embutido na imagem.
- Identidade de CI: token pessoal novo, escopado só a package write, mesma conta `torgge` — não reaproveita o token superdimensionado já sinalizado como dívida. Sem conta de bot separada (projeto solo).

Nota sobre o que não foi resolvido nesta rodada: o `.woodpecker.yml` em si (sintaxe exata dos steps) e qual arquivo específico do repo de infra recebe o bump da tag (novo `apps/order-app.yaml` + `bases/order/` ainda não existem lá) ficam pra quando a implementação do MS Order começar de verdade.

**Atualização 2026-08-30 (3ª rodada) — ADR-004 fechada pelo `claude-infra`, commit `ad8d16d` em `docs/adr/ADR-004.md` do repo de infra.** Passo 5 confirmado como **commit direto do CI** (não Image Updater) — decisão final, não mais inclinação. Registry self-hosted também foi reavaliado e recusado de novo, agora com dois motivos: mais um stateful num nó único, e circular (cluster guardando as imagens de que ele mesmo depende pra subir); Docker Hub recusado pela lição do catálogo Bitnami sumindo. Deploy contínuo sem gate humano confirmado pelo usuário (push do CI → cluster em ~3 min).

**Credencial de escrita no repo de infra — decisão registrada com ressalva explícita:** o `claude-infra` recomendou **deploy key por repositório** (Codeberg suporta — escrita limitada só a este repo, revogável isoladamente), mas o usuário escolheu **token pessoal**, por simplicidade de uso no Woodpecker. Consequência registrada na ADR-004, não como nota de rodapé: tokens do Codeberg são de conta, não de repositório — essa credencial alcança todos os repositórios do usuário, e passa a ser a segunda assim em circulação (a primeira é a dívida já registrada, token `admin`/`push` amplo). Relevante porque ela vive no **runner compartilhado** do Codeberg — o raio de um vazamento ali não se limita à infra.

**Dois itens que a ADR-004 deixa em aberto e dependem desta sessão, antes do `claude-infra` escrever `bases/ms-order/` + `apps/ms-order-app.yaml`:**
1. Caminho e linha exatos que o CI edita no manifesto — precisa ser inequívoco pro `sed` do pipeline. Sugestão dele: validar o YAML antes do push (`sed` que quebra indentação vira `OutOfSync` com erro de parse, e com deploy contínuo isso chega ao cluster sozinho).
2. Requisitos de runtime do `ms-order`: portas, dependências (Postgres e Kafka, ambos já rodando), variáveis de ambiente, health checks. Sem código do MS Order ainda, isso não existe — fica pendente até a implementação começar.

Status: accepted — arquitetura do lado da aplicação e mecanismo de bump do manifesto (passo 5) ambos fechados. Pendente: caminho exato do bump no manifesto e requisitos de runtime do `ms-order` (bloqueado por falta de código).
