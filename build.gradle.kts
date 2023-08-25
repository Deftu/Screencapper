plugins {
    id("xyz.deftu.gradle.multiversion-root")
}

preprocess {
    val fabric12001 = createNode("1.20.1-fabric", 1_20_01, "yarn")
    val fabric11902 = createNode("1.19.2-fabric", 1_19_02, "yarn")

    fabric12001.link(fabric11902)
}
