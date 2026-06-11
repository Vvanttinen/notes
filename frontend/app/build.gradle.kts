plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val generatedMsalResDir = layout.buildDirectory.dir("generated/msalAuthConfig/res")
val unconfiguredTenantId = "00000000-0000-0000-0000-000000000000"
val unconfiguredClientId = "00000000-0000-0000-0000-000000000000"
val unconfiguredRedirectUri = "msauth://dev.vvanttinen.notes/notes-msal-unconfigured"
val unconfiguredSignatureHash = "notes-msal-unconfigured"
val unconfiguredApiScope = "api://00000000-0000-0000-0000-000000000000/access_as_user"

fun localEntraValue(name: String): String? =
    providers.environmentVariable(name)
        .orElse(providers.gradleProperty(name))
        .orNull
        ?.takeIf { it.isNotBlank() }

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}

val notesEntraTenantId = localEntraValue("NOTES_ENTRA_TENANT_ID")
val notesEntraAndroidClientId = localEntraValue("NOTES_ENTRA_ANDROID_CLIENT_ID")
val notesEntraAndroidRedirectUri = localEntraValue("NOTES_ENTRA_ANDROID_REDIRECT_URI")
val notesEntraAndroidSignatureHash = localEntraValue("NOTES_ENTRA_ANDROID_SIGNATURE_HASH")
val notesEntraApiScope = localEntraValue("NOTES_ENTRA_API_SCOPE")
val notesEntraAuthority = notesEntraTenantId?.let { "https://login.microsoftonline.com/${it.trim().trim('/')}" }
val notesEntraConfigured = listOf(
    notesEntraTenantId,
    notesEntraAndroidClientId,
    notesEntraAndroidRedirectUri,
    notesEntraAndroidSignatureHash,
    notesEntraApiScope,
    notesEntraAuthority
).all { !it.isNullOrBlank() }

val generateMsalAuthConfig by tasks.registering {
    val tenantId = notesEntraTenantId ?: unconfiguredTenantId
    val clientId = notesEntraAndroidClientId ?: unconfiguredClientId
    val redirectUri = notesEntraAndroidRedirectUri ?: unconfiguredRedirectUri
    val outputDir = generatedMsalResDir

    inputs.property("tenantId", tenantId)
    inputs.property("clientId", clientId)
    inputs.property("redirectUri", redirectUri)
    outputs.dir(outputDir)

    doLast {
        val rawDir = outputDir.get().asFile.resolve("raw")
        rawDir.mkdirs()
        rawDir.resolve("msal_auth_config.json").writeText(
            """
            {
              "client_id": ${jsonString(clientId)},
              "redirect_uri": ${jsonString(redirectUri)},
              "broker_redirect_uri_registered": true,
              "authorization_user_agent": "DEFAULT",
              "account_mode": "SINGLE",
              "authorities": [
                {
                  "type": "AAD",
                  "audience": {
                    "type": "AzureADMyOrg",
                    "tenant_id": ${jsonString(tenantId)}
                  },
                  "default": true
                }
              ]
            }
            """.trimIndent()
        )
    }
}

android {
    namespace = "dev.vvanttinen.notes"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.vvanttinen.notes"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["msalPackageName"] = applicationId ?: "dev.vvanttinen.notes"
        manifestPlaceholders["msalSignatureHash"] = notesEntraAndroidSignatureHash ?: unconfiguredSignatureHash
        buildConfigField("boolean", "NOTES_ENTRA_CONFIGURED", notesEntraConfigured.toString())
        buildConfigField("String", "NOTES_ENTRA_API_SCOPE", buildConfigString(notesEntraApiScope ?: ""))
        buildConfigField("String", "NOTES_ENTRA_AUTHORITY", buildConfigString(notesEntraAuthority ?: ""))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            res.srcDir(generatedMsalResDir.get().asFile)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(generateMsalAuthConfig)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.msal)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
