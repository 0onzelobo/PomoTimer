# PomoTimer

Timer Pomodoro para Android, bonito e privado: vidro fosco, modo escuro/claro, medidor em arco,
resumo com gráfico, heatmap e horários de foco. **Sem internet, sem anúncios, sem rastreadores.**

## Instalar
Baixe o `.apk` mais recente em **Releases** e confira a assinatura (veja `SECURITY.md`).

A impressão digital SHA-256 do certificado de release será publicada junto ao primeiro APK oficial.

## Compilar
- **Requisitos**: JDK 17 e Android SDK 36. O app é compatível com Android 8.0 (API 26) ou superior.
- **Android Studio**: abra a pasta do projeto e escolha *Build › Build APK(s)*.
- **Terminal** (com JDK 17 e Android SDK 36):
  ```powershell
  .\gradlew.bat lintDebug assembleDebug --no-daemon
  ```
  O APK de teste será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### Release assinado
Configure localmente `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`, depois execute
`.\gradlew.bat assembleRelease --no-daemon`. A keystore **nunca** deve ir para o repositório (`.gitignore`
bloqueia `*.jks` e `*.keystore`).

## Estrutura
- `app/src/main/assets/index.html` — interface completa (HTML/CSS/JS, sem rede, CSP estrita)
- `MainActivity.kt` — WebView endurecido + ponte `window.Android` com entrada validada
- `Alarms.kt` — AlarmManager (exato quando permitido) e notificações

Licença MIT · Fonte Inter sob OFL 1.1 · Privacidade: `PRIVACY.md`
