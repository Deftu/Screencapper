plugins {
    java
    kotlin("jvm") version("1.7.0")
    kotlin("plugin.serialization") version("1.7.0")
    id("xyz.unifycraft.gradle.tools") version("1.7.0")
    id("xyz.unifycraft.gradle.tools.loom") version("1.7.0")
    id("xyz.unifycraft.gradle.tools.shadow") version("1.7.0")
    id("xyz.unifycraft.gradle.tools.releases") version("1.7.0")
    id("xyz.unifycraft.gradle.tools.blossom") version("1.7.0")
}

repositories {
    maven("https://maven.terraformersmc.com")
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    modImplementation("net.fabricmc.fabric-api:fabric-api:0.57.0+1.18.2")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.8.1+kotlin.1.7.0")

    unishade(api("com.squareup.okhttp3:okhttp:4.9.3")!!)
    include(modImplementation("gg.essential:vigilance-1.18.1-fabric:227") {
        exclude(module = "kotlin-stdlib")
        exclude(module = "kotlin-stdlib-common")
        exclude(module = "kotlin-stdlib-jdk8")
        exclude(module = "kotlin-stdlib-jdk7")
        exclude(module = "kotlin-reflect")
        exclude(module = "annotations")
        exclude(module = "fabric-loader")
    })

    modImplementation("com.terraformersmc:modmenu:3.+")
}

tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs += "-Xjvm-default=enable"
        }
    }
}
