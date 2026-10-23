#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-17-openjdk}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
GRADLE_VER=8.5
GRADLE_HOME="$HOME/gradle-$GRADLE_VER"

command -v java >/dev/null || { echo "JDK নেই। চালান: ./setup-termux.sh"; exit 1; }
[ -d "$ANDROID_HOME/platforms/android-34" ] || { echo "Android SDK নেই। চালান: ./setup-termux.sh"; exit 1; }
[ -x "$PREFIX/bin/aapt2" ] || { echo "aapt2 নেই। চালান: pkg install aapt2"; exit 1; }

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  echo "Gradle $GRADLE_VER নামানো হচ্ছে..."
  curl -L -o "$HOME/gradle.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
  unzip -q "$HOME/gradle.zip" -d "$HOME" && rm "$HOME/gradle.zip"
fi

echo "sdk.dir=$ANDROID_HOME" > local.properties
"$GRADLE_HOME/bin/gradle" --no-daemon \
  -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2" \
  assembleDebug

APK=app/build/outputs/apk/debug/app-debug.apk
if [ -f "$APK" ]; then
  echo "সফল! APK: $(pwd)/$APK"
  ls -lh "$APK"
else
  echo "APK তৈরি হয়নি — উপরের এরর দেখুন (README-র Troubleshooting)"; exit 1
fi
