# AirType
-keep class com.airtype.keyboard.** { *; }
-keep class io.github.davidallison.android.sdk.penremote.** { *; }
-keep class com.samsung.android.sdk.penremote.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
