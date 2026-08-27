# Worker-polling entre Orkes e os MS de Processing Step, em vez de HTTP push

O design inicial tinha o Orkes chamando via HTTP cada um dos 7 MS de dado (Customers, Products, Pricing, Stock, Contabil, Invoice, Route) para executar cada Processing Step — a task HTTP nativa do Conductor.

Decidimos usar o modelo de worker-polling do Conductor: cada MS roda um worker (SDK do Conductor) que faz polling na fila de tasks do próprio Orkes, executa o trabalho quando recebe uma task, e reporta conclusão de volta via API do Conductor.

Motivo: nenhum dos 7 MS precisa expor um endpoint HTTP alcançável pelo Orkes — eles puxam trabalho no próprio ritmo, com backpressure natural (um worker sobrecarregado simplesmente para de fazer poll por um tempo, em vez de derrubar uma chamada síncrona do Orkes). Elimina o acoplamento de rede na direção Orkes→MS.

Nota honesta: isso não elimina HTTP do sistema — o worker ainda fala com a API do Conductor (polling + relatar conclusão) via HTTP/gRPC dependendo do SDK. O que muda é a direção e a natureza do acoplamento: antes era "Orkes precisa alcançar 7 endereços de rede diferentes, síncrono, no timing do fork-join"; agora é "7 MS falam com 1 endereço conhecido (o Conductor), no próprio ritmo".

Alternativa rejeitada: HTTP task nativa do Conductor (Orkes chama cada MS diretamente) — mais simples de configurar por task, mas exige service discovery/rede alcançável em ambas direções e acopla o MS ao timing do fork-join do workflow.

Trade-off aceito: cada MS agora roda um processo worker de longa duração fazendo polling, com mais boilerplate de setup (registro do worker, task definition no Conductor) do que uma HTTP task simples.

Status: accepted
