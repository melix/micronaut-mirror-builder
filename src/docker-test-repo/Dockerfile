FROM ghcr.io/graalvm/graalvm-ce:java11-21.3.0

RUN gu install native-image
COPY demo /demo
COPY gradle-dist /gradle
COPY repo /repo
ENV PATH=$PATH:/gradle/bin
WORKDIR /demo
# Using separate steps to build layers incrementally
RUN gradle help -i
RUN gradle assemble -i
# Force download of dependencies
RUN gradle nativeImage -i || true
RUN gradle build -i || true
