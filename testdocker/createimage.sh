#!/bin/sh

echo Building base image intershopmock/icm-as-mock:latest

docker build -t intershopmock/icm-as-mock:latest . -f ./Dockerfile
