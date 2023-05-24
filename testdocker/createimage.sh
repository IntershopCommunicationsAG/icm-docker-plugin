#!/bin/sh

echo Building base image intershopmock/icm-as-mock:mock

USER=`whoami`
USERID=`id -u $USER`

echo Build for user $USER and id $USERID

docker build --build-arg USERNAME=$USER --build-arg USERID=$USERID -t intershopmock/icm-as-mock:mock . -f ./Dockerfile
