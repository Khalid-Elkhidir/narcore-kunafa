import org.gradle.internal.IoActions
import org.gradle.internal.util.PropertiesUtils
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

val versionNumber = 1


dependencies {
    implementation(projects.dtoWeb)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.jodatime)
    implementation(libs.postgresql)
    implementation(libs.hikariCP)

    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)

    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.status.pages)

    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.call.id)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.compression)

    implementation(libs.ktor.server.jetty)
    implementation(libs.ktor.client.gson)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.server.double.receive)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.html.jvm)
    implementation(libs.javax.mail)
    implementation(libs.reflections)

    implementation(projects.compilerPlugins)
    ksp(projects.compilerPlugins)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)

    testImplementation(libs.h2)
}
configure<SourceSetContainer> {
    main {
        java.srcDir("src/main/kotlin")
    }
    test {
        java.srcDir("src/test/kotlin/")
    }
}
tasks.test {
    environment["IS_TEST"] = true
    useJUnitPlatform()
}


application {
    mainClass.set("com.narbase.narcore.main.MainKt")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "false"}")
}


tasks.jar {
    manifest {
        attributes("Main-Class" to "com.narbase.narcore.main.MainKt")
        attributes("Class-Path" to ".")
    }

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    from(configurations.compileClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
tasks.register("createProperties") {
    dependsOn(tasks.processResources)
    val charset = Charset.forName("UTF-8")
    val path =
        "${projects.narcoreServer.dependencyProject.layout.buildDirectory.asFile.get()}/resources/main/version.properties"
    File(path).parentFile.mkdirs()
    val fileOutputStream = FileOutputStream(path)
    val out: OutputStream = BufferedOutputStream(fileOutputStream)
    try {
        val propertiesToWrite: Properties = Properties()
        propertiesToWrite["versionName"] = project.version.toString()
        propertiesToWrite["versionNumber"] = versionNumber.toString()
        PropertiesUtils.store(propertiesToWrite, out, "Version and name of project", charset, System.lineSeparator())
    } finally {
        IoActions.closeQuietly(out)
    }
}
tasks.classes {
    dependsOn("createProperties")
}

tasks.register("getCommonModulePackages") {
    val kspConfig = KSPConfig.kspConfig
    val commonModulePath =
        "${projects.dtoWeb.dependencyProject.project.projectDir.path}/src/commonMain/kotlin/${kspConfig.destinationPackageRelativePath}"
    val commonModulePackages = File(commonModulePath).listAllDirectories().map { it.path }
    var str = ""
    commonModulePackages.forEachIndexed { index, item ->
        str += item
        if (index != commonModulePackages.lastIndex) {
            str += ";"
        }
    }
    ksp {
        arg("commonModulePackages", str)
    }
}

tasks.register("getProjectPackageName") {
    val name = rootProject.name
    ksp {
        arg("rootProjectName", name)
    }
}

tasks.register("generateDaos") {
    val kspConfig = KSPConfig.kspConfig
    val destinationServerRootPath = "${project.projectDir.path}/src/main/kotlin"
    val destinationServerPackagePath = "${destinationServerRootPath}/${kspConfig.destinationPackageRelativePath}"
    val destinationDaosRelativePath = "${destinationServerPackagePath}/${kspConfig.destinationDaosRelativePath}"
    val sourceRootPath = "${project.projectDir.path}/build/generated/ksp/main/kotlin"
    val sourceDaosRelativePath = "${sourceRootPath}/${kspConfig.sourceDaosRelativePath}"
    val sourceDaosFile = File(sourceDaosRelativePath)
    val destinationDaosPath = "${destinationDaosRelativePath}/autogenerated"
    val destinationDaosFile = File(destinationDaosPath)

    val destinationDtoWebRootPath = "${kspConfig.dtoWebPath}/src/commonMain/kotlin"
    val destinationDtoWebPackagePath = "${destinationDtoWebRootPath}/${kspConfig.destinationPackageRelativePath}"
    val destinationDtosRelativePath = "${destinationDtoWebPackagePath}/${kspConfig.destinationDtosRelativePath}"
    val sourceDtosRelativePath = "${sourceRootPath}/${kspConfig.sourceDtosRelativePath}"
    val sourceDtosFile = File(sourceDtosRelativePath)
    val destinationDtosPath = "${destinationDtosRelativePath}/autogenerated"
    val destinationDtosFile = File(destinationDtosPath)

    val destinationConvertorsRelativePath = "${destinationServerPackagePath}/${kspConfig.destinationConvertorsRelativePath}"
    val sourceConvertorsRelativePath = "${sourceRootPath}/${kspConfig.sourceConvertorsRelativePath}"
    val sourceConvertorsFile = File(sourceConvertorsRelativePath)
    val destinationConvertorsPath = "${destinationConvertorsRelativePath}/autogenerated"
    val destinationConvertorsFile = File(destinationConvertorsPath)

    ksp {
        arg("destinationDaosPath", destinationDaosPath)
        arg("destinationDtosPath", destinationDtosPath)
        arg("destinationConvertorsPath", destinationConvertorsPath)
    }

    dependsOn("getCommonModulePackages")
    dependsOn("getProjectPackageName")
    dependsOn("kspKotlin")
    doLast {
        val didCopyDaos = sourceDaosFile.copyRecursively(destinationDaosFile, kspConfig.shouldOverwrite)
        if (didCopyDaos) sourceDaosFile.deleteRecursively()

        val didCopyDtos = sourceDtosFile.copyRecursively(destinationDtosFile, kspConfig.shouldOverwrite)
        if (didCopyDtos) sourceDtosFile.deleteRecursively()

        val didCopyConvertors = sourceConvertorsFile.copyRecursively(destinationConvertorsFile, kspConfig.shouldOverwrite)
        if (didCopyConvertors) sourceConvertorsFile.deleteRecursively()
    }
}


codeGeneration {
    destinationPackageRelativePath = "com/narbase/narcore"
    destinationDaosRelativePath = "data/access"
    sourceDaosRelativePath = "daos"
    destinationDtosRelativePath = "dto/domain"
    sourceDtosRelativePath = "dtos"
    destinationConvertorsRelativePath = "data/conversions"
    sourceConvertorsRelativePath = "conversions"
    dtoWebPath = projects.dtoWeb.dependencyProject.projectDir.path
    shouldOverwrite = true
}

class KSPConfig {
    lateinit var destinationPackageRelativePath: String
    lateinit var destinationDaosRelativePath: String
    lateinit var sourceDaosRelativePath: String
    lateinit var destinationDtosRelativePath: String
    lateinit var sourceDtosRelativePath: String
    lateinit var destinationConvertorsRelativePath: String
    lateinit var sourceConvertorsRelativePath: String
    lateinit var dtoWebPath: String
    var shouldOverwrite: Boolean = false

    companion object {
        val kspConfig = KSPConfig()
    }
}

fun Project.codeGeneration(configuration: KSPConfig.() -> Unit) {
    KSPConfig.kspConfig.apply(configuration)
}

fun File.listAllDirectories(): Set<File> {
    val allDirectories = mutableSetOf<File>()
    val directories = this.listFiles(File::isDirectory)
    if (directories != null) {
        allDirectories.addAll(directories)
        directories.forEach {
            allDirectories.addAll(it.listAllDirectories())
        }
    }
    return allDirectories
}
