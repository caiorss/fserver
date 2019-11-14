// import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.gradle.jvm.tasks.Jar

val mainKotlinClassName = "com.github.fserver.command.Command"
val mainJavaClassName = "${mainKotlinClassName}Kt"

plugins {
    application
    kotlin("jvm") version "1.3.50"
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
    // getByName("debug").java.srcDirs("src/debug/kotlin")
    // getByName("test").java.srcDirs("src/test/kotlin")
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
