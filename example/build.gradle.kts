plugins {
    id("java-library")
}

group = "dev.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://mvn.hezhongkj.top/releases/")
    maven("https://repo-eo.catnies.top/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    compileOnly("dev.tako:papersdelight-api:2.0.0")
    compileOnly("cn.chengzhimeow:CC-Scheduler:2.0.4")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    jar {
        archiveFileName.set("ExampleAddon-${project.version}.jar")
    }
}
