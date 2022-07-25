plugins {
    id("xyz.unifycraft.gradle.multiversion-root")
}

preprocess {
    val fabric11900 = createNode("1.19-fabric", 11900, "yarn")
    val fabric11802 = createNode("1.18.2-fabric", 11802, "yarn")

    fabric11900.link(fabric11802)
}
