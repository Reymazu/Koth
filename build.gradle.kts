plugins {
    java
}

group = "id.koth"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperApi")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.processResources {
    val props = mapOf("version" to project.version, "apiVersion" to project.property("pluginApiVersion"))
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}
