import com.bmuschko.gradle.docker.tasks.container.DockerCopyFileFromContainer
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import org.gradle.api.internal.file.FileOperations
import org.gradle.kotlin.dsl.support.serviceOf
import io.micronaut.mirroring.MirroringExtension

plugins {
    `jvm-ecosystem`
    id("com.bmuschko.docker-remote-api")
}

val extension = extensions.create<MirroringExtension>("mirroring")

val micronautCli by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
    dependencies.addLater(project.provider {
        project.dependencies.create("io.micronaut.starter:micronaut-micronaut-cli:${extension.micronautVersion.get()}")
    })
}

val gradleProjectDir = layout.buildDirectory.dir("gradle-project")

val fileOperations = serviceOf<FileOperations>()
val archiveOperations = serviceOf<ArchiveOperations>()

val generateGradle = tasks.register<JavaExec>("generateMicronautGradleProject") {
    outputs.dir(gradleProjectDir)
    inputs.property("features", extension.features)
    inputs.property("extraDependencies", extension.extraDependencies)
    mainClass.set("io.micronaut.starter.cli.MicronautStarter")
    classpath = micronautCli
    args(listOf("create-app", "demo",
            "-l", "java",
            "-b", "gradle",
            "--jdk", "11",
            "-f") + extension.features.get().joinToString(","))
    workingDir = gradleProjectDir.get().asFile
    doFirst {
        fileOperations.delete(gradleProjectDir.map { it.dir("demo") })
    }
    doLast {
        val extraDeps = extension.extraDependencies.get()
        if (extraDeps.isNotEmpty()) {
            val buildFile = gradleProjectDir.get().file("demo/build.gradle").asFile
            val depsBlock = extraDeps.map { e ->
                val scope = e.key
                val deps = e.value
                deps.map { d -> "    ${scope}(\"${d}\")" }.joinToString("\n")
            }.joinToString("\n")
            buildFile.appendText("""
                dependencies {
                $depsBlock                                                   
                }
            """.trimIndent())
        }
        val testContainersProperties = gradleProjectDir.get().file("demo/src/test/resources/testcontainers.properties").asFile
        testContainersProperties.parentFile.mkdir()
        testContainersProperties.writeText("""
            oracle.container.image=ghcr.io/graalvm/native-image:java11-21.3
        """.trimIndent())
    }
}

val utilsDir = layout.buildDirectory.dir("utils")
val copyGradleDistribution = tasks.register<Sync>("copyGradleDist") {
    into(utilsDir.map { it.dir("gradle-dist") })
    from(gradle.gradleHomeDir)
}

val prepareDockerContext = tasks.register<Sync>("prepareGradleDockerContext") {
    into(layout.buildDirectory.dir("docker/gradle-project"))
    from(generateGradle.map { gradleProjectDir.get() })
    from(copyGradleDistribution.map { utilsDir })
    from("src/docker-export-repo")
}

val buildInDocker = tasks.register<DockerBuildImage>("buildGradleProject") {
    inputDir.fileProvider(prepareDockerContext.map { it.destinationDir })
}

val exportDir = layout.buildDirectory.dir("repo")

val createContainer = tasks.register<DockerCreateContainer>("createContainer") {
    imageId.set(buildInDocker.map { it.imageId.get() })
    //hostConfig.autoRemove.set(true)
}

val startContainer = tasks.register<DockerStartContainer>("startContainer") {
    targetContainerId(createContainer.map { it.containerId.get() })
}

val deleteContainer = tasks.register<DockerRemoveContainer>("removeContainer") {
    targetContainerId(createContainer.map { it.containerId.get() })
}

val exportDependencies = tasks.register<DockerCopyFileFromContainer>("exportDependencies") {
    description = "Exports the dependencies required by a Micronaut build into a file repository"
    group = "Micronaut"
    outputs.dir(exportDir)
    targetContainerId(startContainer.map { it.containerId.get() })
    remotePath.set("/export/")
    hostPath.set(exportDir.map { it.asFile.absolutePath })
    finalizedBy(deleteContainer)
    doFirst {
        fileOperations.delete(exportDir)
        fileOperations.mkdir(exportDir)
    }
    doLast {
        // Patch the repository for Gradle weirdness
        fileOperations.fileTree(exportDir).visit {
            if (name == "okio-jvm-2.4.3.jar") {
                fileOperations.copy {
                    from(file)
                    into(file.parentFile)
                    rename { "okio-2.4.3.jar" }
                }
            }
        }
    }
}

val offlineGradleTestProject = layout.buildDirectory.dir("offline-gradle-project")
val generateOfflineGradleProject = tasks.register<Copy>("generateOfflineGradleProject") {
    into(offlineGradleTestProject)
    from(generateGradle) {
        eachFile {
            if (name == "settings.gradle") {
                filter {
                    if (it.startsWith("rootProject.")) {
                        """pluginManagement {
                        |    repositories {
                        |        maven {
                        |           url "file://repo"
                        |        }
                        |    }
                        |}
                        |$it
                    """.trimMargin()
                    } else {
                        it
                    }
                }
            }
            if (name == "build.gradle") {
                filter {
                    it.replace("mavenCentral()", "maven { url { \"file://repo\" } }")
                }
            }
        }
    }
}

val prepareTestDockerContext = tasks.register<Sync>("prepareTestDockerContext") {
    into(layout.buildDirectory.dir("docker/gradle-test-project"))
    from(generateOfflineGradleProject.map { offlineGradleTestProject.get() })
    from(copyGradleDistribution.map { utilsDir })
    from("src/docker-test-repo")
    from(exportDependencies.map { exportDir }) {
        into("repo")
    }
}

val testInDocker = tasks.register<DockerBuildImage>("testGradleProject") {
    description = "Tests that the application can build with the mirrored dependencies"
    group = "Micronaut"
    inputDir.fileProvider(prepareTestDockerContext.map { it.destinationDir })
}
