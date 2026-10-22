plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.devtools.ksp")
}

/**
 * Datos del proyecto de Firebase, leídos del archivo estándar app/google-services.json (el que se descarga de la
 * consola de Firebase). Sin el archivo la app compila igual y la pantalla de ingreso avisa que falta ese paso.
 */
val firebaseConfig: Map<String, String> = run {
  val source = file("google-services.json")
  if (!source.exists()) return@run emptyMap()
  val root = groovy.json.JsonSlurper().parse(source) as Map<*, *>
  val clients = (root["client"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
  fun Map<*, *>.at(vararg path: String): Any? = path.fold(this as Any?) { node, key -> (node as? Map<*, *>)?.get(key) }
  val client = clients.firstOrNull { it.at("client_info", "android_client_info", "package_name") == "com.aura.finanzas" }
    ?: return@run emptyMap()
  val oauth = (client["oauth_client"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>() +
    (client.at("services", "appinvite_service", "other_platform_oauth_client") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
  mapOf(
    "FIREBASE_PROJECT_ID" to (root.at("project_info", "project_id") as? String).orEmpty(),
    "FIREBASE_APP_ID" to (client.at("client_info", "mobilesdk_app_id") as? String).orEmpty(),
    "FIREBASE_API_KEY" to ((client["api_key"] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("current_key")?.toString().orEmpty(),
    // Cliente OAuth de tipo web (client_type 3): es el que firma el token de identidad que recibe Firebase.
    "GOOGLE_WEB_CLIENT_ID" to oauth.firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }?.get("client_id")?.toString().orEmpty(),
  )
}

android {
  namespace = "com.aura.finanzas"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aura.finanzas"
    minSdk = 26
    targetSdk = 36
    versionCode = 21
    versionName = "0.21"
    for (name in listOf("FIREBASE_PROJECT_ID", "FIREBASE_APP_ID", "FIREBASE_API_KEY", "GOOGLE_WEB_CLIENT_ID")) {
      buildConfigField("String", name, "\"${firebaseConfig[name].orEmpty()}\"")
    }
  }

  buildTypes {
    release {
      // La versión que se instala en el teléfono: sin modo de depuración, Compose corre mucho más fluido y
      // gasta menos batería. Se firma con la misma clave de depuración de este PC para que el SHA-1 registrado
      // en Firebase/Google siga valiendo (el ingreso con Google depende de él).
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("debug")
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
  packaging {
    // El SDK de Anthropic y sus dependencias traen archivos de licencia repetidos.
    resources.excludes += setOf(
      "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/AL2.0", "META-INF/LGPL2.1",
      "META-INF/INDEX.LIST", "META-INF/*.kotlin_module", "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
    )
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
      "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
      "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
      "-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi",
    )
  }
}

dependencies {
  implementation(platform("androidx.compose:compose-bom:2025.12.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.animation:animation")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.core:core-ktx:1.18.0")
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.navigation:navigation-compose:2.8.9")
  implementation("androidx.room:room-runtime:2.7.0")
  implementation("androidx.room:room-ktx:2.7.0")
  implementation("androidx.datastore:datastore-preferences:1.1.7")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("com.anthropic:anthropic-java:2.34.0")
  implementation("dev.chrisbanes.haze:haze:1.6.10")
  implementation("androidx.work:work-runtime-ktx:2.10.0")
  implementation("com.google.android.gms:play-services-auth:21.2.0")
  implementation("com.google.mlkit:text-recognition:16.0.1")
  implementation("com.google.android.gms:play-services-ads:23.6.0")
  implementation("com.android.billingclient:billing:8.0.0")
  implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
  implementation("com.google.firebase:firebase-auth")
  implementation("com.google.firebase:firebase-firestore")
  implementation("androidx.credentials:credentials:1.5.0")
  implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
  ksp("androidx.room:room-compiler:2.7.0")
}
