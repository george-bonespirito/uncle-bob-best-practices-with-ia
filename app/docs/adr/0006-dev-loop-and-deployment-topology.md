# Dev/test loop mira o cluster real; deploy de produção via ArgoCD; WhatsApp sem exposição pública

Três decisões relacionadas ao ambiente on-premise (K3s + ArgoCD, `bonespirito-cluster-k3s-infra`), fechando o que faltava do item "Direção K3s".

**Dev/test loop:** o código do MS Order roda em container local (`docker compose run --rm app ./gradlew check`, container-first mantido), mas conecta nos brokers **reais do cluster** (Postgres, Kafka, Apicurio via NodePort) em vez de duplicar Postgres/Kafka/Apicurio localmente. Motivo: já era a direção decidida antes mesmo do cluster existir de verdade ("brokers provisionados direto no K3s desde o início, evita setup descartável") — confirmado agora que o cluster é real. Sem ambiente "funciona local, quebra no cluster" por divergência de versão/config dos brokers.

**Deploy de produção:** MS Order (e, à medida que forem implementados, os outros 8 MS) roda **de verdade no cluster**, como Application do ArgoCD, mesmo padrão App of Apps já usado pros brokers (`bonespirito-cluster-k3s-infra/apps/<serviço>-app.yaml`). Isso é distinto do dev/test loop acima — teste roda local conectando ao cluster; a aplicação em si, quando "implantada", roda dentro do cluster.

**Consequência não resolvida, nova:** ArgoCD referencia uma imagem de container — não existe ainda definição de onde a imagem Docker do MS Order é construída e publicada (registry). Isso não é o mesmo problema que "deploy mechanism" (já resolvido — é GitOps/App of Apps) — é especificamente about build+publish da imagem antes do ArgoCD conseguir referenciá-la. Fica como novo item em aberto (ver PRD, partes cinzentas).

**WhatsApp:** MS Notifications só **envia** mensagem (chamada de saída pra API do provedor) — não recebe webhook de confirmação/opt-out no escopo atual. Resolve a exposição de rede sozinho: NodePort/LAN já basta, servidor só precisa de saída pra internet. Nenhuma exposição pública, túnel ou port forwarding necessário.

Status: accepted
