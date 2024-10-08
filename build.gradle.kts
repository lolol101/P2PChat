plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/")
    maven("https://jitpack.io")
    maven("https://artifacts.consensys.net/public/maven/maven/")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("io.libp2p:jvm-libp2p:1.2.0-RELEASE")
}

tasks.test {
    useJUnitPlatform()
}