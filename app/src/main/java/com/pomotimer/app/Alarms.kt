package com.pomotimer.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Agenda o fim de cada etapa no AlarmManager — dispara com a tela desligada e o app fechado.
 *
 * Com o app visível, quem trata o fim da etapa é o JavaScript (index.html). Em segundo plano,
 * quem trata é o [onAlarm]: avisa, respeita Som/Vibrar e, se o início automático estiver ligado,
 * já agenda a etapa seguinte. Ao voltar, o JS refaz as mesmas contas e chega ao mesmo estado.
 */
object Alarms {
    private const val CH_RUNNING = "running"
    private const val CH_ALERT = "alert"
    private const val CH_ALERT_SILENT = "alert_silent"
    private const val ID_RUNNING = 1
    private const val ID_ALERT = 2

    private val PHASE_NAME = mapOf("work" to "foco", "short" to "pausa curta", "long" to "pausa longa")

    /** true enquanto a Activity está visível: aí o JS cuida do fim da etapa e o alarme não faz nada. */
    @Volatile
    var foreground = false

    /** O que o alarme precisa saber para avisar e emendar a próxima etapa sem o app aberto. */
    data class Plan(
        val phase: String, val cycle: Int, val every: Int,
        val work: Int, val short: Int, val long: Int,
        val autoBreak: Boolean, val autoWork: Boolean,
        val sound: Boolean, val vibrate: Boolean,
    ) {
        fun minutes(p: String = phase) = when (p) {
            "work" -> work
            "short" -> short
            else -> long
        }

        /** Mesma regra do advance() do index.html. */
        fun next(): Plan = if (phase == "work") {
            val c = cycle + 1
            copy(phase = if (c >= every) "long" else "short", cycle = c)
        } else {
            copy(phase = "work", cycle = if (phase == "long") 0 else cycle)
        }

        /** Esta etapa começa sozinha quando a anterior acaba? */
        fun autoStarts() = if (phase == "work") autoWork else autoBreak

        fun toJson(): String = JSONObject()
            .put("phase", phase).put("cycle", cycle).put("every", every)
            .put("work", work).put("short", short).put("long", long)
            .put("autoBreak", autoBreak).put("autoWork", autoWork)
            .put("sound", sound).put("vibrate", vibrate)
            .toString()

        companion object {
            /** Lê e valida o JSON (vindo do JavaScript ou das prefs); null se algo estiver fora do esperado. */
            fun parse(json: String): Plan? = runCatching {
                val o = JSONObject(json)
                fun int(k: String, range: IntRange): Int {
                    val v = o.getInt(k)
                    require(v in range)
                    return v
                }
                val phase = o.getString("phase")
                require(phase in PHASE_NAME)
                Plan(
                    phase = phase,
                    cycle = int("cycle", 0..99),
                    every = int("every", 2..8),
                    work = int("work", 1..120),
                    short = int("short", 1..30),
                    long = int("long", 1..60),
                    autoBreak = o.getBoolean("autoBreak"),
                    autoWork = o.getBoolean("autoWork"),
                    sound = o.getBoolean("sound"),
                    vibrate = o.getBoolean("vibrate"),
                )
            }.getOrNull()
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("alarm", Context.MODE_PRIVATE)

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, "Timer em andamento", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        // O som de uma notificação é definido pelo canal, então o ajuste "Som" escolhe o canal
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Fim de etapa", NotificationManager.IMPORTANCE_HIGH)
                .apply { enableVibration(false) } // vibração feita manualmente
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT_SILENT, "Fim de etapa (sem som)", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null); enableVibration(false) }
        )
    }

    private fun alarmPi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openAppPi(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(ctx: Context, endTime: Long, plan: Plan) {
        prefs(ctx).edit().putLong("end", endTime).putString("plan", plan.toJson()).apply()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmPi(ctx)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, pi)
        }
        showRunning(ctx, endTime, plan.phase)
    }

    fun cancel(ctx: Context) {
        prefs(ctx).edit().putLong("end", 0L).apply()
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmPi(ctx))
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_RUNNING)
    }

    private fun showRunning(ctx: Context, endTime: Long, phase: String) {
        val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endTime))
        val n = Notification.Builder(ctx, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(PHASE_NAME.getValue(phase).replaceFirstChar { it.uppercase() })
            .setContentText("Termina às $hora")
            .setWhen(endTime)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPi(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_RUNNING, n)
    }

    /** Alarme disparou com o app em segundo plano. */
    fun onAlarm(ctx: Context) {
        if (foreground) return // app na tela: o JS já está tratando o fim da etapa
        val p = prefs(ctx)
        val end = p.getLong("end", 0L)
        val now = System.currentTimeMillis()
        if (end == 0L || now < end - 1500) return
        var plan = p.getString("plan", null)?.let(Plan::parse) ?: return cancel(ctx)

        // Avança como o finish() do JS; se o alarme atrasou (Doze), emenda as etapas que já passaram
        var ended: String
        var endTime = end
        var running = true
        var guard = 0
        do {
            ended = plan.phase
            plan = plan.next()
            if (!plan.autoStarts()) {
                running = false
                break
            }
            endTime += plan.minutes() * 60_000L
        } while (endTime <= now && ++guard < 50)

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(ID_RUNNING)
        val next = "${PHASE_NAME.getValue(plan.phase)} (${plan.minutes()} min)"
        val n = Notification.Builder(ctx, if (plan.sound) CH_ALERT else CH_ALERT_SILENT)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(if (ended == "work") "Pomodoro concluído 🍅" else "Pausa encerrada")
            .setContentText(if (running) "Começou: $next" else "Próxima: $next")
            .setAutoCancel(true)
            .setContentIntent(openAppPi(ctx))
            .build()
        nm.notify(ID_ALERT, n)
        if (plan.vibrate) vibrate(ctx)

        if (running) schedule(ctx, endTime, plan) else p.edit().putLong("end", 0L).apply()
    }

    /**
     * O Android apaga os alarmes ao reiniciar o aparelho: reagenda a etapa que estava rodando,
     * ou avisa na hora se ela terminou enquanto o aparelho estava desligado.
     */
    fun restore(ctx: Context) {
        val p = prefs(ctx)
        val end = p.getLong("end", 0L)
        if (end == 0L) return
        val plan = p.getString("plan", null)?.let(Plan::parse) ?: return cancel(ctx)
        if (System.currentTimeMillis() < end - 1500) schedule(ctx, end, plan) else onAlarm(ctx)
    }

    /** Fim de etapa com o app aberto: o JS já mostrou o aviso na tela. */
    fun alertNow(ctx: Context, sound: Boolean, vibrate: Boolean) {
        if (sound) {
            runCatching {
                RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
            }
        }
        if (vibrate) vibrate(ctx)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ctx: Context) {
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 700), -1))
    }
}
