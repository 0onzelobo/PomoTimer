package com.pomotimer.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** Casca nativa: a interface é o index.html (assets); alarme, notificação e vibração ficam aqui. */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private var insetsJs = ""
    private val home = "file:///android_asset/index.html"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alarms.createChannels(this)
        goEdgeToEdge()

        // Depuração remota do WebView só em builds de debug
        WebView.setWebContentsDebuggingEnabled(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)

        web = WebView(this).apply {
            setBackgroundColor(0xFF111214.toInt())
            overScrollMode = View.OVER_SCROLL_NEVER
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Endurecimento: nada de arquivos do aparelho, content providers, popups ou localização
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                setGeolocationEnabled(false)
                mediaPlaybackRequiresUserGesture = false
            }
            webViewClient = object : WebViewClient() {
                // Só a página do próprio app pode ser carregada
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    !request.url.toString().startsWith("file:///android_asset/")

                override fun onPageFinished(view: WebView, url: String) {
                    if (insetsJs.isNotEmpty()) view.evaluateJavascript(insetsJs, null)
                }
            }
            setOnApplyWindowInsetsListener { v, insets -> pushInsets(v as WebView, insets); insets }
            addJavascriptInterface(Bridge(), "Android")
            loadUrl(home)
        }
        setContentView(web)
    }

    // Visível (mesmo sem foco, ex.: tela dividida): o JS trata o fim da etapa; senão, o alarme trata
    override fun onStart() {
        super.onStart()
        Alarms.foreground = true
    }

    override fun onStop() {
        Alarms.foreground = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.evaluateJavascript("window.pfResume && pfResume(); window.pfPerms && pfPerms()", null)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        web.evaluateJavascript("window.pfPerms && pfPerms()", null)
    }

    /** Desenha por trás das barras do sistema; o HTML recebe as margens (notch, barra de gestos). */
    @Suppress("DEPRECATION")
    private fun goEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun pushInsets(v: WebView, insets: WindowInsets) {
        val d = resources.displayMetrics.density
        val t: Int; val r: Int; val b: Int; val l: Int
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            t = i.top; r = i.right; b = i.bottom; l = i.left
        } else {
            t = insets.systemWindowInsetTop; r = insets.systemWindowInsetRight
            b = insets.systemWindowInsetBottom; l = insets.systemWindowInsetLeft
        }
        insetsJs = "window.pfInsets && window.pfInsets(${t / d},${r / d},${b / d},${l / d})"
        v.evaluateJavascript(insetsJs, null)
    }

    @Suppress("DEPRECATION")
    private fun applyBars(dark: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
        } else {
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val f = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (dark) f and light.inv() else f or light
        }
    }

    private fun notificationsOk() = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    private fun exactOk() = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun openAppNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    /** Métodos chamados pelo JavaScript via window.Android. Toda entrada é validada. */
    inner class Bridge {
        @JavascriptInterface
        fun schedule(endTime: String, plan: String) {
            val t = endTime.toLongOrNull() ?: return
            val now = System.currentTimeMillis()
            if (t < now - 60_000L || t > now + 6 * 3_600_000L) return // máx. 6 h à frente
            if (plan.length > 1000) return
            val p = Alarms.Plan.parse(plan) ?: return
            Alarms.schedule(applicationContext, t, p)
        }

        /** O JS só trata o fim da etapa com o app visível; em segundo plano quem trata é o alarme. */
        @JavascriptInterface
        fun foreground(): Boolean = Alarms.foreground

        @JavascriptInterface
        fun cancel() = Alarms.cancel(applicationContext)

        @JavascriptInterface
        fun alert(sound: Boolean, vibrate: Boolean) = Alarms.alertNow(applicationContext, sound, vibrate)

        @JavascriptInterface
        fun keepScreenOn(on: Boolean) = runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        @JavascriptInterface
        fun setDark(dark: Boolean) = runOnUiThread { applyBars(dark) }

        @JavascriptInterface
        fun permissions(): String = "{\"notif\":${notificationsOk()},\"exact\":${exactOk()}}"

        @JavascriptInterface
        fun requestNotifications() = runOnUiThread {
            val prefs = getSharedPreferences("perm", MODE_PRIVATE)
            if (Build.VERSION.SDK_INT < 33) {
                openAppNotificationSettings()
                return@runOnUiThread
            }
            val p = Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED &&
                (!prefs.getBoolean("asked", false) || shouldShowRequestPermissionRationale(p))
            ) {
                prefs.edit().putBoolean("asked", true).apply()
                requestPermissions(arrayOf(p), 1)
            } else {
                // Negada de vez (ou desligada pelo usuário): só dá pra ligar nas Configurações
                openAppNotificationSettings()
            }
        }

        @JavascriptInterface
        fun requestExactAlarm() = runOnUiThread {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
    }
}
