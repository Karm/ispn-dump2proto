FROM fedora:25
MAINTAINER Michal Karm Babacek <karm@email.cz>

ENV DEPS      java-1.8.0-openjdk-devel.x86_64 jna.x86_64 nfs-utils libnfsidmap
ENV D2P_HOME  "/opt/dump2proto/"
ENV JAVA_HOME "/usr/lib/jvm/java-1.8.0"

RUN dnf -y update && dnf -y install ${DEPS} && dnf clean all && \
    useradd -s /sbin/nologin dump2proto && \
    mkdir -p /exports /opt/dump2proto && chown dump2proto /opt/dump2proto /exports && \
    chgrp dump2proto /opt/dump2proto /exports && chmod ug+rwxs /opt/dump2proto /exports

WORKDIR /opt/dump2proto

VOLUME /exports

EXPOSE 111/udp 2049/tcp

ENV D2P_VERSION 1.0-SNAPSHOT
# TODO: So something like su -c 'java ...' -s /bin/bash - dump2proto to drop root for java process...
ADD start.sh /opt/dump2proto/
RUN if [[ ${ATACH_DEBUGGER:-False} == "True" ]]; then \
        export DBG_OPTS="-Dtrace=org.infinispan -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${D2P_DBG_PORT:-1661} -Xnoagent -Djava.compiler=NONE"; \
    fi; \
    echo 'echo "/exports ${D2P_NFS_EXPORT:-*(ro)}" > /etc/exports' >> /opt/dump2proto/start.sh && \
    echo 'start' >> /opt/dump2proto/start.sh && \
    echo 'export JAVA_OPTS="\
 -server \
 -Xms${D2P_MS_RAM:-1g} \
 -Xmx${D2P_MX_RAM:-1g} \
 -XX:MetaspaceSize=${D2P_METASPACE_SIZE:-128m} \
 -XX:MaxMetaspaceSize=${D2P_MAX_METASPACE_SIZE:-512m} \
 -XX:+UseG1GC \
 -XX:MaxGCPauseMillis=300 \
 -XX:InitiatingHeapOccupancyPercent=60 \
 -XX:+HeapDumpOnOutOfMemoryError \
 -XX:HeapDumpPath=/opt/dump2proto \
"' >> /opt/dump2proto/start.sh && \
   echo '\
 java \
 ${JAVA_OPTS} \
 -DD2P_GENERATED_PROTOFILES_DIRECTORY=${D2P_GENERATED_PROTOFILES_DIRECTORY:-/exports} \
 -DD2P_HOTROD_HOST=${D2P_HOTROD_HOST:-192.168.122.156} \
 -DD2P_HOTROD_PORT=${D2P_HOTROD_PORT:-11322} \
 -DD2P_HOTROD_CONN_TIMEOUT_S=${D2P_HOTROD_CONN_TIMEOUT_S:-60} \
 -DD2P_CUSTOMLIST_GENERATOR_INTERVAL_S=${D2P_CUSTOMLIST_GENERATOR_INTERVAL_S:-30} \
 -DD2P_IOC_GENERATOR_INTERVAL_S=${D2P_IOC_GENERATOR_INTERVAL_S:-30} \
 -DD2P_ALL_IOC_GENERATOR_INTERVAL_S=${D2P_ALL_IOC_GENERATOR_INTERVAL_S:-30} \
 -DD2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S=${D2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S:-30} \
 -DD2P_WHITELIST_GENERATOR_INTERVAL_S=${D2P_WHITELIST_GENERATOR_INTERVAL_S:-30} \
 ${DBG_OPTS} \
 -jar /opt/dump2proto/ispn-dump2proto-${D2P_VERSION}.jar \
 ' >> /opt/dump2proto/start.sh
ADD target/ispn-dump2proto-${D2P_VERSION}.jar /opt/dump2proto/
CMD ["/opt/dump2proto/start.sh"]