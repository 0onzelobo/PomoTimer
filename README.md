# PomoTimer

Timer Pomodoro para Android, bonito e privado: vidro fosco, modo escuro/claro, medidor em arco,
resumo com gráfico, heatmap e horários de foco. **Sem internet, sem anúncios, sem rastreadores.**

## Instalar
Baixe o `.apk` mais recente em **Releases** e confira a assinatura (veja `SECURITY.md`).

Impressão digital SHA-256 do certificado de release (`CN=PomoTimer`):
```
CE:5A:10:F0:88:C8:AA:BF:69:21:27:4C:0C:40:EF:67:D0:0E:B3:60:56:18:6D:5D:DA:A4:92:3B:DB:3A:E8:A5
```

## Compilar
- **Requisitos**: JDK 17 e Android SDK 36. O app é compatível com Android 8.0 (API 26) ou superior.
- **Android Studio**: abra a pasta do projeto e escolha *Build › Build APK(s)*.
- **Terminal** (com JDK 17 e Android SDK 36):
  ```powershell
  .\gradlew.bat lintDebug assembleDebug --no-daemon
  ```
  O APK de teste será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

### Compilação automática (GitHub Actions)
- **A cada envio para a `main`**: roda o lint e compila um APK de teste, disponível em
  *Actions › execução › Artifacts*. Ele é assinado com uma chave de debug temporária, então para
  instalar por cima de outro build é preciso desinstalar o anterior.
- **Nova versão**: crie e envie uma tag `vX.Y.Z`. O workflow *Release* compila o APK assinado e
  publica em **Releases** com o checksum SHA-256 e a impressão digital do certificado.
  ```powershell
  git tag v1.0.0
  git push origin v1.0.0
  ```
  O `versionCode` sai da tag (`1.2.3` → `10203`), então cada versão nova precisa de um número maior.
  Os secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` precisam estar em
  *Settings › Secrets and variables › Actions*.

### Release assinado local
Configure localmente `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`, depois execute
`.\gradlew.bat assembleRelease --no-daemon`. A keystore **nunca** deve ir para o repositório (`.gitignore`
bloqueia `*.jks` e `*.keystore`).

## Estrutura
- `app/src/main/assets/index.html` — interface completa (HTML/CSS/JS, sem rede, CSP estrita)
- `MainActivity.kt` — WebView endurecido + ponte `window.Android` com entrada validada
- `Alarms.kt` — AlarmManager (exato quando permitido) e notificações

Licença MIT · Fonte Inter sob OFL 1.1 · Privacidade: `PRIVACY.md`
