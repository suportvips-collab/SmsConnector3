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
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SmsService : Service() {

    private val CHANNEL_ID = "SmsMonitorChannel"

    // Obter o NotificationManager de forma preguiçosa (lazy) para otimização
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
                // Dispara o fluxo de validação + envio
                validateAndSend(sender, body)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SMS_SERVICE", "🛡️ Serviço Destruído (Blindagem Desativada)")
    }

    // Função auxiliar para pegar o ID Único do Android
    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
    }

    private fun validateAndSend(sender: String, body: String) {
        val prefs = getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
        val licenseKey = prefs.getString("license_key", "") ?: ""
        // CORREÇÃO 1: Recuperando o e-mail salvo pelo usuário na MainActivity
        val targetEmail = prefs.getString("target_email", "") ?: ""

        if (licenseKey.isEmpty()) {
            Log.e("SMS_SERVICE", "⚠️ Configuração incompleta (License Key).")
            notificationManager.notify(1, buildNotification("Erro: Licença não configurada"))
            stopSelf()
            return
        }

        // Validação extra: Se não tiver e-mail, avisa (opcional, mas recomendado)
        if (targetEmail.isEmpty()) {
            Log.w("SMS_SERVICE", "⚠️ E-mail de destino não configurado no App.")
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
            targetEmail = targetEmail // CORREÇÃO 1: Enviando o e-mail no JSON
        )

        api.sendSmsData(payload).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    val responseString = response.body()!!.string()
                    Log.d("API_RESPOSTA", "Google respondeu: $responseString")

                    if (responseString.contains("success")) {
                        Log.d("SMS_SERVICE", "✅ Sucesso! Script processou a mensagem.")
                        notificationManager.notify(1, buildNotification("Envio com sucesso! ✅"))
                    } else {
                        var errorMessage = "Erro no Script"
                        try {
                            // CORREÇÃO 2: Sintaxe corrigida (removido aspas triplas inválidas)
                            // Verifica se contem "status":"error" escapando as aspas duplas corretamente
                            if (responseString.contains("\"status\":\"error\"")) {
                                val tag = "\"message\":\""
                                val messageStart = responseString.indexOf(tag)
                                if (messageStart != -1) {
                                    val start = messageStart + tag.length
                                    val end = responseString.indexOf("\"", start)
                                    if (end != -1) {
                                        errorMessage = "Erro: ${responseString.substring(start, end)}"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SMS_SERVICE_PARSER", "Falha ao extrair erro da resposta.", e)
                        }

                        Log.e("API_ERROR", "⛔ $errorMessage | Resposta original: $responseString")
                        notificationManager.notify(1, buildNotification("⚠️ $errorMessage"))
                    }

                } else {
                    val errorBody = response.errorBody()?.string() ?: "Corpo do erro indisponível"
                    Log.e("API_ERROR", "❌ Erro HTTP: ${response.code()}. Resposta: $errorBody")
                    notificationManager.notify(1, buildNotification("Erro de conexão: ${response.code()}"))
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.e("API_ERROR", "💀 Falha de conexão com o Google: ${t.message}", t)
                notificationManager.notify(1, buildNotification("Falha de conexão com a API"))
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}