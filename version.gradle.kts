import com.modrinth.minotaur.dependencies.ModDependency
import com.modrinth.minotaur.dependencies.DependencyType

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("xyz.deftu.gradle.multiversion")
    id("xyz.deftu.gradle.tools")
    id("xyz.deftu.gradle.tools.shadow")
    id("xyz.deftu.gradle.tools.minecraft.loom")
    id("xyz.deftu.gradle.tools.minecraft.releases")
}

//loomHelper {
//    disableRunConfigs(xyz.unifycraft.gradle.utils.GameSide.SERVER)
//}

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

    modImplementation("net.fabricmc.fabric-api:fabric-api:${mcData.fabricApiVersion}")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.8.2+kotlin.1.7.10")

    modImplementation("xyz.deftu:DeftuLib-${mcData.versionStr}-${mcData.loader.name}:1.6.0")

    include(api("com.squareup.okhttp3:okhttp:4.9.3")!!)
    include(api("ca.weblite:java-objc-bridge:1.2")!!)
    include(modImplementation(libs.versions.universalcraft.map {
        "gg.essential:universalcraft-${when (mcData.version) {
            11802 -> "1.18.1-fabric"
            else -> "${mcData.versionStr}-${mcData.loader.name}"
        }}:$it"
    }.get()).excludeVitals())
    include(modImplementation(libs.versions.elementa.map {
        "gg.essential:elementa-${when (mcData.version) {
            11902 -> "1.18.1-fabric"
            11802 -> "1.18.1-fabric"
            else -> "${mcData.versionStr}-${mcData.loader.name}"
        }}:$it"
    }.get()).excludeVitals())
    include(modImplementation(libs.versions.vigilance.map {
        "gg.essential:vigilance-${when (mcData.version) {
            11902 -> "1.18.1-fabric"
            11802 -> "1.18.1-fabric"
            else -> "${mcData.versionStr}-${mcData.loader.name}"
        }}:$it"
    }.get()).excludeVitals())

    modImplementation(mcData.modMenuDependency)
}

toolkitReleases {
    val versionChangelogFile = rootProject.file("changelogs/${modData.version}.md")
    if (versionChangelogFile.exists()) changelogFile.set(versionChangelogFile)

    modrinth {
        projectId.set(property("releases.modrinth.id")?.toString() ?: throw IllegalStateException("No Modrinth project ID set."))
        dependencies.set(listOf(
            ModDependency("P7dR8mSH", DependencyType.REQUIRED),
            ModDependency("Ha28R6CL", DependencyType.REQUIRED),
            ModDependency("mOgUt4GM", DependencyType.OPTIONAL)
        ))
    }

//    curseforge {
//        projectId.set(property("releases.curseforge.id")?.toString() ?: throw IllegalStateException("No CurseForge project ID set."))
//        dependencies.set(listOf(
//            CurseDependency("fabric-api", true),
//            CurseDependency("fabric-language-kotlin", true),
//            CurseDependency("modmenu", false)
//        ))
//    }
}
