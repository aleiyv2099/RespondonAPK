# Respondon

App Android nativa (Kotlin) que detecta automaticamente los screenshots que
tomas, extrae el texto con OCR local y se lo manda a DeepSeek para mostrarte
la respuesta en una burbuja flotante sobre cualquier app.

## Como funciona

1. Un `ContentObserver` escucha el `MediaStore` y detecta imagenes nuevas cuyo
   nombre contiene "screenshot" o "captura" (creadas en los ultimos 15 segundos).
2. ML Kit Text Recognition extrae el texto de la imagen, todo en el dispositivo
   (offline, no se sube la imagen a ningun lado).
3. El texto se envia a la API de DeepSeek (`deepseek-reasoner`) pidiendo que
   responda la pregunta o resuma el contenido.
4. La respuesta aparece en un panel flotante translucido (overlay) que se puede
   arrastrar y cerrar.

## Componentes

- `MainActivity`: pantalla de configuracion. Guarda la API key, gestiona los
  permisos e inicia/detiene el servicio.
- `BubbleService`: foreground service que dibuja la burbuja, observa el
  MediaStore, corre el OCR y llama a DeepSeek.
- `DeepSeekClient`: cliente HTTP minimo para la API de DeepSeek (bloqueante,
  se usa desde un hilo de fondo).

## Requisitos

- Android 8.0+ (minSdk 26, targetSdk 35).
- JDK 17 y Android SDK con compileSdk 35 para compilar.
- Una API key de DeepSeek (https://platform.deepseek.com). Se ingresa en la app
  y se guarda en `SharedPreferences`; nunca va en el codigo ni en el repo.

## Compilar e instalar

```bash
gradle assembleDebug
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/r.apk
adb shell pm install -r /data/local/tmp/r.apk
```

Nota: en algunos dispositivos (ej. TECNO POVA 5) `adb install` por streaming
falla con `INSTALL_PARSE_FAILED_NOT_APK`; por eso se instala con push + `pm install`.

## Permisos

- **Mostrar sobre otras apps** (overlay): para la burbuja flotante.
- **Fotos y videos** (`READ_MEDIA_IMAGES`): para detectar los screenshots.

Importante (Android 14+): el acceso parcial a fotos ("Seleccionar fotos") NO
sirve, porque el MediaStore solo devuelve las fotos elegidas a mano y los
screenshots nuevos nunca llegan. Hay que dar "Permitir todo". La app detecta el
acceso parcial y redirige a Ajustes.

## Tests

```bash
gradle test
```
