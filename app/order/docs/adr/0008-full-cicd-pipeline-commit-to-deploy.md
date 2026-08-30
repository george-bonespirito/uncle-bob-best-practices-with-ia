# Pipeline commit→deploy completo: Woodpecker builda e faz bump do manifesto de infra

ADR raiz 0007 (revisada) decidiu Codeberg Container Registry + Woodpecker CI com runner compartilhado, mas não fechou o laço inteiro: ArgoCD reage a mudança de **Git**, não fica de olho no registry — faltava decidir quem conecta "imagem nova publicada" a "ArgoCD sabe que precisa atualizar".

**Fato verificado antes de decidir:** pull de imagem no Codeberg Container Registry exige autenticação sempre, mesmo pra repositório público — testado via `curl` no endpoint de token com escopo de pull anônimo; o token retornado tem `Scope: ""` (vazio), sem permissão real concedida. Confirma que o cluster precisa de `imagePullSecret` com credencial real, não dá pra contar com pull anônimo.

Decidimos o pipeline completo:

1. Commit em `main` (ou PR mergeado) no repo `uncle-bob-best-practices-with-ia`.
2. Woodpecker CI dispara, runner compartilhado do Codeberg (ADR raiz 0007). Roda `./gradlew check` (gate de qualidade — Detekt/JaCoCo/ArchUnit/Cucumber) como pré-condição do build.
3. Builda a imagem Docker do MS Order, **tag = SHA do commit** — não `latest`. Motivo: `latest` é tag mutável, quebraria a garantia "rollback = revert + sync" que já é princípio da própria ADR-001 do repo de infra (reverter um commit do manifesto só rasteia de volta pra uma imagem específica e determinística se a tag identifica um build exato).
4. `docker push` pro Codeberg Container Registry, autenticado com credencial de CI (token/deploy key guardado como secret no Woodpecker deste repo).
5. **O mesmo pipeline Woodpecker** clona `bonespirito-cluster-k3s-infra`, edita a tag da imagem no manifesto do MS Order, commita e dá push nesse repo. Exige uma segunda credencial de CI — deploy key/token com escrita no repo de infra, guardado como secret separado no Woodpecker. Aceitável: é o mesmo usuário controlando os dois repos, não é fronteira de confiança entre times.
6. ArgoCD (auto-sync + selfHeal já habilitados, ADR-001 do repo de infra) detecta o commit no repo de infra e sincroniza sozinho, sem intervenção manual.
7. kubelet do cluster puxa a imagem do Codeberg Container Registry usando o `imagePullSecret` (Secret criado fora do Git, mesmo padrão já estabelecido — nunca versionado, manifesto só referencia).
8. Pod novo sobe; ArgoCD reporta `Synced`/`Healthy` pra essa Application.

Alternativa rejeitada: **ArgoCD Image Updater** — componente separado que ficaria de olho no registry e faria o bump do manifesto sozinho, sem o pipeline precisar de credencial cross-repo. Rejeitado pelo mesmo raciocínio que já descartou o registry self-hosted na ADR raiz 0007: mais um componente pra manter no cluster sem necessidade real, quando o pipeline já resolve isso com uma credencial a mais em vez de uma peça de infra a mais.

Nota sobre o que não foi resolvido nesta rodada: o `.woodpecker.yml` em si (sintaxe exata dos steps) e qual arquivo específico do repo de infra recebe o bump da tag (novo `apps/order-app.yaml` + `bases/order/` ainda não existem lá) ficam pra quando a implementação do MS Order começar de verdade.

Status: accepted
