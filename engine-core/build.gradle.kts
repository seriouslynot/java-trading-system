plugins { `java-library` }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
tasks.test { useJUnitPlatform() }
val checkNoFramework = tasks.register("checkNoFramework") {
    doLast {
        val forbidden = configurations.runtimeClasspath.get().incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
            .filter { it.group.startsWith("org.springframework") }
        check(forbidden.isEmpty()) { "engine-core 不能依赖 Spring: $forbidden" }
    }
}
val bench = sourceSets.create("bench") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
tasks.register<JavaExec>("regimeBench") {
    classpath = bench.runtimeClasspath
    mainClass.set("com.fredlin.trading.bench.RegimeBenchmark")
    args(providers.gradleProperty("steps").getOrElse("300000"))
    jvmArgs("-Dstdout.encoding=UTF-8")
}
tasks.register<JavaExec>("journalBench") {
    classpath = bench.runtimeClasspath
    mainClass.set("com.fredlin.trading.bench.JournalBenchmark")
    jvmArgs("-Dstdout.encoding=UTF-8")
}
tasks.check { dependsOn(checkNoFramework, bench.classesTaskName) }
