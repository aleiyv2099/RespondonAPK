# Respondon

App Android nativa que detecta automáticamente tus screenshots, extrae el texto con OCR local y responde la pregunta con DeepSeek en una burbuja flotante sobre cualquier app.

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![ML Kit](https://img.shields.io/badge/ML%20Kit-4285F4?style=for-the-badge&logo=google&logoColor=white)
![DeepSeek](https://img.shields.io/badge/DeepSeek-FF6F00?style=for-the-badge&logoColor=white)

---

## Pipeline

```
  screenshot      +-----------------+    +-----------+    +------------------+    +----------+
 del usuario ---->| ContentObserver |--->| OCR local |--->| DeepSeek         |--->| Burbuja  |
                  | (MediaStore)    |    | (ML Kit)  |    | deepseek-reasoner|    | overlay  |
                  +-----------------+    +-----------+    +------------------+    +----------+
                         |                    |                   |                    |
                  detecta imagenes       extrae el texto     responde la          panel flotante
                  nuevas "screenshot"    en el dispositivo   pregunta o           translucido y
                  o "captura" (<15s)     (offline)           resume               arrastrable
```

---

## ¿Cómo funciona?

1. Un `ContentObserver` escucha el **MediaStore** y detecta imágenes nuevas (últimos 15 segundos) cuyo nombre contiene "screenshot" o "captura"
2. **ML Kit Text Recognition** extrae el texto de la imagen, 100% en el dispositivo — la imagen nunca sale del teléfono
3. El texto se envía a **DeepSeek** (`deepseek-reasoner`) pidiendo que responda la pregunta o resuma el contenido
4. La respuesta aparece en una **burbuja flotante translúcida** (overlay) visible sobre cualquier app

---

## Funcionalidades

- **Detección automática de screenshots** — sin abrir la app, solo toma la captura
- **OCR offline** — ML Kit procesa la imagen localmente, sin subirla a ningún servidor
- **Respuesta con razonamiento** — usa `deepseek-reasoner` para preguntas complejas
- **Burbuja overlay** — panel flotante arrastrable que funciona sobre cualquier app
- **API key segura** — se guarda en `SharedPreferences`, nunca en el código ni en el repo
- **Detección de permiso parcial** — si Android 14 da acceso parcial a fotos, la app lo detecta y redirige a Ajustes

---

## Stack

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Kotlin (JVM 17) |
| Plataforma | Android nativo (minSdk 26, targetSdk 35) |
| OCR | ML Kit Text Recognition (on-device) |
| IA | DeepSeek API (`deepseek-reasoner`) |
| Overlay | `WindowManager` + `TYPE_APPLICATION_OVERLAY` |
| Detección | `ContentObserver` sobre `MediaStore.Images` |
| Persistencia | `SharedPreferences` |

---

## Estructura

```
Respondon/
├── app/src/main/java/com/mickaell/respondon/
│   ├── MainActivity.kt      # configuracion: API key, permisos, iniciar/detener servicio
│   ├── BubbleService.kt     # foreground service: burbuja, observer, OCR y llamada a DeepSeek
│   └── DeepSeekClient.kt    # cliente HTTP minimo para la API de DeepSeek
├── app/src/main/res/        # layouts del panel flotante y la pantalla principal
└── app/src/test/            # tests unitarios (parseo de respuesta DeepSeek)
```

---

## Compilar e instalar

```bash
gradle assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/r.apk
adb shell pm install -r /data/local/tmp/r.apk
```

Requiere JDK 17 y Android SDK con compileSdk 35.

> **Nota:** en algunos dispositivos (ej. TECNO POVA 5) `adb install` por streaming falla con `INSTALL_PARSE_FAILED_NOT_APK`; por eso se instala con push + `pm install`.

---

## Configuración

1. Consigue una API key en [platform.deepseek.com](https://platform.deepseek.com)
2. Abre la app, pega la key y guárdala (queda en `SharedPreferences`)
3. Concede los permisos y toca **Iniciar**

### Permisos

| Permiso | Para qué |
|---------|----------|
| Mostrar sobre otras apps | Dibujar la burbuja flotante |
| Fotos y videos (`READ_MEDIA_IMAGES`) | Detectar los screenshots nuevos |

> **Importante (Android 14+):** el acceso parcial a fotos ("Seleccionar fotos") **no sirve** — el MediaStore solo devuelve las fotos elegidas a mano y los screenshots nuevos nunca llegan. Hay que dar **"Permitir todo"**. La app detecta el acceso parcial y redirige a Ajustes.

---

## Tests

```bash
gradle test
```
