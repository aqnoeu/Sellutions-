#!/usr/bin/env bash
set -euo pipefail

package="com.aqnoeu.sellutions"
previous="${RUNNER_TEMP}/previous-apk/sellutions-release.apk"
new_apk="sellutions-release.apk"

if [[ -f "$previous" ]]; then
  echo "Instalando APK anterior para testar atualização..."
  adb install -r "$previous"
fi

echo "Instalando APK assinado da nova versão..."
adb install -r "$new_apk"

echo "Conferindo versionCode instalado..."
adb shell dumpsys package "$package" | grep -E "versionCode=${SELLUTIONS_VERSION_CODE}([[:space:]]|$)"

echo "Abrindo o Sellutions..."
adb shell am start -W -n "$package/.MainActivity"
sleep 5
adb shell pidof "$package" | grep -Eq '[0-9]+'

echo "Teste aprovado: APK instalado, versão conferida e aplicativo iniciado."
