package com.intershop.gradle.icm.docker.utils

class PortMapping(val hostPort:Int, val containerPort:Int) {
    fun render() : String = "$hostPort:$containerPort"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PortMapping

        if (hostPort != other.hostPort) return false
        if (containerPort != other.containerPort) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hostPort
        result = 31 * result + containerPort
        return result
    }


}