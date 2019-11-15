// import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

//group   = "jfserver"
//version = "0.1"

val mainKotlinClassName = "com.github.fserver.command.Command"
val mainJavaClassName = "${mainKotlinClassName}Kt"

plugins {
    application
    kotlin("jvm") version "1.3.50"
    id("java")
}

application {
    mainClassName = mainJavaClassName
}

dependencies {
    compile(kotlin("stdlib"))
    compile("io.javalin:javalin:3.6.0")
    compile( "org.slf4j:slf4j-simple:1.7.29")
    compile(    "com.github.ajalt:clikt:2.3.0")

}

repositories {
    // jcenter()
    mavenCentral()
}

sourceSets {
    // Change source directory
    getByName("main").java.srcDirs("src")
    // Set resources directory:
    // see: https://qiita.com/aya_n/items/d1fdf817a553ccfe6a22
    getByName("main").resources.srcDirs("resources")
}

// Reference: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.JavaExec.html?_ga=2.248692736.646913499.1573843899-716276975.1562400899
val runServer = task("runServer", type = JavaExec::class )
{
    classpath = sourceSets.main.get().runtimeClasspath
    main = mainJavaClassName
    args("dir", "/home/archbox/")
}

// ========>>> Optional: Used for builduing fat-jar <<==========//

val fatJar = task("fatJar", type = Jar::class) {
    baseName = "${project.name}-fat"
    manifest {
        attributes["Implementation-Title"] = "Gradle Jar File Example"
        // attributes["Implementation-Version"] = version
        attributes["Main-Class"] = "${mainJavaClassName}"
    }
    from(configurations.runtimeClasspath.get().map({ if (it.isDirectory) it else zipTree(it) }))
    with(tasks.jar.get() as CopySpec)
}


tasks {
    "build" {
        dependsOn(fatJar)
    }
}
