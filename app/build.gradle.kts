plugins { kotlin("jvm") version "1.9.25" }

group = "io.github.architectplatform.plugins"

version = "1.5.9"

java { sourceCompatibility = JavaVersion.toVersion("17") }

kotlin { jvmToolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

repositories {
  mavenCentral()
  maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/architect-platform/architect-api")
    credentials {
      username =
          System.getenv("GITHUB_USER")
              ?: project.findProperty("githubUser") as String?
              ?: "github-actions"
      password =
          System.getenv("REGISTRY_TOKEN")
              ?: System.getenv("GITHUB_TOKEN")
              ?: project.findProperty("githubToken") as String?
    }
  }
}

dependencies {
  implementation("io.github.architectplatform:architect-api:2.0.0")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1") // core Jackson
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1") // Kotlin support
}
