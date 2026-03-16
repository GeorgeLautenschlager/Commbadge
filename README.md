# Combadge

A Star Trek TNG combadge voice communication app for Android. Multiple phones on the same Wi-Fi network discover each other automatically. Tap the badge, say a name, and a full-duplex voice channel opens — no servers, no cellular, no accounts.

---

## Building the APK (Linux / WSL2)

You need the Android command-line tools. No Android Studio required.

### 1 — Install Java 17

**Debian / Ubuntu / WSL2:**
```bash
sudo apt update
sudo apt install openjdk-17-jdk
java -version   # should print openjdk 17...
```

### 2 — Install the Android SDK command-line tools

Download the "Command line tools only" package from Google. This is a ~130 MB zip — no full Android Studio download needed.

```bash
# Create a home for the SDK
mkdir -p ~/android-sdk/cmdline-tools

# Download the latest command-line tools (check https://developer.android.com/studio for current URL)
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
     -O /tmp/cmdline-tools.zip

# Extract into the right place — the subfolder MUST be named "latest"
unzip /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted
mv /tmp/cmdline-tools-extracted/cmdline-tools ~/android-sdk/cmdline-tools/latest

# Add to your PATH (add these lines to ~/.bashrc or ~/.zshrc too)
export ANDROID_HOME=~/android-sdk
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

source ~/.bashrc   # or restart your terminal
```

### 3 — Install the required SDK components

```bash
# Accept all licenses first (type 'y' repeatedly, or pipe yes)
yes | sdkmanager --licenses

# Install the build tools and platform the app targets
sdkmanager "platform-tools" \
           "platforms;android-34" \
           "build-tools;34.0.0"
```

This downloads another ~200 MB. Go make a coffee.

### 4 — Clone the repo and build

```bash
git clone https://github.com/GeorgeLautenschlager/Commbadge.git
cd Commbadge

# Make the Gradle wrapper executable
chmod +x gradlew

# Build a debug APK (fastest — no signing required)
./gradlew assembleDebug
```

Gradle will download its own dependencies on the first run (~500 MB, cached after that). The build takes 2–5 minutes the first time, under a minute after.

Your APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 5 — Install on a device

**Option A — USB cable**

Enable Developer Options on your phone (`Settings → About phone → tap Build number 7 times`), then enable USB Debugging.

```bash
adb devices          # confirm your phone shows up
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Option B — Wi-Fi (no cable)**

With the phone on the same network:

```bash
# On the phone: Settings → Developer options → Wireless debugging → enable
# Tap "Pair device with pairing code" and note the IP:port and pairing code shown

adb pair <IP>:<port>       # enter the 6-digit pairing code when prompted
adb connect <IP>:5555      # use the IP from above, port is usually 5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Option C — copy the file manually**

Copy `app-debug.apk` to your phone via USB file transfer or cloud storage, open it in a file manager, and install. You'll need to allow "Install from unknown sources" for your file manager app in Settings.

---

## Building a release APK (optional)

The debug APK works fine for personal use on your own devices. If you want a smaller, optimized APK without debug overhead:

### Create a signing keystore (one-time setup)

```bash
keytool -genkeypair \
  -keystore ~/combadge-release-key.jks \
  -alias combadge \
  -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for a password and some name fields. Remember the password.

### Configure signing in the project

Create a file called `keystore.properties` in the project root (next to `settings.gradle.kts`). **Do not commit this file.**

```
storeFile=/home/YOUR_USERNAME/combadge-release-key.jks
storePassword=your_keystore_password
keyAlias=combadge
keyPassword=your_key_password
```

Add this to the top of `app/build.gradle.kts`:

```kotlin
import java.util.Properties

val keystoreProps = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) keystoreProps.load(keystoreFile.inputStream())
```

And add a `signingConfigs` block inside the `android {}` block, before `buildTypes`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = keystoreProps["storeFile"]?.let { file(it) }
        storePassword = keystoreProps["storePassword"] as String?
        keyAlias = keystoreProps["keyAlias"] as String?
        keyPassword = keystoreProps["keyPassword"] as String?
    }
}
buildTypes {
    release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("release")
    }
}
```

Then build:

```bash
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/app-release.apk
```

---

## Troubleshooting

**`JAVA_HOME` not found / wrong Java version**
```bash
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
```

**`SDK location not found` error**
```bash
export ANDROID_HOME=~/android-sdk
# Or create a local.properties file in the project root:
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

**WSL2: `adb` can't see USB device**
WSL2 doesn't have native USB support. Use [usbipd-win](https://github.com/dorssel/usbipd-win) to forward the USB device into WSL, or just copy the APK file to Windows and run `adb` from a Windows terminal instead.

**Gradle download is very slow**
The first build downloads Gradle 8.6 (~130 MB) and all dependencies. If it times out, just run `./gradlew assembleDebug` again — it resumes from where it left off.

**`Permission denied` on `./gradlew`**
```bash
chmod +x gradlew
```

---

## Runtime requirements

- Android 8.0 (API 26) or newer
- Wi-Fi connection (the app warns you if you're on mobile data)
- Microphone permission — granted on first launch
- All devices must be on the **same Wi-Fi network / subnet**
