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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsService : Service() {

    private val CHANNEL_ID = "SmsMonitorChannel"
    // Escopo de Coroutine para o serviço, garantindo que as tarefas sejam canceladas quando o serviço for destruído.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        serviceScope.cancel() // Cancela todas as coroutines do escopo
        Log.d("SMS_SERVICE", "🛡️ Serviço Destruído (Blindagem Desativada)")
    }

    // Função auxiliar para pegar o ID Único do Android
    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
    }

    private fun validateAndSend(sender: String, body: String) {
        val prefs = getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
        val targetEmail = prefs.getString("target_email", "") ?: ""
        val licenseKey = prefs.getString("license_key", "") ?: ""

        if (targetEmail.isEmpty() || licenseKey.isEmpty()) {
            Log.e("SMS_SERVICE", "⚠️ Configuração incompleta. Ignorando e parando o serviço.")
            stopSelf() // Para o serviço se a configuração for removida enquanto ele roda
            return
        }

        Log.d("SMS_SERVICE", "🚀 Validando licença com o Google...")

        val retrofit = Retrofit.Builder()
            .baseUrl("https://script.google.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(ApiService::class.java)

        val payload = SmsPayload(
            licenseKey = licenseKey,
            targetEmail = targetEmail,
            smsSender = sender,
            smsBody = body,
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            deviceId = getDeviceId(this) // Envia o ID para validação
        )

        api.sendSmsData(payload).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful && response.body() != null) {
                    val responseString = response.body()!!.string()
                    Log.d("API_RESPOSTA", "Google respondeu: $responseString")

                    if (responseString.contains("success")) {
                        Log.d("SMS_SERVICE", "✅ Licença Aprovada! Iniciando envio SMTP...")
                        notificationManager.notify(1, buildNotification("Enviando E-mail... 📤"))

                        serviceScope.launch {
                            val emailSuccess = SmtpSender.sendEmail(
                                toEmail = targetEmail,
                                subject = "[SMS-SYNC] Novo SMS de $sender",
                                body = "Remetente: $sender'''\n\n'''Conteúdo:'''\n'''$body'''\n\n'''Recebido em: ${Date()}"
                            )

                            // Volta para a thread principal para atualizar a UI (Notificação)
                            withContext(Dispatchers.Main) {
                                if (emailSuccess) {
                                    Log.d("SMS_SERVICE", "🏆 CICLO COMPLETO: SMS -> Google -> SMTP -> Sucesso!")
                                    notificationManager.notify(1, buildNotification("Último envio: Sucesso ✅"))
                                } else {
                                    Log.e("SMS_SERVICE", "❌ Falha no envio SMTP.")
                                    notificationManager.notify(1, buildNotification("Erro no envio SMTP ❌"))
                                }
                            }
                        }
                    } else {
                        var errorMessage = "Licença Inválida ou Erro no Script"
                        try {
                            if (responseString.contains("Document") && responseString.contains("missing")) {
                                errorMessage = "Erro Google: Planilha não encontrada"
                            } else if (responseString.contains("'''"status"'''":"'''"error"'''")) {
                                val messageStart = responseString.indexOf("'''"message"'''":"'''")
                                if (messageStart != -1) {
                                    val start = messageStart + 11
                                    val end = responseString.indexOf("'''"'''", start)
                                    if (end != -1) {
                                        errorMessage = "Erro Google: ${responseString.substring(start, end)}"
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
