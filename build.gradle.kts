plugins {
    kotlin("jvm") version "1.3.72"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("edu.sc.seis.launch4j") version "2.4.6"
}

group = "fr.omary.lol"
version = System.getenv("BRANCH_NAME")

repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
}

tasks {
    shadowJar{
        archiveBaseName.set("shadow-yuumi")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "fr.omary.lol.yuumi.ApplicationKt",
            "Implementation-Version" to "${rootProject.version}"))
        }
    }
    createExe {
        outfile = "${rootProject.name}.exe"
        mainClassName = "fr.omary.lol.yuumi.ApplicationKt"
        icon = "$projectDir/assets/favicon.ico"
        productName = "Yuumi"
        jar = "${projectDir}/build/libs/shadow-yuumi-${rootProject.version}-all.jar"
        bundledJrePath = "%JAVA_HOME%"
        bundledJreAsFallback = true

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

val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "1.8"
