## Micronaut Application Mirror

This Gradle build is responsible for creating a mirror repository of everything required to build a Micronaut application.

TL/DR: By calling `./gradlew exportDependencies`, a directory named `build/repo` will be generated, corresponding to all dependencies which are required to build the project.

### How it works

The first thing this project does is creating a Micronaut Application from the Launch CLI.
You can look at the [features](build.gradle.kts) to select the list of features to be supported.

Once the project is generated, it is _patched_ (for example to add configuration to make it pass), then the build is executed within a Docker container.

The build in docker will trigger the download of many dependencies, which will then be copied into this `build/repo` directory.

### Testing

Eventually, the generated build is _patched_ in order to replace the use of the `mavenLocal()` and Gradle plugin portal repositories to use the local repository instead.

By running the `testGradleProject` task, you can see the output of executing the test project against that repository, again within a Docker container.

The patched build is available in `build/docker/gradle-test-project`.
