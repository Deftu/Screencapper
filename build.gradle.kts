plugins {
    kotlin("jvm") version("1.6.21")
    kotlin("plugin.serialization") version("1.6.21")
    id("xyz.unifycraft.gradle.multiversion-root") version("1.8.3")
}

preprocess {
    val fabric11900 = createNode("1.19-fabric", 11900, "yarn")
    val fabric11802 = createNode("1.18.2-fabric", 11802, "yarn")

    fabric11900.link(fabric11802)
}
