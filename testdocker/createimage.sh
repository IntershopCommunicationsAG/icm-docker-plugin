#!/bin/sh

echo Building base image intershopmock/icm-as-mock:latest

USER=`whoami`
USERID=`id -u $USER`

echo Build for user $USER and id $USERID and group id $USERID

docker build --build-arg USERNAME=$USER --build-arg USERID=$USERID -t intershopmock/icm-as-mock:latest . -f ./Dockerfile
