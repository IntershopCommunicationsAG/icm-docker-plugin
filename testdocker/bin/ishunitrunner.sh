#!/bin/sh

#
# wait for AS to be started
#
SERVER_NAME=testrunner

#
# Build and log Tomcat command line
#
CMD_LINE="java $JAVA_OPTS com.intershop.testrunner.IshTestrunner"

if [ $# -ne 3 ]; then
    echo "Usage: $0 <build directory> <cartridge> <test context:  -c=class -p=package -s=suite>" >&2
    exit 10
else
    CONTEXT=`echo $3 | awk -F= '{print $2}'`
fi

export CARTRIDGE_DESCRIPTOR=/intershop/project/cartridges/$2/$1/descriptor/cartridge.descriptor

CMD_LINE="$CMD_LINE -o=/intershop/ishunitrunner/output/${CONTEXT} $3"

echo Cartridge Descriptor: $CARTRIDGE_DESCRIPTOR
echo Command Line:
echo $CMD_LINE
echo

#
# Execute TESTRUNNER
#

if [ "$2" == "ac_solr_cloud_test" ]; then
  echo "run test 1"
  sleep 60
  echo "run test 1 finished"
  cp -pR /intershop/bin/output/tests.embedded.com.intershop.adapter.search_solr.internal.SuiteSolrCloud /intershop/ishunitrunner/output/
else
  echo "run test 2"
  sleep 60
  echo "run test 2 finished"
  cp -pR /intershop/bin/output/tests.suite.SFResponsivB2bAllSuite /intershop/ishunitrunner/output/
fi
