import com.modrinth.minotaur.dependencies.ModDependency
import com.modrinth.minotaur.dependencies.DependencyType
import xyz.unifycraft.gradle.tools.CurseDependency

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("xyz.unifycraft.gradle.multiversion")
    id("xyz.unifycraft.gradle.tools")
    id("xyz.unifycraft.gradle.tools.loom")
    id("xyz.unifycraft.gradle.tools.shadow")
    id("xyz.unifycraft.gradle.tools.releases")
}

loomHelper {
    disableRunConfigs(xyz.unifycraft.gradle.utils.GameSide.SERVER)
}

repositories {
    maven("https://maven.terraformersmc.com/")
    mavenCentral()
}

fun Dependency?.excludeVitals(): Dependency = apply {
    check(this is ExternalModuleDependency)
    exclude(module = "kotlin-stdlib")
    exclude(module = "kotlin-stdlib-common")
    exclude(module = "kotlin-stdlib-jdk8")
    exclude(module = "kotlin-stdlib-jdk7")
    exclude(module = "kotlin-reflect")
    exclude(module = "annotations")
    exclude(module = "fabric-loader")
}!!

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    modImplementation("net.fabricmc.fabric-api:fabric-api:${when (mcData.version) {
        11900 -> "0.57.0+1.19"
        11802 -> "0.57.0+1.18.2"
        else -> throw IllegalStateException("Invalid MC version: ${mcData.version}")
    }}")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.8.2+kotlin.1.7.10")

    unishade(api("com.squareup.okhttp3:okhttp:4.9.3")!!)
    include("com.mojang:brigadier:1.0.18")
    include(modImplementation(libs.versions.universalcraft.map {
        "gg.essential:universalcraft-${when (mcData.version) {
            11802 -> "1.18.1-fabric"
            else -> "${mcData.versionStr}-${mcData.loader.name}"
        }}:$it"
    }.get()).excludeVitals())
    include(modImplementation(libs.versions.elementa.map {
        "gg.essential:elementa-${when (mcData.version) {
            11900 -> "1.18.1-fabric"
            11802 -> "1.18.1-fabric"
            else -> "${mcData.versionStr}-${mcData.loader.name}"
        }}:$it"
    }.get()).excludeVitals())
    include(modImplementation(libs.versions.vigilance.map {
        "gg.essential:vigilance-${when (mcData.version) {
            11900 -> "1.18.1-fabric"
            11802 -> "1.18.1-fabric"
            else -> "${mcData.versionStr}-${mcData.loader.name}"
        }}:$it"
    }.get()).excludeVitals())

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
        projectId.set(property("releases.modrinth.id")?.toString() ?: throw IllegalStateException("No Modrinth project ID set."))
        dependencies.set(listOf(
            ModDependency("P7dR8mSH", DependencyType.REQUIRED),
            ModDependency("Ha28R6CL", DependencyType.REQUIRED),
            ModDependency("mOgUt4GM", DependencyType.OPTIONAL)
        ))
    }

    curseforge {
        releaseName.set("[${mcData.versionStr}] ${modData.name} ${modData.version}")
        projectId.set(property("releases.curseforge.id")?.toString() ?: throw IllegalStateException("No CurseForge project ID set."))
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
