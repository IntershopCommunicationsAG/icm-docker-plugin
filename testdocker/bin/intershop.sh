#!/bin/sh


if [ "$1" != "dbprepare" -a "$#" -eq 1 ]; then
    SERVER_NAME=$1
fi

# check for other processes
# ... if dbinit will start
if [ "$1" == "dbprepare" ]; then
    SERVER_NAME=dbprepare
    shift
    if [ -z "$2" ]; then
      MAINPARAMETER=-classic
    else
      MAINPARAMETER=$@
    fi
    echo "run mock for dbinit .... with $MAINPARAMETER"
fi

echo "run command for $SERVER_NAME!"

echo '{"@timestamp":"2020-05-29T12:34:03.113+00:00","@version":"1","message":"Success: core:Class1 SQLScriptPreparer [resources/core/dbinit/scripts/utctimestamp.sql] 87ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class1 SQLScriptPreparer [resources/core/dbinit/scripts/utctimestamp.sql]","tags":["DBInit"]}'
echo '{"@timestamp":"2020-05-29T12:34:03.129+00:00","@version":"1","message":"Success: core:Class2 SystemDomainPreparer 13ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class2 SystemDomainPreparer","tags":["DBInit"]}'
echo '{"@timestamp":"2020-05-29T12:34:06.480+00:00","@version":"1","message":"Success: core:Class3 SQLScriptPreparer [resources/core/dbinit/scripts/spmainfile.ddl] 3351ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class3 SQLScriptPreparer [resources/core/dbinit/scripts/spmainfile.ddl]","tags":["DBInit"]}'
sleep 5
echo '{"@timestamp":"2020-05-29T12:34:07.475+00:00","@version":"1","message":"Success: core:Class4 DatabaseIndexesPreparer [resources/core/dbinit/scripts/dbindex.ddl] 991ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class4 DatabaseIndexesPreparer [resources/core/dbinit/scripts/dbindex.ddl]","tags":["DBInit"]}'
sleep 5
echo '{"@timestamp":"2020-05-29T12:34:16.095+00:00","@version":"1","message":"Success: core:Class4.1 CartridgeDatabaseIndexesPreparer 8615ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class4.1 CartridgeDatabaseIndexesPreparer","tags":["DBInit"]}'
sleep 5
echo '{"@timestamp":"2020-05-29T12:34:16.137+00:00","@version":"1","message":"Success: core:Class5 PrepareContextIndexPrefs 38ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class5 PrepareContextIndexPrefs","tags":["DBInit"]}'
sleep 5
echo '{"@timestamp":"2020-05-29T12:34:24.022+00:00","@version":"1","message":"Success: core:Class6 LocaleInformationPreparer 7878ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class6 LocaleInformationPreparer","tags":["DBInit"]}'
sleep 5
echo '{"@timestamp":"2020-05-29T12:34:24.262+00:00","@version":"1","message":"Success: core:Class8 PreparePreferenceGroups [com.intershop.beehive.core.dbinit.data.preference.PreferenceGroups,com.intershop.beehive.core.dbinit.data.preference.PreferenceGroupInformation] 236ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class8 PreparePreferenceGroups [com.intershop.beehive.core.dbinit.data.preference.PreferenceGroups,com.intershop.beehive.core.dbinit.data.preference.PreferenceGroupInformation]","tags":["DBInit"]}'
sleep 5
echo '{"@timestamp":"2020-05-29T12:34:25.101+00:00","@version":"1","message":"Success: core:Class9 PreparePreferenceDefinitions [com.intershop.beehive.core.dbinit.data.preference.PreferenceDefinitions,com.intershop.beehive.core.dbinit.data.preference.PreferenceDefinitionInformation] 835ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class9 PreparePreferenceDefinitions [com.intershop.beehive.core.dbinit.data.preference.PreferenceDefinitions,com.intershop.beehive.core.dbinit.data.preference.PreferenceDefinitionInformation]","tags":["DBInit"]}'
sleep 5
echo '{"@timestamp":"2020-05-29T12:34:25.689+00:00","@version":"1","message":"Success: core:Class10 PrepareCurrencies [com/intershop/beehive/core/dbinit/data/currency/currencies.resource] 584ms","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"core:Class10 PrepareCurrencies [com/intershop/beehive/core/dbinit/data/currency/currencies.resource]","tags":["DBInit"]}'
sleep 5

echo '{"@timestamp":"2020-05-29T12:44:18.859+00:00","@version":"1","message":"","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"ac_oci:post.Class10 SQLScriptPreparer [resources/ac_oci/dbinit/scripts/enfinitytable.sql]","executionsite":"SLDSystem","tags":["DBInit"]}'
sleep 3
echo '{"@timestamp":"2020-05-29T12:44:22.704+00:00","@version":"1","message":"220 cartridge information(s) stored.","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"ac_oci:post.Class10 SQLScriptPreparer [resources/ac_oci/dbinit/scripts/enfinitytable.sql]","executionsite":"SLDSystem","tags":["DBInit"]}'
sleep 3
echo '{"@timestamp":"2020-05-29T12:44:22.708+00:00","@version":"1","message":"DBPrepare 810 initialization steps (success: 810, failure: 0) finished in 714s","logger_name":"com.intershop.tool.dbinit.DBInit","thread_name":"main","level":"INFO","level_value":20000,"requestapplication":"ac_oci:post.Class10 SQLScriptPreparer [resources/ac_oci/dbinit/scripts/enfinitytable.sql]","executionsite":"SLDSystem","tags":["DBInit"]}'
sleep 3
echo '{"@timestamp":"2020-05-29T12:44:22.735+00:00","@version":"1","message":"Additional MBean oracle.ucp.admin.UniversalConnectionPoolMBean:name=UniversalConnectionPoolManager(574249547)-6279166176859690069-2-a14fcc5ab2ab for pattern '*UniversalConnectionPoolMBean*:*' has been found and will be deregistered!","logger_name":"com.intershop.beehive.orm.oracle.capi.jdbc.OracleUcpDataSourceFactory","thread_name":"main","level":"WARN","level_value":30000,"requestapplication":"ac_oci:post.Class10 SQLScriptPreparer [resources/ac_oci/dbinit/scripts/enfinitytable.sql]","executionsite":"SLDSystem"}'
sleep 3
echo '{"@timestamp":"2020-05-29T12:44:22.735+00:00","@version":"1","message":"Additional MBean oracle.ucp.admin.UniversalConnectionPoolMBean:name=UniversalConnectionPoolManager(574249547)-6279166176859690069-2-a14fcc5ab2ab for pattern '*UniversalConnectionPoolMBean*:*' has been found and will be deregistered!","logger_name":"com.intershop.beehive.orm.oracle.capi.jdbc.OracleUcpDataSourceFactory","thread_name":"main","level":"WARN","level_value":30000,"requestapplication":"ac_oci:post.Class10 SQLScriptPreparer [resources/ac_oci/dbinit/scripts/enfinitytable.sql]","executionsite":"SLDSystem"}'
sleep 3

EXITCODE=$?

exit $EXITCODE