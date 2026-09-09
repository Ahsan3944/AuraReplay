plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
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

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION

val pluginName = "AuraReplay"

tasks.jar {
    archiveBaseName.set(pluginName)
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
}
