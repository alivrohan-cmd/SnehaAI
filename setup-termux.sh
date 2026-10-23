#!/data/data/com.termux/files/usr/bin/bash
# একবারই চালাতে হবে। Termux (F-Droid/GitHub সংস্করণ) দরকার।
set -e
pkg update -y
pkg install -y openjdk-17 aapt2 curl unzip zip git
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
SDK="$HOME/android-sdk"
mkdir -p "$SDK/cmdline-tools"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  curl -L -o "$HOME/cmdtools.zip" "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  unzip -q "$HOME/cmdtools.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm "$HOME/cmdtools.zip"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-34" "build-tools;34.0.0"
echo "SDK প্রস্তুত: $SDK"
