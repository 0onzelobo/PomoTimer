# Métodos chamados pelo JavaScript (window.Android) não podem ser renomeados/removidos
-keepclassmembers class com.pomotimer.app.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
