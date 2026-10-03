# ===== 保留数据模型（Room 实体）=====
# Room 通过反射实例化实体，字段名不能混淆
-keep class com.autoskip.helper.data.** { *; }
-keepclassmembers class com.autoskip.helper.data.** { *; }

# ===== 保留系统组件（被系统反射启动）=====
# AccessibilityService / Service / Activity / Application / Receiver
-keep public class * extends android.app.Service
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.accessibilityservice.AccessibilityService

# ===== 保留 ViewModel（被 ViewModelProvider 反射实例化）=====
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# ===== 保留自定义 View / Composable 的参数名（Compose 稳定类型）=====
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ===== Kotlin 元数据（反射用）=====
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ===== 保留 JSON 手拼解析用到的类（防止字符串反射）=====
# 已用正则解析 JSON，无需 Gson 规则

# ===== 保留日志（可选：想关闭日志就删）=====
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
# }

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ===== DataStore =====
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ===== Kotlin 协程 =====
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ===== 保留行号（便于崩溃日志定位，可选去掉）=====
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===== 常见 dontwarn =====
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**
-dontwarn javax.annotation.**
