-keepattributes *Annotation*
-keep class com.vladsch.flexmark.** { *; }

# Flexmark/jsoup 引用了桌面端 Java API 和注解，Android 没有，需要让 R8 忽略
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn javax.imageio.**
-dontwarn javax.annotation.**
