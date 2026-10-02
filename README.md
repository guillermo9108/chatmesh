# ChatMesh 📡💬

Aplicación nativa de mensajería y llamadas fuera de línea (offline) estilo WhatsApp que opera sobre **WiFi Direct (P2P)** y **WiFi Aware**, sin necesidad de Internet, servidores externos, datos móviles ni saldo.

---

## 🚀 Compilación Automática en GitHub Actions
Este proyecto incluye un flujo de integración continua (`.github/workflows/android.yml`) que compila automáticamente el APK con cada push o de forma manual:

1. Ve a la pestaña **Actions** en tu repositorio de GitHub.
2. Selecciona **Android CI / Build & Auto-Repair**.
3. Pulsa en **Run workflow**.
4. Al finalizar, descarga el APK listo desde la sección **Artifacts** o desde **Releases**.

---

## 💻 Compilación Local en Android Studio / Terminal
- **JDK requerido:** Java 21 (requerido por AGP 9.1 y Gradle 9.3)
- **Windows:** `gradlew.bat assembleDebug`
- **Linux / Mac:** `./gradlew assembleDebug`
