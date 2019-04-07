FROM fedora:28
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

ENV D2P_VERSION "3.0-SNAPSHOT"
# TODO: So something like su -c 'java ...' -s /bin/bash - dump2proto to drop root for java process...
ADD start.sh /opt/dump2proto/
RUN if [[ ${ATACH_DEBUGGER:-False} == "True" ]]; then \
        export DBG_OPTS="-Dtrace=org.infinispan -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${D2P_DBG_PORT:-1661} -Xnoagent -Djava.compiler=NONE"; \
    fi; \
    echo 'handlers = java.util.logging.ConsoleHandler' > /opt/dump2proto/logging.properties && \
    echo "${D2P_LOGGING:-.level=ALL}" >> /opt/dump2proto/logging.properties && \
    if [[ ${RUN_NFS:-True} == "True" ]]; then \
        echo 'echo "/exports ${D2P_NFS_EXPORT:-*(ro)}" > /etc/exports' >> /opt/dump2proto/start.sh && \
        echo 'start' >> /opt/dump2proto/start.sh; \
    fi; \
    echo 'export JAVA_OPTS="\
 -server \
 -Xms${D2P_MS_RAM:-1g} \
 -Xmx${D2P_MX_RAM:-1g} \
 -XX:MetaspaceSize=${D2P_METASPACE_SIZE:-128m} \
 -XX:MaxMetaspaceSize=${D2P_MAX_METASPACE_SIZE:-512m} \
 -XX:+UseG1GC \
 -XX:MaxGCPauseMillis=${D2P_MAX_GC_PAUSE_MILLIS:-2000} \
 -XX:InitiatingHeapOccupancyPercent=${D2P_INITIAL_HEAP_OCCUPANCY_PERCENT:-75} \
 -XX:+HeapDumpOnOutOfMemoryError \
 -XX:HeapDumpPath=/opt/dump2proto \
"' >> /opt/dump2proto/start.sh && \
   echo '\
 java \
 ${JAVA_OPTS} \
 -Djava.util.logging.config.file="/opt/dump2proto/logging.properties" \
 -DD2P_GENERATED_PROTOFILES_DIRECTORY="/exports" \
 -DD2P_HOTROD_HOST=${D2P_HOTROD_HOST:-192.168.122.156} \
 -DD2P_HOTROD_PORT=${D2P_HOTROD_PORT:-11322} \
 -DD2P_HOTROD_CONN_TIMEOUT_S=${D2P_HOTROD_CONN_TIMEOUT_S:-60} \
 -DD2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S=${D2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S:-0} \
 -DD2P_ALL_IOC_GENERATOR_INTERVAL_S=${D2P_ALL_IOC_GENERATOR_INTERVAL_S:-0} \
 -DD2P_CUSTOMLIST_GENERATOR_INTERVAL_S=${D2P_CUSTOMLIST_GENERATOR_INTERVAL_S:-0} \
 -DD2P_IOC_GENERATOR_INTERVAL_S=${D2P_IOC_GENERATOR_INTERVAL_S:-0} \
 -DD2P_RESOLVER_CACHE_BATCH_SIZE_S=${D2P_RESOLVER_CACHE_BATCH_SIZE_S:-20} \
 -DD2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S=${D2P_RESOLVER_CACHE_GENERATOR_INTERVAL_S:-0} \
 -DD2P_RESOLVER_CACHE_LISTENER_GENERATOR_INTERVAL_S=${D2P_RESOLVER_CACHE_LISTENER_GENERATOR_INTERVAL_S:-0} \
 -DD2P_RESOLVER_THREAT_TASK_RECORD_BATCH_SIZE_S=${D2P_RESOLVER_THREAT_TASK_RECORD_BATCH_SIZE_S:-1} \
 -DD2P_WHITELIST_GENERATOR_INTERVAL_S=${D2P_WHITELIST_GENERATOR_INTERVAL_S:-0} \
 -DD2P_IOC_DUMPER_INTERVAL_S=${D2P_IOC_DUMPER_INTERVAL_S:-0} \
 -DD2P_USE_S3_ONLY=${D2P_USE_S3_ONLY:-False} \
 -DD2P_S3_ENDPOINT=${D2P_S3_ENDPOINT:-https://localhost:9000} \
 -DD2P_S3_ACCESS_KEY=${D2P_S3_ACCESS_KEY:-3chars} \
 -DD2P_S3_SECRET_KEY=${D2P_S3_SECRET_KEY:-8chars} \
 -DD2P_S3_BUCKET_NAME=${D2P_S3_BUCKET_NAME:-serve-file} \
 -DD2P_S3_REGION=${D2P_S3_REGION:-eu-west-1} \
 -DD2P_ENABLE_CACHE_LISTENERS=${D2P_ENABLE_CACHE_LISTENERS:-False} \
 -DD2P_REVERSE_RESOLVERS_ORDER=${D2P_REVERSE_RESOLVERS_ORDER:-False} \
 -DD2P_NOTIFICATION_ENDPOINT_TEMPLATE=${D2P_NOTIFICATION_ENDPOINT_TEMPLATE:-http://wsproxy_adddress:8080/wsproxy/rest/message/%d/updatecache} \
 -DD2P_NOTIFICATION_ENDPOINT_METHOD=${D2P_NOTIFICATION_ENDPOINT_METHOD:-POST} \
 -DD2P_NOTIFICATION_ENDPOINT_TIMEOUT_MS=${D2P_NOTIFICATION_ENDPOINT_TIMEOUT_MS:-2000} \
 -DD2P_USE_NOTIFICATION_ENDPOINT=${D2P_USE_NOTIFICATION_ENDPOINT:-False} \
 -DD2P_IOC_REFRESH_INTERVAL_S=${D2P_IOC_REFRESH_INTERVAL_S:-600} \
 ${DBG_OPTS} \
 -jar /opt/dump2proto/ispn-dump2proto-${D2P_VERSION}.jar \
 ' >> /opt/dump2proto/start.sh
ADD target/ispn-dump2proto-${D2P_VERSION}.jar /opt/dump2proto/
CMD ["/opt/dump2proto/start.sh"]
