# Classificador transitória vs. permanente para DLT: default é permanente

ADR raiz 0005 definiu o conceito (falha transitória tenta retry curto antes de desistir; falha permanente vai direto ao DLT) mas não os tipos concretos de exceção que caem em cada lado.

Decidimos uma política de **default para permanente**: só uma lista curta e explícita de exceções conhecidas como transitórias entra no caminho de retry; qualquer coisa não listada — incluindo exceções nunca vistas antes — vai direto ao DLT.

**Transitório** (retry curto: 3 tentativas, backoff 200ms/500ms/1s, antes do DLT):
- `org.springframework.dao.TransientDataAccessException` — Spring já categoriza timeout de conexão, deadlock detectado, etc. sob essa hierarquia
- Timeout de rede consultando o Apicurio Registry (schema lookup)

**Permanente** (direto ao DLT — é o default, não uma lista a manter):
- Erro de deserialização Avro (payload corrompido ou schema incompatível)
- `orderId` referenciado que não existe em `orders`
- Qualquer exceção não listada acima como transitória

Motivo do default-para-permanente: uma lista exaustiva de "tudo que é transitório" está condenada a ficar desatualizada, e o custo de errar pro lado errado é assimétrico — reintentar algo permanente é inofensivo (vai pro DLT de qualquer forma, só atrasa um pouco), mas tratar como transitório algo que na verdade é um bug real mascararia o problema atrás de retries que nunca vão funcionar. Fail-safe: na dúvida, DLT.

**Não confundir com o retry de conflito de `version`** (ADR order/0001) — aquele é sobre reaplicar a mudança com dado fresco depois de um optimistic lock falhar; este classificador é sobre a mensagem Kafka nem conseguir ser processada (erro antes de chegar no `UPDATE`).

Status: accepted
