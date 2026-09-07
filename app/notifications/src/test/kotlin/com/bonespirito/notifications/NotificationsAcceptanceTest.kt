package com.bonespirito.notifications

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Roda os .feature deste contexto. Todo cenário está marcado `@pending` e é
 * filtrado em junit-platform.properties: o backlog do domínio fica visível no
 * arquivo, e a tag sai de um cenário por vez, conforme ele passa a existir.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.glue", value = "com.bonespirito.notifications.acceptance")
class NotificationsAcceptanceTest
