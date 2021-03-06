# ispn-dump2proto
Dumps Infinispan cache to a Google Protobuffer file wth filters and processors applied and serves the files over NFS.

## Coverage

To get coverage analysis run the following:

    mvn clean test -Pcoverage
    
Then the files with are located in target/site/jacoco/index.html 

## Testing

    mvn test -DD2P_HOTROD_HOST=192.168.122.156 -DD2P_HOTROD_PORT=11322

## Building

The dependency on protostream 3.0.7.Final-karm-SNAPSHOT comes from the experimental: [Karm/protostream/tree/3.0.7.Final-karm](https://github.com/Karm/protostream/tree/3.0.7.Final-karm)

    mvn clean package -Passemble && \
    sudo docker build -t karm/ispn-dump2proto:1.0.15 . && \
    sudo docker push karm/ispn-dump2proto:1.0.15

## Running

TODO: get rid of privileged and define CAPs

    sudo docker run -e 'D2P_NFS_EXPORT=*(ro,sync,insecure,no_subtree_check,no_root_squash)' \
    -e 'D2P_MS_RAM=1g' \
    -e 'D2P_MX_RAM=1g' \
    -e 'D2P_METASPACE_SIZE=128m' \
    -e 'D2P_MAX_METASPACE_SIZE=512m' \
    -e 'D2P_HOTROD_HOST=192.168.122.156' \
    -e 'D2P_HOTROD_PORT=11322' \
    -e 'D2P_HOTROD_CONN_TIMEOUT_S=60' \
    -e 'D2P_CUSTOMLIST_GENERATOR_INTERVAL_S=30' \
    -e 'D2P_IOC_GENERATOR_INTERVAL_S=30' \
    -e 'D2P_ALL_IOC_GENERATOR_INTERVAL_S=30' \
    -e 'D2P_ALL_CUSTOMLIST_GENERATOR_INTERVAL_S=30' \
    -e 'D2P_WHITELIST_GENERATOR_INTERVAL_S=30' \
    --privileged -p 192.168.122.156:111:111/udp -p 192.168.122.156:2049:2049/tcp \
    --net=host -v /exports:/exports -d -i --name ispn-dump2proto karm/ispn-dump2proto:1.0-SNAPSHOT
