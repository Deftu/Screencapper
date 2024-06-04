import com.modrinth.minotaur.dependencies.ModDependency
import com.modrinth.minotaur.dependencies.DependencyType
import dev.deftu.gradle.tools.minecraft.CurseRelation
import dev.deftu.gradle.tools.minecraft.CurseRelationType
import dev.deftu.gradle.utils.GameSide

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("dev.deftu.gradle.multiversion")
    id("dev.deftu.gradle.tools")
    id("dev.deftu.gradle.tools.shadow")
    id("dev.deftu.gradle.tools.minecraft.loom")
    id("dev.deftu.gradle.tools.minecraft.releases")
}

toolkit.useDevAuth()
toolkitLoomHelper.disableRunConfigs(GameSide.SERVER)

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

    modImplementation("net.fabricmc.fabric-api:fabric-api:${mcData.fabricApiVersion}")
    modImplementation(mcData.modMenuDependency)
    modImplementation("net.fabricmc:fabric-language-kotlin:1.7.4+kotlin.1.6.21")
    modImplementation("dev.deftu:DeftuLib-${mcData.versionStr}-${mcData.loader.name}:1.8.0")

    api(include("ca.weblite:java-objc-bridge:1.2")!!)
}

toolkitReleases {
    val versionChangelogFile = rootProject.file("changelogs/${modData.version}.md")
    if (versionChangelogFile.exists()) changelogFile.set(versionChangelogFile)

    modrinth {
        projectId.set("yzNFLSqx")
        dependencies.set(listOf(
            ModDependency("P7dR8mSH", DependencyType.REQUIRED),                     // Fabric API
            ModDependency("Ha28R6CL", DependencyType.REQUIRED),                     // Fabric Language Kotlin
            ModDependency("mOgUt4GM", DependencyType.OPTIONAL),                     // Mod Menu
            ModDependency("WfhjX9sQ", DependencyType.REQUIRED)                      // DeftuLib
        ))
    }

    curseforge {
        projectId.set("640948")
        relations.set(listOf(
            CurseRelation("fabric-api", CurseRelationType.REQUIRED),                // Fabric API
            CurseRelation("fabric-language-kotlin", CurseRelationType.REQUIRED),    // Fabric Language Kotlin
            CurseRelation("modmenu", CurseRelationType.OPTIONAL),                   // Mod Menu
            CurseRelation("deftulib", CurseRelationType.REQUIRED)                   // DeftuLib
        ))
    }
}
