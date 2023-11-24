package com.intershop.gradle.icm.docker.tasks.utils

enum class ClasspathLayout(val value: String) {
    RELEASE("release"), SOURCE("source"), SOURCE_JAR("sourceJar"), ECLIPSE("eclipse");

    companion object {

        fun parse(stringValues: String?) : Set<ClasspathLayout> {
            return stringValues?.split(",")?.map { ClasspathLayout.findByName(it) }?.toSet() ?: setOf()
        }
        fun findByName(name: String) : ClasspathLayout {
            return ClasspathLayout.values().find { it.value.equals(name, ignoreCase = true) }
                   ?: throw IllegalArgumentException(
                           "There's no ${ClasspathLayout::class.java.name} for string '$name'")
        }
        fun render(classpathLayouts: Set<ClasspathLayout> ) : String =
                classpathLayouts.joinToString(separator = ",") { it.value }

        fun default() : Set<ClasspathLayout> = setOf(SOURCE_JAR, RELEASE)
    }
}
