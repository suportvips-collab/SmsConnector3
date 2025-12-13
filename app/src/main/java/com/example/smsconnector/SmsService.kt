package com.example.smsconnector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Função utilitária para obter o ID único do dispositivo.
 */
private fun getDeviceId(context: Context): String {
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
}

class SmsService : Service() {

    companion object {
        private const val CHANNEL_ID = "SmsConnectorChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: ""
        val body = intent?.getStringExtra("body") ?: ""

        Log.d("SMS_SERVICE", "Serviço iniciado para processar SMS de: $sender")

        startForeground(NOTIFICATION_ID, buildNotification("Validando licença com o servidor..."))

        val prefs = getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
        val targetEmail = prefs.getString("target_email", "")
        val licenseKey = prefs.getString("license_key", "")

        if (!targetEmail.isNullOrEmpty() && !licenseKey.isNullOrEmpty()) {
            sendToApi(licenseKey, targetEmail, sender, body)
        } else {
            Log.w("SMS_SERVICE", "Configurações de e-mail ou licença não encontradas. Abortando.")
            stopSelf()
        }

        return START_STICKY
    }

    private fun sendToApi(license: String, email: String, sender: String, body: String) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ApiService::class.java)

        val payload = SmsPayload(
            licenseKey = license,
            targetEmail = email,
            smsSender = sender,
            smsBody = body,
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            deviceId = getDeviceId(this)
        )

        api.sendSmsData(payload).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (response.isSuccessful) {
                    val responseString = response.body()?.string() ?: ""
                    if (responseString.contains("success")) {
                        Log.d("API_SUCCESS", "✅ Licença Validada! Iniciando envio SMTP...")
                        notificationManager.notify(NOTIFICATION_ID, buildNotification("Encaminhando SMS via E-mail..."))

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val emailSuccess = SmtpSender.sendEmail(
                                    toEmail = email,
                                    subject = "[SMS-SYNC] Novo SMS de $sender",
                                    body = "Remetente: $sender\n\nConteúdo:\n$body\n\nRecebido em: ${Date()}"
                                )
                                if (emailSuccess) {
                                    notificationManager.notify(NOTIFICATION_ID, buildNotification("SMS encaminhado com sucesso! 📤"))
                                } else {
                                    notificationManager.notify(NOTIFICATION_ID, buildNotification("Erro no envio do E-mail ⚠️"))
                                }
                            } finally {
                                stopSelf() // Para o serviço após a tentativa de envio
                            }
                        }
                    } else {
                        Log.e("API_ERROR", "⛔ Licença negada pelo servidor: $responseString")
                        notificationManager.notify(NOTIFICATION_ID, buildNotification("Falha na validação da licença! ⛔"))
                        stopSelf()
                    }
                } else {
                    Log.e("API_ERROR", "❌ Erro do servidor: ${response.code()}")
                    notificationManager.notify(NOTIFICATION_ID, buildNotification("Erro de comunicação com o servidor! ❌"))
                    stopSelf()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("API_ERROR", "💀 Falha na conexão: ${t.message}")
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, buildNotification("Falha de conexão com o servidor! 💀"))
                stopSelf()
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SMS Connector Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Connector")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
