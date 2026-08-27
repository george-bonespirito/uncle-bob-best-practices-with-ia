# Um tópico Kafka por Processing Step, não um tópico único de resultado

ADR order/0003 decidiu que os 7 MS de dado publicam o resultado da sua Processing Step num tópico Kafka consumido por MS Order, mas não detalhou se seria um tópico único (com envelope discriminando a etapa) ou um por etapa — na hora, isso não parecia relevante. Depois de decidir Avro + Apicurio Registry (ADR raiz 0003) para as mensagens, passou a ser: cada Processing Step retorna um payload de formato diferente (Enrichment: dado de cliente/produto; Credit Validation: status+reserva de crédito; Stock Validation: reserva+itens; Route Calculation: dado de rota), e um tópico único exigiria um schema Avro com union type ou uma estratégia de múltiplos schemas por tópico no registry (`TopicRecordNameStrategy`).

Decidimos um tópico por **task do Conductor**, não por Processing Step de alto nível — como Data Enrichment é um sub-workflow de 3 tasks reais (ADR raiz 0001), o mapeamento vira 1 MS = 1 task = 1 tópico, 7 no total: `order.customer-enrichment.result`, `order.product-enrichment.result`, `order.pricing.result`, `order.credit-validation.result`, `order.stock-validation.result`, `order.route-calculation.result`, `order.invoice-generation.result`. Cada um com schema Avro único e simples, sem union type. MS Order roda um consumer por tópico (ou subscreve por padrão de nome `order.*.result`), gravando em `order_update_event` com o `stepType` inferido do tópico de origem, não de um campo de envelope.

Isso não afeta a propriedade de concorrência da ADR order/0003 — nenhum desses tópicos é particionado por `order_id`, então a race entre instâncias de MS Order continua existindo do mesmo jeito, só que espalhada entre 5 tópicos em vez de 1.

Alternativa rejeitada: tópico único com envelope + union type Avro — menos tópicos para gerenciar operacionalmente, mas schema mais complexo e evolução mais difícil (mudar o payload de uma etapa exige revisar o union inteiro, não só o schema daquela etapa).

Status: accepted
