import com.modrinth.minotaur.dependencies.ModDependency
import com.modrinth.minotaur.dependencies.DependencyType
import xyz.unifycraft.gradle.tools.CurseDependency

plugins {
    java
    kotlin("jvm") version("1.6.21")
    kotlin("plugin.serialization") version("1.6.21")
    id("xyz.unifycraft.gradle.multiversion")
    id("xyz.unifycraft.gradle.tools")
    id("xyz.unifycraft.gradle.tools.loom")
    id("xyz.unifycraft.gradle.tools.shadow")
    id("xyz.unifycraft.gradle.tools.releases")
}

repositories {
    maven("https://maven.terraformersmc.com/")
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    modImplementation("net.fabricmc.fabric-api:fabric-api:${when (mcData.version) {
        11900 -> "0.57.0+1.19"
        11802 -> "0.57.0+1.18.2"
        else -> throw IllegalStateException("Invalid MC version: ${mcData.version}")
    }}")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.8.1+kotlin.1.7.0")

    unishade(api("com.squareup.okhttp3:okhttp:4.9.3")!!)
    include("com.mojang:brigadier:1.0.18")
    include(modImplementation("gg.essential:vigilance-${when (mcData.version) {
        11900 -> "1.18.1-fabric"
        11802 -> "1.18.1-fabric"
        else -> "${mcData.versionStr}-${mcData.loader.name}"
    }}:227") {
        exclude(module = "kotlin-stdlib")
        exclude(module = "kotlin-stdlib-common")
        exclude(module = "kotlin-stdlib-jdk8")
        exclude(module = "kotlin-stdlib-jdk7")
        exclude(module = "kotlin-reflect")
        exclude(module = "annotations")
        exclude(module = "fabric-loader")
    })

    modImplementation("com.terraformersmc:modmenu:${when (mcData.version) {
        11900 -> "4.0.4"
        11802 -> "3.2.3"
        else -> throw IllegalStateException("Invalid MC version: ${mcData.version}")
    }}")
}

releases {
    file.set(tasks.remapJar)
    changelogFile.set(file("CHANGELOG.md"))

    modrinth {
        projectId.set(property("releases.modrinth.id")?.toString() ?: throw IllegalStateException("No modrinth project ID set."))
        dependencies.set(listOf(
            ModDependency("P7dR8mSH", DependencyType.REQUIRED),
            ModDependency("Ha28R6CL", DependencyType.REQUIRED),
            ModDependency("mOgUt4GM", DependencyType.OPTIONAL)
        ))
    }

    curseforge {
        releaseName.set("[${mcData.versionStr}] ${modData.name} ${modData.version}")
        projectId.set(property("releases.curseforge.id")?.toString() ?: throw IllegalStateException("No curseforge project ID set."))
        dependencies.set(listOf(
            CurseDependency("fabric-api", true),
            CurseDependency("fabric-language-kotlin", true),
            CurseDependency("modmenu", false)
        ))
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs += "-Xjvm-default=enable"
        }
    }

    remapJar {
        archiveBaseName.set("${modData.name}-${mcData.versionStr}")
    }
}
