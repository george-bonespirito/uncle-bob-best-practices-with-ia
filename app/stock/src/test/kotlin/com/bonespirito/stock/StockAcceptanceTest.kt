package com.bonespirito.stock

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Roda os .feature deste contexto. Cenário ainda não implementado carrega a
 * tag `@pending` e é filtrado em junit-platform.properties: o backlog do
 * domínio fica visível no arquivo, e a tag sai de um cenário por vez,
 * conforme ele passa a existir.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue", value = "com.bonespirito.stock.acceptance")
class StockAcceptanceTest
