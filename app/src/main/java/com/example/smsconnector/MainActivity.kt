package com.example.smsconnector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smsconnector.ui.theme.SmsConnectorTheme
import com.google.gson.Gson
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsConnectorTheme {
                MainAppNavigation()
            }
        }
    }
}

@Composable
fun MainAppNavigation() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE) }
    var isSetupDone by remember { mutableStateOf(prefs.getBoolean("setup_completed", false)) }

    if (isSetupDone) {
        HomeScreen()
    } else {
        OnboardingWizard(onFinish = {
            prefs.edit().putBoolean("setup_completed", true).apply()
            isSetupDone = true
        })
    }
}

@Composable
fun OnboardingWizard(onFinish: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                0 -> StepOneWelcome { currentStep = 1 }
                1 -> StepTwoPermissions { currentStep = 2 }
                2 -> StepThreeBattery(onFinish)
            }
        }
    }
}

@Composable
fun StepOneWelcome(onNext: () -> Unit) {
    WizardTemplate(
        icon = Icons.Default.Email,
        title = "Bem-vindo ao PlamilhaSMS",
        description = "Este aplicativo conecta seus SMS bancários e de vendas diretamente à sua Planilha Google, via e-mail.\n\nSem configuração de servidor, simples e rápido.",
        buttonText = "Começar Configuração",
        onButtonClick = onNext
    )
}

@Composable
fun StepTwoPermissions(onNext: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        val readGranted = permissions[Manifest.permission.READ_SMS] ?: false
        if (smsGranted && readGranted) {
            hasPermission = true
        } else {
            Toast.makeText(context, "Precisamos ler o SMS para funcionar.", Toast.LENGTH_LONG).show()
        }
    }

    WizardTemplate(
        icon = Icons.Default.Lock,
        title = "Permissões de Acesso",
        description = "Para funcionar, o app precisa ler os SMS que chegam no seu celular.\n\nSeus dados são processados localmente e enviados apenas para o seu e-mail configurado.",
        buttonText = if (hasPermission) "Próximo Passo" else "Conceder Permissões",
        onButtonClick = {
            if (hasPermission) {
                onNext()
            } else {
                launcher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
            }
        }
    )
}

@Composable
fun StepThreeBattery(onFinish: () -> Unit) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBattery by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    WizardTemplate(
        icon = Icons.Default.Settings,
        title = "Rodar em 2º Plano",
        description = "O Android costuma 'matar' aplicativos para economizar bateria.\n\nPara garantir que nenhum SMS seja perdido, você precisa permitir que este app rode sem restrições.",
        buttonText = if (isIgnoringBattery) "Finalizar e Conectar" else "Remover Restrições",
        onButtonClick = {
            if (isIgnoringBattery) {
                onFinish()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    settingsLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Não foi possível abrir o ajuste automaticamente.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )
}

@Composable
fun WizardTemplate(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onButtonClick,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(buttonText, fontSize = 18.sp)
        }
    }
}

// --- TELA PRINCIPAL (HOME) ---

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE) }

    var email by remember { mutableStateOf(prefs.getString("target_email", "") ?: "") }
    var license by remember { mutableStateOf(prefs.getString("license_key", "") ?: "") }
    
    // Estado de validação persistente
    var isValidated by remember { mutableStateOf(prefs.getBoolean("config_valid", false)) }
    
    // Estados para UX de Status
    var statusText by remember { 
        mutableStateOf(if (isValidated) "Serviço Ativo e Monitorando" else "Aguardando Configuração") 
    }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Card Dinâmico
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isError -> Color(0xFFFFEBEE) // Vermelho (Erro)
                    !isValidated -> Color(0xFFFFF3E0) // Laranja (Pendente)
                    else -> Color(0xFFE8F5E9) // Verde (Ativo)
                }
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        isError -> Icons.Default.Warning
                        !isValidated -> Icons.Default.Info
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when {
                        isError -> Color(0xFFC62828)
                        !isValidated -> Color(0xFFE65100)
                        else -> Color(0xFF2E7D32)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    color = when {
                        isError -> Color(0xFFB71C1C)
                        !isValidated -> Color(0xFFBF360C)
                        else -> Color(0xFF1B5E20)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(text = "Configurações", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail de Destino") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Email, null) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = license,
            onValueChange = { license = it },
            label = { Text("Chave de Licença") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, null) }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // BOTÃO TESTE DE CONEXÃO
        OutlinedButton(
            onClick = {
                if (email.isEmpty() || license.isEmpty()) {
                    Toast.makeText(context, "Preencha os campos antes de testar", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                isLoading = true
                statusText = "Testando conexão..."
                isError = false
                
                testConnection(context, email, license) { success, message ->
                    isLoading = false
                    isError = !success
                    isValidated = success
                    statusText = if (success) "Serviço Ativo e Monitorando" else message
                    
                    // Salva o estado de validação
                    prefs.edit().putBoolean("config_valid", success).apply()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Testar Conexão Agora")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isEmpty() || license.isEmpty()) {
                    Toast.makeText(context, "Preencha os campos antes de salvar", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                prefs.edit().apply {
                    putString("target_email", email)
                    putString("license_key", license)
                    putBoolean("config_valid", true) 
                    apply()
                }
                isValidated = true
                isError = false
                statusText = "Serviço Ativo e Monitorando"
                Toast.makeText(context, "Configurações Salvas e Ativadas!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Salvar Alterações")
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = {
            prefs.edit().remove("setup_completed").remove("config_valid").apply()
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }) {
            Text("Resetar App (Voltar ao Wizard)", color = Color.Gray)
        }
    }
}

fun testConnection(context: Context, email: String, license: String, onResult: (Boolean, String) -> Unit) {
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "android_test"
    
    val retrofit = Retrofit.Builder()
        .baseUrl("https://script.google.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api = retrofit.create(ApiService::class.java)
    val payload = SmsPayload(
        licenseKey = license,
        deviceId = deviceId,
        smsContent = "TESTE DE CONEXÃO - INICIANDO SISTEMA",
        senderNumber = "SISTEMA",
        targetEmail = email
    )

    api.sendSmsData(payload).enqueue(object : Callback<ResponseBody> {
        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
            if (response.isSuccessful) {
                val responseString = response.body()?.string() ?: ""
                val serverResponse = try {
                    Gson().fromJson(responseString, ServerResponse::class.java)
                } catch (e: Exception) { null }

                if (serverResponse?.status == "success") {
                    onResult(true, "Conexão Ok: ${serverResponse.message}")
                } else {
                    onResult(false, "Falha: ${serverResponse?.message ?: "Resposta inválida"}")
                }
            } else {
                onResult(false, "Erro de Servidor: Código ${response.code()}")
            }
        }

        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
            onResult(false, "Sem Internet ou Servidor Offline")
        }
    })
}
