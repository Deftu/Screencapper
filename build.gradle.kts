plugins {
    java
    kotlin("jvm") version("1.7.0")
    kotlin("plugin.serialization") version("1.7.0")
    id("xyz.unifycraft.gradle.tools") version("1.5.1")
    id("xyz.unifycraft.gradle.tools.shadow") version("1.5.1")
    id("xyz.unifycraft.gradle.tools.releases") version("1.5.1")
    id("xyz.unifycraft.gradle.tools.blossom") version("1.5.1")
}

repositories {
    maven("https://maven.terraformersmc.com")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    modImplementation("net.fabricmc.fabric-api:fabric-api:0.56.0+1.18.2")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.8.0+kotlin.1.7.0")

    shade(modImplementation("gg.essential:vigilance-1.18.1-fabric:224+pull-51") {
        exclude(module = "kotlin-stdlib")
        exclude(module = "kotlin-stdlib-common")
        exclude(module = "kotlin-stdlib-jdk8")
        exclude(module = "kotlin-stdlib-jdk7")
        exclude(module = "kotlin-reflect")
        exclude(module = "annotations")
        exclude(module = "fabric-loader")
    } {
        exclude(module = "kotlin-stdlib")
    })

    modImplementation("com.terraformersmc:modmenu:3.+")
}

tasks {
    named<Jar>("unishadowJar") {
        relocate("gg.essential.elementa", "dev.isxander.lib.elementa")
        relocate("gg.essential.universal", "dev.isxander.lib.universalcraft")
    }

    compileKotlin {
        kotlinOptions {
            freeCompilerArgs += "-Xjvm-default=enable"
        }
    }
}
