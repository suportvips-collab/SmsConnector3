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
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class ServerResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?
)

class SmsService : Service() {

    private val CHANNEL_ID = "SmsMonitorChannel"

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SMS_SERVICE", "🛡️ Serviço Iniciado (Blindagem Ativa)")
        createNotificationChannel()
        startForeground(1, buildNotification("Aguardando novos SMS..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val sender = intent.getStringExtra("sender")
            val body = intent.getStringExtra("body")

            if (!sender.isNullOrEmpty() && !body.isNullOrEmpty()) {
                notificationManager.notify(1, buildNotification("Processando SMS de $sender"))
                validateAndSend(sender, body)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SMS_SERVICE", "🛡️ Serviço Destruído (Blindagem Desativada)")
    }

    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
    }

    private fun validateAndSend(sender: String, body: String) {
        val prefs = getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
        
        // REFORÇO: Trim e Uppercase para evitar erros de digitação com letras
        val licenseKey = prefs.getString("license_key", "")?.trim()?.uppercase() ?: ""
        val targetEmail = prefs.getString("target_email", "")?.trim() ?: ""

        if (licenseKey.isEmpty() || targetEmail.isEmpty()) {
            val errorMessage = "Licença ou E-mail não configurado."
            Log.e("SMS_SERVICE", "⚠️ $errorMessage")
            NotificationHelper.showNotification(this, "Configuração Incompleta", errorMessage, isError = true)
            return
        }

        Log.d("SMS_SERVICE", "🚀 Enviando SMS via API (Token: $licenseKey)...")

        val retrofit = Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ApiService::class.java)

        val payload = SmsPayload(
            licenseKey = licenseKey,
            deviceId = getDeviceId(this),
            smsContent = body,
            senderNumber = sender,
            targetEmail = targetEmail
        )

        api.sendSmsData(payload).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                notificationManager.notify(1, buildNotification("Aguardando novos SMS..."))

                if (response.isSuccessful) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        Log.d("API_RESPOSTA", "Google respondeu: $responseString")
                        val serverResponse = try {
                            Gson().fromJson(responseString, ServerResponse::class.java)
                        } catch (e: Exception) {
                            Log.e("SMS_SERVICE_PARSER", "Falha ao parsear JSON", e)
                            ServerResponse("error", "Resposta JSON inválida.")
                        }

                        val jsonStatus = serverResponse.status ?: "error"
                        val jsonMessage = serverResponse.message ?: "Resposta vazia"

                        if (jsonStatus == "success") {
                            NotificationHelper.showNotification(
                                context = applicationContext,
                                title = "SMS Sincronizado",
                                message = jsonMessage,
                                isError = false
                            )
                        } else {
                            NotificationHelper.showNotification(
                                context = applicationContext,
                                title = "Falha no Token/Licença",
                                message = jsonMessage,
                                isError = true
                            )
                        }
                    }
                } else {
                    NotificationHelper.showNotification(
                        context = applicationContext,
                        title = "Erro de Servidor",
                        message = "Erro HTTP: ${response.code()}",
                        isError = true
                    )
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                notificationManager.notify(1, buildNotification("Aguardando novos SMS..."))
                NotificationHelper.showNotification(
                    context = applicationContext,
                    title = "Sem Conexão",
                    message = "Falha ao conectar com o servidor.",
                    isError = true
                )
            }
        })
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PlamilhaSMS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Monitoramento de SMS",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
