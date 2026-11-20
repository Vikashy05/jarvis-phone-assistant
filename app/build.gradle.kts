import java.net.URI
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.local.jarvis"
    compileSdk = 33

    defaultConfig {
        applicationId = "dev.local.jarvis"
        minSdk = 33
        targetSdk = 33
        versionCode = 2
        versionName = "0.2"
        ndk { abiFilters += "arm64-v8a" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources { noCompress += "task" }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/planner"))
}

dependencies {
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
}

val modelOutput = layout.buildDirectory.file("generated/planner/planner.task")
val sourceOverride = providers.gradleProperty("plannerFile")
val modelDigest = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2"
val preparePlanner by tasks.registering {
    inputs.property("sha256", modelDigest)
    outputs.file(modelOutput)
    doLast {
        val target = modelOutput.get().asFile
        target.parentFile.mkdirs()
        val temp = target.resolveSibling("planner.partial")
        try {
            val url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/49bfd323c25c9dae2118906e1f3ffd16e0729b13/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
            val source = sourceOverride.orNull?.let { File(it).inputStream() }
                ?: URI(url).toURL().openConnection().apply {
                    connectTimeout = 20000
                    readTimeout = 120000
                }.getInputStream()
            source.use { input -> temp.outputStream().use { input.copyTo(it) } }
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(temp.inputStream(), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
            check(digest.digest().joinToString("") { "%02x".format(it) } == modelDigest) { "Planner checksum mismatch" }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }
}
tasks.named("preBuild") { dependsOn(preparePlanner) }
