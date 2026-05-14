plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.flexmark.core)
    implementation(libs.flexmark.ext.tables)
    implementation(libs.flexmark.ext.strikethrough)
    implementation(libs.flexmark.html2md)

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
