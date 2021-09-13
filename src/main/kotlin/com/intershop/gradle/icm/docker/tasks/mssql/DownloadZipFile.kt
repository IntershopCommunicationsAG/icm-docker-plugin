package com.intershop.gradle.icm.docker.tasks.mssql

import com.intershop.gradle.icm.docker.utils.PackageUtil
import org.gradle.api.DefaultTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

open class DownloadZipFile @Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {

    @get:Input
    val moduleGroup: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val moduleName: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val moduleVersion: Property<String> = project.objects.property(String::class.java)

    @get:Input
    val classifier: Property<String> = project.objects.property(String::class.java)

    @TaskAction
    fun download() {
        val file = PackageUtil.downloadPackage(project,
            "${moduleGroup.get()}:${moduleName.get()}:${moduleVersion.get()}",
            classifier.get())
        println(file?.path)
    }
}