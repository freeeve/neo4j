FROM debian:bullseye
ENV DEBIAN_FRONTEND noninteractive

COPY ${DEBFILE} /tmp/

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:21 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

RUN apt-get update -qq && \
    # https://bugs.launchpad.net/ubuntu/+source/ca-certificates-java/+bug/1998065
    apt-get install -y ca-certificates-java && \
    apt-get install -y --no-install-recommends /tmp/${DEBFILE}

ENTRYPOINT ["/usr/bin/cypher-shell"]
