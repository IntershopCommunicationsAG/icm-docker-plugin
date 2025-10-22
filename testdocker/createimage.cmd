rem this file is not actually used by the build but supports developers to execute the tests locally

echo Building base image intershopmock/icm-as-mock:mock

set USER=intershop
set USERID=150

echo Build for user $USER and id $USERID

docker build --build-arg USERNAME=%USER% --build-arg USERID=%USERID% -t intershopmock/icm-as-mock:mock . -f .\Dockerfile
