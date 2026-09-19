import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Entregable 11 de la guia de cierre: la URL fija http://10.0.2.2:8000/ solo funciona en el
// emulador (es el alias que el emulador usa para el localhost del host). Contra un dispositivo
// fisico con "adb reverse tcp:8000 tcp:8000" hace falta http://127.0.0.1:8000/ -- no la IP de
// LAN del host, como decia el comentario de mas abajo hasta esta entrega, ni el alias del
// emulador. Antes, probar contra un dispositivo fisico exigia editar este archivo a mano (sin
// versionar el cambio, asi que un APK asi no se podia reproducir desde un clon limpio). Ahora
// se lee de local.properties (no versionado, igual que sdk.dir), con el mismo valor de siempre
// como respaldo si no se define nada.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}
val mobileBaseUrl: String = (localProperties.getProperty("mobileBaseUrl") ?: "http://10.0.2.2:8000/")

android {
    namespace = "ec.edu.uteq.soporte.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "ec.edu.uteq.soporte.mobile"
        // SDK minimo 26 (Android 8) y objetivo 34, exigidos por la Guia de Entrega 4, Modulo C.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Por defecto, 10.0.2.2 es el alias con el que el EMULADOR de Android alcanza el
        // localhost del host. Para un dispositivo fisico con "adb reverse tcp:8000 tcp:8000"
        // hace falta 127.0.0.1; para un dispositivo fisico en la misma red sin adb reverse,
        // la IP de LAN del host. Se define agregando "mobileBaseUrl=http://<host>:8000/" a
        // local.properties (no versionado) -- ver apps/mobile/README.md. Desde la Entrega 4
        // (Modulo B/D4.1) ambos clientes pasan por el API Gateway unico (services/api-gateway,
        // puerto 8000) en vez de hablarle directo a cada microservicio -- las rutas de
        // AuthApi/TicketApi ya incluyen "api/v1/..." completo, asi que el gateway las enruta
        // sin reescritura.
        buildConfigField("String", "AUTH_BASE_URL", "\"$mobileBaseUrl\"")
        buildConfigField("String", "TICKETS_BASE_URL", "\"$mobileBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // El backend local corre en HTTP plano, sin certificado -- necesario solo para
            // desarrollo contra el docker-compose local, nunca para un build de produccion real.
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // --- UI (Jetpack Compose) ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // Solo para el modifier pullRefresh (Material3 en la version del BOM usada aun no trae
    // su propio pull-to-refresh estable); coexiste sin problema con Material 3.
    implementation("androidx.compose.material:material")
    // Iconos ademas de los ~20 basicos que trae material3 por defecto (Email/Lock/
    // Visibility/VisibilityOff de LoginScreen.kt no estan en ese set reducido). Version
    // alineada automaticamente por composeBom, sin declararla aqui.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- Datos remotos (Retrofit + OkHttp) ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // --- Datos locales (Room) -- cache para el modo sin conexion ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Sesion segura (JWT) ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // --- Capacidades del dispositivo: camara + geolocalizacion ---
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- Vista previa de la foto de evidencia ---
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- Corrutinas ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Pruebas unitarias (ViewModels): JUnit 5 + coroutines-test ---
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("io.mockk:mockk:1.13.11")

    // --- Pruebas instrumentadas (E2E en dispositivo/emulador) ---
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
