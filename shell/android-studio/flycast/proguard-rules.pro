# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# JNI entry points: native methods are exported using their Java names.
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}

# Native code resolves these callbacks and classes by string through JNI.
-keep class com.flycast.emulator.emu.JNIdc { *; }
-keep class com.flycast.emulator.emu.LocaleUtils {
    private static java.lang.String formatShortDateTime(long);
    private static java.lang.String getCurrentLocale();
}
-keep class com.flycast.emulator.Emulator {
    public void SaveAndroidSettings(java.lang.String);
    public void enableNetworkBroadcast(boolean);
}
-keep class com.flycast.emulator.BaseGLActivity {
    public void showAlertDialog(java.lang.String);
    public void onGameStateChange(boolean);
    public java.lang.String getNativeLibDir();
    public java.lang.String getInternalFilesDir();
}
-keep class com.flycast.emulator.NativeGLActivity {
    public void showScreenKeyboard(boolean);
    public void setVGamepadEditMode(boolean);
}
-keep class com.flycast.emulator.periph.SipEmulator {
    public byte[] getData(int);
    public void startRecording(int);
    public void stopRecording();
}
-keep class com.flycast.emulator.emu.AudioBackend {
    private boolean init(int);
    private void term();
    private int writeBuffer(short[], boolean);
}
-keep class com.flycast.emulator.periph.InputDeviceManager {
    private boolean rumble(int, float, float, int);
}
-keep class com.flycast.emulator.emu.HttpClient {
    public void init(java.lang.String);
    public int openUrl(java.lang.String, byte[][], java.lang.String[], java.lang.String[], java.lang.String[][], java.lang.String[][]);
    public int post(java.lang.String, java.lang.String[], java.lang.String[], java.lang.String[]);
    public int post(java.lang.String, java.lang.String, java.lang.String, byte[][]);
}
-keep class com.flycast.emulator.AndroidStorage { *; }
-keep class com.flycast.emulator.FileInfo { *; }

# Native callbacks used by the Android Game SDK's bundled JNI library.
-keep class com.google.androidgamesdk.** { *; }

# Preserve source line numbers in release crash reports without keeping original filenames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Apache HttpClient contains optional Kerberos support unavailable on Android.
-dontwarn org.ietf.jgss.**
