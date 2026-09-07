plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.pitest) apply false
}

// Convenção única, aplicada a todo microsserviço. O PRD manda os mesmos
// guardrails e thresholds pra cada MS — escrever isso uma vez é o motivo
// de o build ser multi-projeto.
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "info.solidsoft.pitest")
    apply(plugin = "jacoco")

    group = "com.bonespirito"
    version = "0.1.0-SNAPSHOT"

    repositories { mavenCentral() }

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    // `libs.xxx` (accessor type-safe) não resolve dentro de subprojects{} — limitação
    // do Gradle. Catálogo acessado explicitamente via VersionCatalogsExtension.
    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

    dependencies {
        "implementation"("org.springframework.boot:spring-boot-starter-web")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"(libs.findLibrary("cucumber-java").get())
        "testImplementation"(libs.findLibrary("cucumber-junit-platform-engine").get())
        "testImplementation"(libs.findLibrary("cucumber-spring").get())
        "testImplementation"(libs.findLibrary("junit-platform-suite").get())
        "testImplementation"(libs.findLibrary("archunit-junit5").get())
        "testImplementation"(libs.findLibrary("mockk").get())
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")

        "pitest"(libs.findLibrary("pitest-junit5").get())
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions { freeCompilerArgs.add("-Xjsr305=strict") }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    }

    // Thresholds do PRD: LINE >= 80%, BRANCH >= 70%. A classe @SpringBootApplication
    // fica de fora — é ponto de entrada do framework, não código com decisão.
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        classDirectories.setFrom(
            fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
                exclude("**/*Application*")
            }
        )
        violationRules {
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.80".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "0.70".toBigDecimal()
                }
            }
        }
    }

    tasks.named("check") { dependsOn(tasks.named("jacocoTestCoverageVerification")) }

    // Pitest fica FORA do check, de propósito — é a checagem cara (PRD).
    // Alvo: domain.policy.*, threshold 80% (ADR order/0005).
    extensions.configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
        targetClasses.set(setOf("com.bonespirito.*.domain.policy.*"))
        junit5PluginVersion.set(libs.findVersion("pitestJunit5").get().requiredVersion)
        mutationThreshold.set(80)
    }

    // Nenhum MS é uma lib; o artefato de deploy é o bootJar.
    tasks.named<Jar>("jar") { enabled = false }
}
