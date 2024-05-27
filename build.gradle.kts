import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    application
    kotlin("jvm") version "1.9.22"
    id("com.google.protobuf") version "0.8.18"
    kotlin("plugin.serialization") version "1.8.10"
    id("com.github.gmazzo.buildconfig") version "3.1.0"
}

buildscript {
}

group = "com.aibot"
version = "0.3.0"
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
    implementation("com.aallam.openai:openai-client:3.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    implementation("com.google.protobuf:protobuf-javalite:3.21.12")
    implementation("com.google.protobuf:protobuf-kotlin-lite:3.21.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
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
        dependsOn.addAll(
            listOf(
                "compileJava",
                "compileKotlin",
                "processResources"
            )
        ) // We need this for Gradle optimization to work
        archiveClassifier.set("standalone") // Naming the jar
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to application.mainClass)) } // Provided we set it up in the application plugin configuration
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) } +
                sourcesMain.output
        from(contents)
    }
    build {
        dependsOn(fatJar) // Trigger fat jar creation during build
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "17"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "17"
}