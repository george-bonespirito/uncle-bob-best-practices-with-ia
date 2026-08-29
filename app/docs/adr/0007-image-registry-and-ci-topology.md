# Registry self-hosted no cluster + Woodpecker CI com agente self-hosted

ArgoCD referencia uma imagem de container pra rodar o MS Order (ADR raiz 0006) — faltava definir onde essa imagem é publicada e como chega lá.

Decidimos: **registry self-hosted no próprio cluster** (`registry:2`, imagem oficial Docker, mais um Application no repo `bonespirito-cluster-k3s-infra`, mesmo padrão App of Apps já usado por Postgres/Kafka/Apicurio). Sem dependência de conta externa (Docker Hub) nem de confirmar se o Codeberg Container Registry está habilitado (não confirmado por pesquisa — ver nota abaixo).

Build e publish da imagem via **Woodpecker CI** (integrado ao Codeberg) — pipeline dispara em commit/tag, builda a imagem, publica no registry. Decisão explícita contra manual (`docker build && push` do Mac), mesmo custando mais setup agora — prioriza automação desde o início em vez de esperar até "ter algo pra automatizar de verdade".

**Amarração de rede resolvida:** o registry só é exposto via NodePort/LAN (mesma decisão de não expor nada publicamente — ADR raiz 0006, WhatsApp). Isso quebraria com o runner compartilhado do Woodpecker no Codeberg, que roda fora da rede do usuário e não alcançaria o registry. Resolvido com **agente Woodpecker self-hosted**, rodando no próprio cluster (mais uma Application no repo de infra) — conecta no servidor Woodpecker do Codeberg por fora (outbound), mas executa o job localmente e alcança o registry pela rede interna, sem precisar expor nada pra fora.

Nota sobre o que não foi resolvido nesta rodada: o `.woodpecker.yml` em si (steps concretos do pipeline — build, push, rodar `./gradlew check`/`pitest` como gate de CI) não foi escrito. Consistente com o resto do projeto: ainda não há código Kotlin/Gradle do MS Order pra ter o que buildar; o pipeline real fica pra quando a implementação começar.

Alternativas rejeitadas:
- **Docker Hub** — mais simples de começar, mas dependência de conta externa e limite de storage/pull no plano gratuito pra repos privados.
- **Codeberg Container Registry** — não confirmado se está habilitado pra esta conta; ficaria como aposta não verificada.
- **Build/push manual do Mac** — mesmo padrão já usado pro repo de infra, mas usuário decidiu priorizar CI automatizado desde o início.
- **Runner compartilhado do Woodpecker + registry público** — mesmo trade-off de exposição de rede já rejeitado pro WhatsApp (ADR raiz 0006); rejeitado pela mesma razão.

Status: accepted
