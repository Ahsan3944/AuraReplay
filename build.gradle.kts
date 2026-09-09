plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23"
}

group = "com.ultraop"
version = "0.1.0-SNAPSHOT"

description = "Next-generation server-side Minecraft motion capture and performance editing studio"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION

tasks.jar {
    archiveBaseName.set("AuraReplay")
}
