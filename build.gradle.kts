plugins {
    id("io.micronaut.mirroring")
}

mirroring {
    micronautVersion.set("3.1.4")
    features.set(listOf(
            "acme",
            "netty-server",
            "oracle-cloud-sdk",
            "oracle-cloud-atp",
            "oracle-function",
            "reactor",
    ))
    extraDependencies.put("implementation", listOf("info.picocli:picocli"))
    extraDependencies.put("annotationProcessor", listOf("info.picocli:picocli-codegen"))
}
