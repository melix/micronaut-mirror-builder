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
}
