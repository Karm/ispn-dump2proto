#!/bin/bash

# @author Michal Karm Babacek
# stub to be generated on Docker Build

function start() {
# TODO: Too much spaghetti.
/sbin/rpcbind -i
/sbin/rpc.statd --no-notify --port 32765 --outgoing-port 32766
/usr/sbin/rpc.nfsd -V3 -N2 -N4 -d 8
/usr/sbin/rpc.mountd -V3 -N2 -N4 --port 32767
/usr/sbin/exportfs -ra
}

function stop() {
    /usr/sbin/rpc.nfsd 0
    /usr/sbin/exportfs -au
    /usr/sbin/exportfs -f
    exit 0
}

trap stop TERM
