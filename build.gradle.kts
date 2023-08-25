plugins {
    id("xyz.deftu.gradle.multiversion-root")
}

preprocess {
    val fabric11902 = createNode("1.19.2-fabric", 1_19_02, "yarn")
    val fabric11802 = createNode("1.18.2-fabric", 1_18_02, "yarn")

    fabric11902.link(fabric11802)
}
