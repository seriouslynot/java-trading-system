plugins {
    java
    alias(libs.plugins.spring.boot)
}
version = "0.1.0-SNAPSHOT"
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":engine-core"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.test { useJUnitPlatform() }
val bench = sourceSets.create("bench") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}
tasks.register<JavaExec>("pipelineBench") {
    classpath = bench.runtimeClasspath
    mainClass.set("com.fredlin.trading.app.bench.PipelineBenchmark")
    args(providers.gradleProperty("pipelineCommands").getOrElse("4096"))
    jvmArgs("-Dstdout.encoding=UTF-8")
}
tasks.check { dependsOn(bench.classesTaskName) }
