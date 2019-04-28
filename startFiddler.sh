#!/bin/bash

/usr/bin/java -Xms${D2P_MS_RAM} -Xmx${D2P_MX_RAM} -XX:MetaspaceSize=${D2P_METASPACE_SIZE} -XX:MaxMetaspaceSize=${D2P_MAX_METASPACE_SIZE} \
-DD2P_HOTROD_HOST=${D2P_HOTROD_HOST} -DD2P_HOTROD_PORT=${D2P_HOTROD_PORT} -DD2P_HOTROD_CONN_TIMEOUT_S=${D2P_HOTROD_CONN_TIMEOUT_S} \
-cp /opt/dump2proto/ispn-dump2proto-${D2P_VERSION}.jar biz.karms.FiddlerTool ${D2P_DOMAIN_TO_CHECK}
