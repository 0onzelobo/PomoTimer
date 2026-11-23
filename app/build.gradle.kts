import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

// A chave de assinatura NUNCA fica no repositório: vem de variáveis de ambiente
// (GitHub Secrets no workflow de release, ou o seu terminal num build local).
val keystorePath: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.pomotimer.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pomotimer.app"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        // Avisos não barram o build (relatório sai como artefato no GitHub Actions); erros fatais ainda barram o release (lintVital)
        abortOnError = false
    }

    dependenciesInfo {
        // Não embute o bloco de dependências criptografado pelo Google (pedido do F-Droid)
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
