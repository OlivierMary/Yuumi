plugins {
    kotlin("jvm") version "1.3.72"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("edu.sc.seis.launch4j") version "2.4.6"
    id("com.peterabeles.gversion") version "1.7.0"
}

group = "fr.omary.lol"
version = if (System.getenv("BRANCH_NAME") != null) {
    System.getenv("BRANCH_NAME")
} else {
    "DEV"
}
val sha: String = if (System.getenv("SHA") != null) {
    System.getenv("SHA")
} else {
    "DEV"
}

repositories {
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
}

tasks {
    shadowJar {
        archiveBaseName.set("shadow-yuumi")
        mergeServiceFiles()
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "fr.omary.lol.yuumi.ApplicationKt",
                    "Implementation-Version" to "${rootProject.version}",
                    "Implementation-Commit" to sha
                )
            )
        }
    }
    createExe {
        outfile = "${rootProject.name.capitalize()}.exe"
        mainClassName = "fr.omary.lol.yuumi.ApplicationKt"
        icon = "$projectDir/assets/favicon.ico"
        productName = rootProject.name.capitalize()
        jar = "${projectDir}/build/libs/shadow-${rootProject.name}-${rootProject.version}-all.jar"
        bundledJrePath = "%JAVA_HOME%"
        splashFileName = "$projectDir/assets/splash.bmp"
        splashWaitForWindows = false
        splashTimeout = 2
        windowTitle = rootProject.name.capitalize()
        mutexName = rootProject.name.capitalize()

        dependsOn(shadowJar)
    }

    build{
        dependsOn(createExe)
    }
}

tasks.compileKotlin {
    dependsOn.add(tasks.createVersionFile)
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
    implementation("com.github.stirante:lol-client-java-api:1.2.2")
    implementation("org.apache.commons:commons-lang3:3.10")
    implementation("org.apache.httpcomponents:httpclient:4.5.12")
    implementation("com.beust:klaxon:5.2")
}

gversion {
    srcDir = "${project.rootDir}/src/main/kotlin/"
    classPackage = "${rootProject.group}.${rootProject.project.name}"
    className = "YuumiVersion"
    dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    timeZone = "UTC"
    debug = false
    language = "kotlin"
    explicitType = false
}
