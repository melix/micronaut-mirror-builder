package io.micronaut.mirroring

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface MirroringExtension {
    val micronautVersion: Property<String>
    val features: ListProperty<String>
}
