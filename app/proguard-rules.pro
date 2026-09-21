# gomobile generates JNI-backed classes whose methods are called from native
# code; renaming or removing them breaks the core at runtime.
-keep class go.** { *; }
-keep class dev.zephyr.zephyrcore.** { *; }

# Our callback into Kotlin is resolved by gomobile through reflection on the
# generated proxy, so the implementation must keep its method signature.
-keep class dev.zephyr.mobile.core.** { *; }

-dontwarn org.slf4j.**
-dontwarn java.beans.**
-dontwarn javax.xml.**

# OkHttp's optional platform integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
