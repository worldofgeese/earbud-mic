#!/usr/bin/env bash
# Builds build/earbud-mic.apk with Nix-provided tools (no Gradle).
# Getting the SDK from androidenv accepts the Android SDK licence.
# Set ANDROID_HOME to use an existing SDK with build-tools 36.1.0 and platform 36.
set -euo pipefail
cd "$(dirname "$0")"

if [[ -z "${ANDROID_HOME:-}" ]]; then
  sdk_out=$(nix build --impure --no-link --print-out-paths --expr '
    let pkgs = import (builtins.getFlake "nixpkgs") {
      system = builtins.currentSystem;
      config = { allowUnfree = true; android_sdk.accept_license = true; };
    };
    in (pkgs.androidenv.composeAndroidPackages {
      platformVersions = [ "36" ];
      buildToolsVersions = [ "36.1.0" ];
      includeEmulator = false; includeSystemImages = false; includeNDK = false;
    }).androidsdk')
  ANDROID_HOME=$sdk_out/libexec/android-sdk
fi
tools=$ANDROID_HOME/build-tools/36.1.0
jar=$ANDROID_HOME/platforms/android-36/android.jar
jdk=$(nix build --no-link --print-out-paths nixpkgs#jdk17 | head -n1)
export PATH=$jdk/bin:$PATH

rm -rf build
mkdir -p build/gen build/classes
"$tools/aapt2" compile --dir res -o build/res.zip
"$tools/aapt2" link -I "$jar" --manifest AndroidManifest.xml --java build/gen \
  --min-sdk-version 31 --target-sdk-version 36 --version-code 1 --version-name 1.0 \
  -o build/unsigned.apk build/res.zip
# JDK platform classes plus android.jar on the classpath; d8 desugars the lambdas.
javac --release 17 -classpath "$jar" -d build/classes \
  $(find src build/gen -name '*.java')
"$tools/d8" --release --min-api 31 --lib "$jar" --output build \
  $(find build/classes -name '*.class')
(cd build && zip -q unsigned.apk classes.dex)
"$tools/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

# Keep this keystore: updates must be signed with the same key to install over the old app.
if [[ ! -f signing.keystore ]]; then
  keytool -genkeypair -keystore signing.keystore -storepass android -keypass android \
    -alias earbudmic -keyalg RSA -keysize 2048 -validity 10000 -dname CN=EarbudMic
fi
"$tools/apksigner" sign --ks signing.keystore --ks-pass pass:android \
  --out build/earbud-mic.apk build/aligned.apk
echo "Built build/earbud-mic.apk"
