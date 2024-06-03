import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    application
    kotlin("jvm") version "2.0.0"
    id("com.google.protobuf") version "0.8.18"
    kotlin("plugin.serialization") version "1.8.10"
    id("com.github.gmazzo.buildconfig") version "3.1.0"
}

group = "com.aibot"
version = "0.3.2"

application {
    mainClass.set("com.aibot.ConversationApplicationKt")
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")
    implementation("io.ktor:ktor-client-java:2.2.3")
    implementation("com.aallam.openai:openai-client:3.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.google.protobuf:protobuf-javalite:3.21.12")
    implementation("com.google.protobuf:protobuf-kotlin-lite:3.21.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("org.ktorm:ktorm-core:4.0.0")
    implementation("org.ktorm:ktorm-support-sqlite:3.6.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
}

buildConfig {
    val props = Properties()
    file("gradle.properties").inputStream().use { props.load(it) }
    buildConfigField("String", "botApiKey", "\"${props.getProperty("botApiKey")}\"")
    buildConfigField("String", "openAiApiKey", "\"${props.getProperty("openAiApiKey")}\"")
    buildConfigField("long?", "historyChatId", props.getProperty("historyChatId"))
}

tasks {
    val fatJar = register<Jar>("fatJar") {
        dependsOn("compileJava", "compileKotlin", "processResources")
        archiveClassifier.set("standalone")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes(mapOf("Main-Class" to application.mainClass.get()))
        }
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) } + sourcesMain.output
        from(contents)
    }

    build {
        dependsOn(fatJar)
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}