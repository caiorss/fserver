// import org.jetbrains.kotlin.gradle.dsl.Coroutines

plugins {
    application
    kotlin("jvm") version "1.3.50"
}

application {
    mainClassName = "samples.ServerAppKt"
}

dependencies {
    compile(kotlin("stdlib"))
    compile("io.javalin:javalin:3.6.0")
	// compile( "io.ktor:ktor-server-core:1.2.4")
	//     compile("io.ktor:ktor-server-netty:1.2.4")
    compile( "org.slf4j:slf4j-simple:1.7.29")
}

repositories {
    // jcenter()
    mavenCentral()
}

sourceSets {
    // Change source directory 
    getByName("main").java.srcDirs("src")
    // getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    // getByName("debug").java.srcDirs("src/debug/kotlin")    
    // getByName("test").java.srcDirs("src/test/kotlin")
}



//sourceCompatibility = 1.8
//
//compileKotlin {
//    kotlinOptions.jvmTarget = "1.8"
//}
//compileTestKotlin {
//    kotlinOptions.jvmTarget = "1.8"
//}
