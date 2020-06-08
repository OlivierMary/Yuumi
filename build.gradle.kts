import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.3.72"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("edu.sc.seis.launch4j") version "2.4.6"
}

group = "fr.omary.lol"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    jcenter()
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("shadow-yuumi")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "fr.omary.lol.yuumi.Application",
            "Implementation-Version" to version))
        }
    }
    createExe {
        outfile = "${rootProject.name}.exe"
        mainClassName = "fr.omary.lol.yuumi.Application"
        icon = "$projectDir/assets/favicon.ico"
        productName = "Yuumi"
        jar = "${projectDir}/build/libs/shadow-yuumi-${rootProject.version}-all.jar"
        bundledJrePath = System.getenv("JAVA_HOME")
        dependsOn(shadowJar)
    }
    build {
        dependsOn(createExe)
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.stirante:lol-client-java-api:1.2.2")
    implementation("org.apache.commons:commons-lang3:3.10")
    implementation("org.apache.httpcomponents:httpclient:4.5.12")
    implementation("com.beust:klaxon:5.2")
}
