#!/bin/bash
set -e
R=$PWD
P=$R/proj/app/src/main
mkdir -p $P/java/com/your/eraser $P/assets $P/res/values $P/res/drawable $P/res/mipmap-anydpi-v26
cd proj

cat > settings.gradle <<'EOF'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "YourEraser"
include(":app")
EOF

cat > build.gradle <<'EOF'
plugins {
    id 'com.android.application' version '8.5.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.24' apply false
}
EOF

cat > gradle.properties <<'EOF'
android.useAndroidX=true
org.gradle.jvmargs=-Xmx3g
EOF

cat > app/build.gradle <<'EOF'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}
android {
    namespace 'com.your.eraser'
    compileSdk 34
    defaultConfig {
        applicationId 'com.your.eraser'
        minSdk 29
        targetSdk 34
        versionCode 2
        versionName '1.1'
    }
    androidResources { noCompress 'onnx' }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = '17' }
}
dependencies {
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.19.2'
}
EOF

cat > $P/AndroidManifest.xml <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Your Eraser"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:largeHeap="true"
        android:theme="@style/Theme.YourEraser">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > $P/res/values/themes.xml <<'EOF'
<resources>
    <style name="Theme.YourEraser" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
EOF

cat > $P/res/values/colors.xml <<'EOF'
<resources>
    <color name="ic_bg">#4F378B</color>
</resources>
EOF

cat > $P/res/mipmap-anydpi-v26/ic_launcher.xml <<'EOF'
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_bg" />
    <foreground android:drawable="@drawable/ic_launcher_fg" />
</adaptive-icon>
EOF

cat > $P/res/drawable/ic_launcher_fg.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFD8E4" android:pathData="M28,58L56,30L68,42L40,70Z" />
    <path android:fillColor="#EADDFF" android:pathData="M40,70L68,42L80,54L52,82Z" />
</vector>
EOF

cat > $P/res/drawable/ic_photo.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#000000" android:pathData="M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z" />
</vector>
EOF

cat > $P/res/drawable/ic_undo.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#000000" android:pathData="M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z" />
</vector>
EOF

cat > $P/res/drawable/ic_save.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#000000" android:pathData="M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z" />
</vector>
EOF

cp "$R/.github/workflows/PaintView.kt" "$R/.github/workflows/MainActivity.kt" "$P/java/com/your/eraser/"
