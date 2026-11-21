# TinyChat 的 R8 保留规则（M9-4，2026-11-21）
#
# 只写「R8 看不见调用点、只能靠反射/约定找到」的那几类东西。其余一律交给 R8 默认规则和
# 各库自带的 consumer rules —— 手写大范围 -keep 会把体积优化全部抵消。

# ---------------------------------------------------------------- 通用属性
# 注解、泛型签名、内部类信息：序列化与 Room 生成代码都要读它们。
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# ---------------------------------------------------------------- kotlinx.serialization
# 报文（core:net）、消息片段（core:model）、归档 v3（core:data）都靠编译器生成的序列化器，
# 这些 `$$serializer` 类只在生成的代码里被直接引用，R8 一旦重命名/裁掉，运行时就抛
# "Serializer for class ... is not found"。
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.tinychat.**$$serializer { *; }
-keepclassmembers class io.tinychat.** {
    *** Companion;
}
-keepclasseswithmembers class io.tinychat.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

# ---------------------------------------------------------------- WebView 的 JS 桥
# `core:markdown/TcRenderHost.Bridge` 的方法只由页面里的 JavaScript 调用，
# Java 侧看不到调用点；R8 会把没被标注的成员当死代码删掉。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------------------------------------------------------------- DataStore（Preferences）
# 偏好设置底层用 protobuf 生成的类做序列化，调用点同样是反射式的。
-keep class androidx.datastore.preferences.protobuf.** { *; }
-dontwarn androidx.datastore.preferences.protobuf.**

# ---------------------------------------------------------------- OkHttp / Coil
# 这两个库带一批「平台可选实现」（Conscrypt / BouncyCastle / OpenJSSE），
# 在 Android 上并不存在，R8 会报 missing class 警告。
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
