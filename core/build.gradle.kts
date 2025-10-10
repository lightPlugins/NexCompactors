plugins {
    id("java")
    kotlin("jvm") version "1.8.0" // Ensure you have the Kotlin plugin applied
    id("io.freefair.lombok") version "8.11"
    id("com.gradleup.shadow") version "8.3.5"
    id("maven-publish")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
}

group = "io.nexstudios.drops"
version = "1.0-SNAPSHOT"

base {
    archivesName.set("NexDrops")
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven("https://mvn.lumine.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.7-R0.1-SNAPSHOT")
    compileOnly(files("C:/Users/philipp/IdeaProjects/Nexus/build/libs/Nexus-1.0.0-all.jar"))
    compileOnly("io.lumine:Mythic-Dist:5.9.5")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    processResources {
        from(sourceSets.main.get().resources.srcDirs()) {
            filesMatching("plugin.yml") {
                expand(
                    "name" to "NexDrops",
                    "version" to version
                )

            }
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("NexDrops")
        destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
        relocate("co.aikar.commands", "io.nexstudios.nexus.libs.commands")
        relocate("co.aikar.locales", "io.nexstudios.nexus.libs.locales")
    }
}