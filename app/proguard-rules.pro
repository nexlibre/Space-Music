# R8 is on for release for speed, not size — see the note on isMinifyEnabled in
# build.gradle.kts. These rules keep it away from everything that finds classes
# by name at runtime, where a missing class shows up as a crash on a device
# rather than as a build error.

# Nothing is renamed. Stack traces stay readable, and every lookup by name —
# enum names in preferences, Media3 custom commands, library reflection — keeps
# working without a rule of its own.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*

# ---- Script engines and extractors ------------------------------------------
# Rhino compiles and runs YouTube's player JavaScript and binds Java classes into
# it reflectively; NewPipe and InnerTubeX drive it and parse with nanojson/jsoup.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class org.schabi.newpipe.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.metrolist.** { *; }
-keep class com.dokar.quickjs.** { *; }

# ---- Native and crypto --------------------------------------------------------
# ONNX Runtime and QuickJS are called from JNI, which R8 cannot see. SMBJ picks
# its BouncyCastle providers and event bus handlers reflectively.
-keep class ai.onnxruntime.** { *; }
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keep class org.bouncycastle.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
# The vendored FFmpeg extension's JNI calls back into this by name to grow
# its output buffer (native/ffmpeg/ffmpeg_jni.cc, JNI_OnLoad).
-keep,includedescriptorclasses class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder {
    private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}

# ---- Serialization --------------------------------------------------------------
# protobuf-lite reads its generated messages' fields reflectively.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# Ktor resolves serializers and engine pieces by type at runtime.
-keep class io.ktor.** { *; }
# Our own @Serializable models, whole: kotlinx.serialization's bundled rules
# cover the generated serializers, and keeping the classes too costs little.
-keep @kotlinx.serialization.Serializable class com.music.bitchord.** { *; }
-keep @kotlinx.serialization.Serializable class com.my.kizzy.** { *; }

# ---- WebView bridges ------------------------------------------------------------
# PoToken minting and the Spotify token page call back into these from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- JVM-only APIs ------------------------------------------------------------
# Referenced by desktop-only paths in Rhino (JSR-223, java.beans, dynalink),
# mbassy's EL filters, SMBJ's Kerberos and Ktor's IntelliJ debugger probe. None
# of them exist on Android and none of those paths run here — the debug build,
# which R8 never touches, has always lived without them.
-dontwarn java.beans.**
-dontwarn java.lang.management.**
-dontwarn javax.el.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.ietf.jgss.**

# Star Music's signed-release instrumentation checks exercise the same persisted
# models and local MediaItem conversion as the UI. Keep their public contract
# available in the optimized APK so the independent test APK can call it.
-keep class com.music.bitchord.data.DevicePlaylistStore { *; }
-keep class com.music.bitchord.data.DevicePlaylist { *; }
-keep class com.music.bitchord.data.model.Song { *; }
-keep class com.music.bitchord.playback.PlayerConnectionKt {
    public static androidx.media3.common.MediaItem toMediaItem(com.music.bitchord.data.model.Song);
}

# AndroidJUnitRunner calls Trace from its separate APK. Gradle excludes the
# shared dependency from the runner, so it must survive shrinking in the app.
-keep class androidx.tracing.** { *; }

# The independent instrumentation APK shares Kotlin and playback dependencies
# with the app. Keep the runtime facades and the public API it exercises; R8
# otherwise inlines them out of the app while Gradle removes duplicate test deps.
-keep class kotlin.** { *; }
-keep interface androidx.media3.common.Player { *; }
-keep interface androidx.media3.common.Player$Listener { *; }
-keep interface androidx.media3.exoplayer.ExoPlayer { *; }
-keep class androidx.media3.exoplayer.ExoPlayer$Builder { *; }
-keep class androidx.media3.common.MediaItem { *; }
-keep class androidx.media3.common.MediaItem$LocalConfiguration { *; }

# The optimized APK is also exercised by its independent instrumentation APK.
# Preserve only the additional public interfaces used for real service playback.
-keep class com.music.bitchord.data.Http { public *; }
-keep class com.music.bitchord.playback.PlayerDeepLink { public *; }
-keep class com.music.bitchord.playback.PlayerConnectionKt {
    public static *** playSongs(androidx.media3.session.MediaController, java.util.List, int, kotlin.coroutines.Continuation);
    public static *** toSong(androidx.media3.common.MediaItem);
}
-keep class androidx.media3.session.MediaController { public *; }
-keep class androidx.media3.session.MediaController$Builder { public *; }
-keep class androidx.media3.session.SessionToken { public *; }
-keep class androidx.media3.common.PlaybackException { public *; }
-keep class okhttp3.Request$Builder { public *; }

# Signed release instrumentation and actual local playback rely on coroutines
# facades across the app/test APK boundary. The optimized beta previously
# omitted BuildersKt (API 33/35 device tests, run 38665608435), resulting in
# NoClassDefFoundError during playlist playback. Keep their runtime contract.
-keep class kotlinx.coroutines.** { *; }

# OkHttp's instance method newCall(Request) was optimized away from the signed
# app even though the separate instrumentation APK invokes it (same run).
# Preserve the callable API and the Request/Call types across this boundary.
-keep class okhttp3.OkHttpClient { public *; }
-keep interface okhttp3.Call { *; }
-keep class okhttp3.Request { public *; }

# Local lyric persistence is verified from the separate release test APK.
-keep class com.music.bitchord.data.lyrics.LocalLyricsStore { public *; }
-keep class com.music.bitchord.data.lyrics.EmbeddedLyrics { public *; }
-keep class com.music.bitchord.data.lyrics.LyricLine { public *; }

# Persisted playlist sorting modes are also part of the release instrumentation API.
# Prevent enum unboxing from removing fields used by the separately compiled runner.
-keep enum com.music.bitchord.data.PlaylistSort { *; }

# Spatial release instrumentation is compiled as a separate APK. Preserve its
# public model/settings/DSP boundary instead of allowing R8 to inline it away.
-keep enum com.music.bitchord.playback.SpatialMode { *; }
-keep class com.music.bitchord.playback.SpatialSettings { public *; }
-keep class com.music.bitchord.playback.SpatialParameters { public *; }
-keep class com.music.bitchord.playback.SpatialAudioProcessor { public *; }
-keep class com.music.bitchord.playback.SpatialSceneEngine { public *; }
-keep class com.music.bitchord.playback.audio.AudioBlock { public *; }
-keep class com.music.bitchord.data.settings.AppSettings {
    public static *** INSTANCE;
    public kotlinx.coroutines.flow.MutableStateFlow getSpatialScene();
    public kotlinx.coroutines.flow.MutableStateFlow getSpatialAudio();
    public void setSpatialScene(com.music.bitchord.playback.SpatialSettings);
    public void setSpatialAudio(boolean);
    public void reload();
}
-keep class androidx.media3.common.audio.BaseAudioProcessor { public *; }
-keep interface androidx.media3.common.audio.AudioProcessor { public *; }
