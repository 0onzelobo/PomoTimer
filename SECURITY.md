# Segurança

Encontrou uma vulnerabilidade? Não abra issue pública: use **Security → Report a vulnerability**
(GitHub Private Vulnerability Reporting) neste repositório.

## Como conferir se o APK é oficial
Todo APK oficial distribuído pelo mantenedor deve vir acompanhado de um checksum SHA-256 e da
impressão digital do certificado de assinatura.

```bash
sha256sum -c pomotimer-X.Y.Z.apk.sha256
apksigner verify --print-certs pomotimer-X.Y.Z.apk
```
A impressão digital SHA-256 do certificado tem que ser igual à publicada no README.
