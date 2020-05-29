#!/usr/bin/env bash


if [ "$1" != "dbinit" -a "$#" -eq 1 ]; then
    SERVER_NAME=$1
fi

# check for other processes
# ... if dbinit will start
if [ "$1" == "dbinit" ]; then
    SERVER_NAME=dbinit
    echo "run mock for dbinit .... "
fi

echo "run command for $SERVER_NAME!"

EXITCODE=$?

exit $EXITCODE