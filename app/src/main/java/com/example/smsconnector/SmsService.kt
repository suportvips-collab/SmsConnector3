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

// Data class para parsear a resposta do Google Script
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
        val licenseKey = prefs.getString("license_key", "") ?: ""
        val targetEmail = prefs.getString("target_email", "") ?: ""

        if (licenseKey.isEmpty() || targetEmail.isEmpty()) {
            val errorMessage = "Licença ou E-mail não configurado."
            Log.e("SMS_SERVICE", "⚠️ $errorMessage")
            NotificationHelper.showNotification(this, "Configuração Incompleta", errorMessage, isError = true)
            stopSelf()
            return
        }

        Log.d("SMS_SERVICE", "🚀 Enviando dados para o Google Apps Script...")

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
                // Restaura a notificação de serviço para o estado padrão
                notificationManager.notify(1, buildNotification("Aguardando novos SMS..."))

                if (response.isSuccessful) {
                    val responseString = response.body()?.string()
                    if (responseString != null) {
                        Log.d("API_RESPOSTA", "Google respondeu: $responseString")
                        val gson = Gson()
                        val serverResponse = try {
                            gson.fromJson(responseString, ServerResponse::class.java)
                        } catch (e: Exception) {
                            Log.e("SMS_SERVICE_PARSER", "Falha ao parsear JSON", e)
                            ServerResponse("error", "Resposta JSON inválida do servidor.")
                        }

                        val jsonStatus = serverResponse.status ?: "error"
                        val jsonMessage = serverResponse.message ?: "Resposta vazia do servidor"

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
                                title = "Falha no Envio",
                                message = jsonMessage,
                                isError = true
                            )
                        }
                    } else {
                        NotificationHelper.showNotification(
                            context = applicationContext,
                            title = "Erro de Servidor",
                            message = "O servidor retornou uma resposta vazia.",
                            isError = true
                        )
                    }
                } else {
                    NotificationHelper.showNotification(
                        context = applicationContext,
                        title = "Erro de Servidor",
                        message = "O servidor do Google retornou erro: ${response.code()}. Tente novamente mais tarde.",
                        isError = true
                    )
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                notificationManager.notify(1, buildNotification("Aguardando novos SMS..."))
                Log.e("API_ERROR", "💀 Falha de conexão com o Google: ${t.message}", t)

                NotificationHelper.showNotification(
                    context = applicationContext,
                    title = "Sem Conexão",
                    message = "Não foi possível conectar. Verifique sua internet.",
                    isError = true
                )
            }
        })
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Connector")
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
