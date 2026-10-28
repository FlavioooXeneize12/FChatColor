import org.gradle.api.tasks.compile.JavaCompile

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.release.set(8)
    options.encoding = "UTF-8"
}

dependencies {
    implementation(project(":fchatcolor-api"))
    // Legacy is Java 8 and can be shaded via runtime classpath
    runtimeOnly(project(":fchatcolor-legacy"))
    // Modern is Java 21 (paper-api) - NOT declared here to avoid variant mismatch.
    // It is shaded manually via shadowJar.from(...) below. The plugin loads it via reflection (Class.forName).
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("com.mysql:mysql-connector-j:8.0.33")
}

tasks.withType<ProcessResources> {
    filesMatching("plugin.yml") {
        expand(mapOf("project" to mapOf("version" to project.version)))
    }
    filesMatching("paper-plugin.yml") {
        expand(mapOf("project" to mapOf("version" to project.version)))
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("FChatColor-${project.version}.jar")
    archiveClassifier.set("")
    mergeServiceFiles()
    // Manually include modern jar (Java 21) without declaring a project dependency
    from(project(":fchatcolor-modern").tasks.named("jar"))
    // Exclude signatures and module-info
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")
    manifest {
        attributes["Main-Class"] = "io.github.fchatcolor.FChatColorPlugin"
    }
    // Ensure modern jar is built before shading
    dependsOn(project(":fchatcolor-modern").tasks.named("jar"))
    dependsOn(project(":fchatcolor-legacy").tasks.named("jar"))
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
