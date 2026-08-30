# Codeberg Container Registry + Woodpecker CI com runner compartilhado

ArgoCD referencia uma imagem de container pra rodar o MS Order (ADR raiz 0006) — faltava definir onde essa imagem é publicada e como chega lá.

**Revisado em 2026-08-30** (decisão original tinha registry self-hosted no cluster + agente Woodpecker self-hosted — ver histórico abaixo): verificação direta contra `codeberg.org/v2/` (`curl`, resposta `401` com headers `docker-distribution-api-version: registry/2.0` e `www-authenticate: ...service="container_registry"`) confirmou que o **Codeberg tem Container Registry nativo habilitado** — a incerteza da decisão original está resolvida.

Decidimos: **Codeberg Container Registry** (`codeberg.org/torgge/uncle-bob-best-practices-with-ia` ou nome de imagem equivalente) em vez de registry self-hosted no cluster. Motivo: reaproveita a conta que já usamos pra tudo (mesmo login `torgge`, mesmo `tea`), sem mais um `Application` pra manter/atualizar/monitorar no `bonespirito-cluster-k3s-infra`.

Build e publish via **Woodpecker CI** (integrado ao Codeberg), com **runner compartilhado** (não self-hosted). Motivo: a exigência de agente self-hosted na decisão original era especificamente pra alcançar um registry LAN-only — com o registry público no Codeberg, build (push) e deploy (pull pelo ArgoCD) são conexões de saída em ambas as pontas, nenhuma exposição de rede necessária. Simplifica a infra: um `Application` a menos no cluster (nada de `woodpecker-agent` nem `registry:2`).

Nota sobre o que não foi resolvido nesta rodada: o `.woodpecker.yml` em si (steps concretos — build, push, rodar `./gradlew check`/`pitest` como gate de CI) ainda não foi escrito. Sem código Kotlin/Gradle do MS Order ainda pra ter o que buildar.

Alternativas rejeitadas:
- **Registry self-hosted no cluster** (`registry:2`) — decisão original, revertida: mais um componente pra manter sem necessidade real, agora que o Codeberg resolve isso de graça.
- **Docker Hub** — dependência de conta externa nova; sem vantagem sobre usar o Codeberg, que já é a plataforma canônica deste usuário.
- **Build/push manual do Mac** — mesmo padrão do repo de infra, mas usuário decidiu priorizar CI automatizado desde o início.
- **Agente Woodpecker self-hosted** — decisão original, revertida junto com o registry: só fazia sentido pra alcançar um registry LAN-only que não existe mais neste desenho.

Status: accepted
