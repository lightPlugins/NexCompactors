plugins {
    id("java")
}

group = "io.nexstudios.compactors"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.named<Jar>("jar") {
    enabled = false
}