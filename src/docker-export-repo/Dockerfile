FROM ghcr.io/graalvm/graalvm-ce:java11-21.3.0

RUN gu install native-image
COPY demo /demo
COPY gradle-dist /gradle
ENV PATH=$PATH:/gradle/bin
WORKDIR /demo
# Using separate steps to build layers incrementally
RUN gradle dependencies -i -M sha256 --dry-run
RUN gradle help -i
RUN gradle assemble -i
# Force download of dependencies
RUN gradle nativeImage -i || true
RUN gradle build -i || true
COPY export-repo.sh /export-repo.sh
RUN bash /export-repo.sh
