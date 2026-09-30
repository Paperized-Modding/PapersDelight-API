plugins {
    id("java-library")
    id("maven-publish")
}

version = libs.versions.apiModule.get()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.craftEngineCore)
    compileOnly(libs.craftEngineBukkit)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }
    withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
    }
    jar {
        archiveFileName.set("papersdelight-api-${project.version}.jar")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.tako"
            artifactId = "papersdelight-api"
            from(components["java"])
            pom {
                name.set("PapersDelight API")
                description.set("Addon API for PapersDelight")
                url.set("https://github.com/PaperizedModding/PapersDelight-API")
                licenses {
                    license {
                        name.set("GNU Affero General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.html")
                        distribution.set("repo")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "hezhongReleases"
            url = uri("https://mvn.hezhongkj.top/releases/")
            credentials {
                username = (findProperty("hezhongMavenUser") as String?) ?: System.getenv("HEZHONG_MAVEN_USER")
                password = (findProperty("hezhongMavenPassword") as String?) ?: System.getenv("HEZHONG_MAVEN_PASSWORD")
            }
            isAllowInsecureProtocol = false
        }
        maven {
            name = "hezhongSnapshots"
            url = uri("https://mvn.hezhongkj.top/snapshots/")
            credentials {
                username = (findProperty("hezhongMavenUser") as String?) ?: System.getenv("HEZHONG_MAVEN_USER")
                password = (findProperty("hezhongMavenPassword") as String?) ?: System.getenv("HEZHONG_MAVEN_PASSWORD")
            }
        }
    }
}

