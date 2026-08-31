package com.intershop.gradle.icm.docker.tasks

import com.intershop.gradle.icm.docker.tasks.utils.ContainerEnvironment
import com.intershop.gradle.icm.docker.tasks.utils.ICMContainerEnvironmentBuilder
import spock.lang.Specification

class DBPrepareTaskSpec extends Specification {

    def 'the container is started as a dbPrepare'() {
        when:
        Map<String, String> environment = DBPrepareTask.createDBPrepareEnvironment("localhost").toMap()

        then:
        environment[ICMContainerEnvironmentBuilder.ENV_IS_DBPREPARE] == "true"
    }

    def 'the JMX connector is enabled to read the preparation progress'() {
        when:
        Map<String, String> environment = DBPrepareTask.createDBPrepareEnvironment("localhost").toMap()

        then:
        environment[ICMContainerEnvironmentBuilder.ENV_ENABLE_JMX] == "true"
    }

    def 'the RMI stubs are created for the host the JMX connector is read from'() {
        when:
        // the default of the container would be an address that cannot be resolved outside of it
        Map<String, String> environment = DBPrepareTask.createDBPrepareEnvironment("192.168.0.10").toMap()

        then:
        environment[ICMContainerEnvironmentBuilder.ENV_EXTERNAL_CONTAINER_IP] == "192.168.0.10"
    }

    def 'the environment of the container is kept'() {
        given:
        def containerEnvironment = new ContainerEnvironment().add("CARTRIDGE_LIST", "ft_icm_as")

        when:
        Map<String, String> environment = containerEnvironment.merge(DBPrepareTask.createDBPrepareEnvironment("localhost")).toMap()

        then:
        environment["CARTRIDGE_LIST"] == "ft_icm_as"
        environment[ICMContainerEnvironmentBuilder.ENV_IS_DBPREPARE] == "true"
    }
}
