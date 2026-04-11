plugins {
    application
    java
}

group = "raynna.tools"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("com.displee:rs-cache-library:7.3.0")
    implementation("com.displee:disio:2.2")
    implementation("org.apache.commons:commons-lang3:3.10")
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("com.formdev:flatlaf-extras:3.5.4")
    implementation("com.formdev:flatlaf-intellij-themes:3.5.4")
    implementation(files(
        "C:/Users/andre/Desktop/cs2-editor/build/classes/java/main",
        "C:/Users/andre/Desktop/cs2-editor/build/resources/main",
        "C:/Users/andre/Desktop/cs2-editor-1.5/build/classes/java/main",
        "C:/Users/andre/Desktop/cs2-editor-1.5/build/classes/kotlin/main",
        "C:/Users/andre/Desktop/cs2-editor-1.5/build/resources/main",
        "C:/Users/andre/Desktop/Avalon-osrs/build/classes/java/main",
        "C:/Users/andre/Desktop/Avalon-osrs/build/resources/main"
    ))
}

application {
    mainClass.set("raynna.tools.ItemEditorApp")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("src/resources"))
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

val packageWindowsDist by tasks.registering(Sync::class) {
    dependsOn(tasks.installDist)
    into(layout.buildDirectory.dir("package/item-editor"))

    from(tasks.installDist) {
        into("app")
    }
    from("run-item-editor.bat")
    from("ensure-java.ps1")
}

tasks.register<Zip>("packageWindowsZip") {
    dependsOn(packageWindowsDist)
    archiveFileName.set("raynna-item-editor-windows.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(packageWindowsDist)
}
