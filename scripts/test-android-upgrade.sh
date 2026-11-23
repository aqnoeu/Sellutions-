#!/usr/bin/env bash
set -euo pipefail

OLD=/tmp/sellutions/v0.1.0/sellutions-release.apk
NEW=/tmp/sellutions/v0.1.1/sellutions-release.apk
PKG=com.aqnoeu.sellutions

test -s "$OLD"
test -s "$NEW"

echo "Instalando Sellutions v0.1.0..."
adb install "$OLD"
adb shell pm path "$PKG" | grep -q 'package:'
adb shell am start -n "$PKG/.MainActivity"
sleep 3

echo "Atualizando por cima para Sellutions v0.1.1..."
adb install -r "$NEW"
adb shell pm path "$PKG" | grep -q 'package:'
adb shell am start -n "$PKG/.MainActivity"
sleep 3

# O PackageManager deve mostrar versão 1001 (antes era 1000).
package_details=$(adb shell dumpsys package "$PKG")
printf '%s\n' "$package_details" | grep -E 'versionCode=1001' >/dev/null || {
  echo "::error::Android não instalou a versão atualizada (versionCode=1001)."
  exit 1
}
echo "SUCESSO: v0.1.0 → v0.1.1 instalada diretamente, sem desinstalar."
