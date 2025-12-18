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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.smsconnector.ui.theme.*
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VipBlack
                ) {
                    MainAppNavigation()
                }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center, // Volta ao centro para equilíbrio
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (currentStep) {
            0 -> StepOneWelcome { currentStep = 1 }
            1 -> StepTwoPermissions { currentStep = 2 }
            2 -> StepThreeBattery(onFinish)
        }
        Spacer(modifier = Modifier.height(80.dp)) // Empurra levemente para cima
    }
}

@Composable
fun StepOneWelcome(onNext: () -> Unit) {
    WizardTemplate(
        icon = Icons.Default.Email,
        title = "Bem-vindo ao PlamilhaSMS",
        description = "Conecte seus SMS de milhas e bancos diretamente à sua planilha com estilo neon.",
        buttonText = "iniciar configuração",
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
            Toast.makeText(context, "Permissões necessárias para o sistema.", Toast.LENGTH_LONG).show()
        }
    }

    WizardTemplate(
        icon = Icons.Default.Lock,
        title = "acesso seguro",
        description = "Precisamos ler seus SMS para processá-los. Seus dados nunca saem do seu controle.",
        buttonText = if (hasPermission) "próximo passo" else "conceder acesso",
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
fun StepThreeBattery(onNext: () -> Unit) {
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
        title = "blindagem ativa",
        description = "Para garantir 100% de entrega, o sistema precisa rodar sem restrições de bateria.",
        buttonText = if (isIgnoringBattery) "finalizar e ativar" else "ativar blindagem",
        onButtonClick = {
            if (isIgnoringBattery) {
                onNext()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    settingsLauncher.launch(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Abra os ajustes manualmente.", Toast.LENGTH_LONG).show()
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
            modifier = Modifier.size(80.dp),
            tint = NeonPurple
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title, 
            style = MaterialTheme.typography.headlineSmall, 
            color = VipWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description, 
            style = MaterialTheme.typography.bodyLarge, 
            color = VipGrey,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onButtonClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
        ) {
            Text(buttonText, color = VipWhite, fontSize = 16.sp)
        }
    }
}

// --- TELA PRINCIPAL (HOME) NEON ---

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE) }

    var email by remember { mutableStateOf(prefs.getString("target_email", "") ?: "") }
    var license by remember { mutableStateOf(prefs.getString("license_key", "") ?: "") }
    
    var emailError by remember { mutableStateOf(false) }
    var licenseError by remember { mutableStateOf(false) }
    
    var isValidated by remember { mutableStateOf(prefs.getBoolean("config_valid", false)) }
    
    var statusText by remember { 
        mutableStateOf(if (isValidated) "sistema ativo e monitorando" else "aguardando ativação") 
    }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Equilibrado no centro
    ) {
        // TÍTULO NEON MAIOR
        Text(
            text = "PlamilhaSMS",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonPurple,
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(48.dp))

        // STATUS CARD DINÂMICO NEON
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isError -> VipError.copy(alpha = 0.15f)
                    !isValidated -> NeonPurple.copy(alpha = 0.1f)
                    else -> VipSuccess.copy(alpha = 0.15f)
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
                        isError -> VipError
                        !isValidated -> NeonPurple
                        else -> VipSuccess
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = statusText,
                    color = when {
                        isError -> VipError
                        !isValidated -> NeonPurple
                        else -> VipSuccess
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 14.sp
                )
            }
        }

        // CAMPO E-MAIL
        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                emailError = false 
            },
            label = { Text("e-mail de destino", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.fillMaxWidth(),
            isError = emailError,
            leadingIcon = { Icon(Icons.Default.Email, null, tint = if (emailError) VipError else NeonPurple) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonPurple,
                unfocusedBorderColor = VipGrey.copy(alpha = 0.5f),
                focusedLabelColor = NeonPurple,
                errorBorderColor = VipError,
                cursorColor = NeonPurple,
                focusedTextColor = VipWhite,
                unfocusedTextColor = VipWhite
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // CAMPO LICENÇA
        OutlinedTextField(
            value = license,
            onValueChange = { 
                license = it
                licenseError = false 
            },
            label = { Text("chave de licença", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.fillMaxWidth(),
            isError = licenseError,
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = if (licenseError) VipError else NeonPurple) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonPurple,
                unfocusedBorderColor = VipGrey.copy(alpha = 0.5f),
                focusedLabelColor = NeonPurple,
                errorBorderColor = VipError,
                cursorColor = NeonPurple,
                focusedTextColor = VipWhite,
                unfocusedTextColor = VipWhite
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        // BOTÃO ATIVAR
        Button(
            onClick = {
                emailError = email.isEmpty()
                licenseError = license.isEmpty()
                
                if (emailError || licenseError) {
                    Toast.makeText(context, "preencha os campos destacados", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                isLoading = true
                statusText = "validando acesso..."
                isError = false
                
                testConnection(context, email, license) { success, message ->
                    isLoading = false
                    isError = !success
                    isValidated = success
                    statusText = if (success) "sistema ativo e monitorando" else message.lowercase()
                    
                    if (!success) {
                        if (message.lowercase().contains("licença") || message.lowercase().contains("token")) {
                            licenseError = true
                        } else if (message.lowercase().contains("e-mail")) {
                            emailError = true
                        }
                    } else {
                        prefs.edit().apply {
                            putString("target_email", email)
                            putString("license_key", license)
                            putBoolean("config_valid", true)
                            apply()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = VipWhite)
            } else {
                Text("ativar sistema", color = VipWhite, fontSize = 16.sp)
            }
        }

        // ESPAÇADOR ESTRATÉGICO para empurrar o layout sutilmente para cima
        Spacer(modifier = Modifier.height(100.dp))
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
        licenseKey = license.trim().uppercase(),
        deviceId = deviceId,
        smsContent = "TESTE DE CONEXÃO - PLAMILHAS ATIVADA",
        senderNumber = "SISTEMA",
        targetEmail = email.trim()
    )


    api.sendSmsData(payload).enqueue(object : Callback<ResponseBody> {
        override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
            if (response.isSuccessful) {
                val responseString = response.body()?.string() ?: ""
                val serverResponse = try {
                    Gson().fromJson(responseString, ServerResponse::class.java)
                } catch (e: Exception) { null }

                if (serverResponse?.status == "success") {
                    onResult(true, "ativado: ${serverResponse.message}")
                } else {
                    onResult(false, serverResponse?.message ?: "falha na ativação")
                }
            } else {
                onResult(false, "servidor ocupado (${response.code()})")
            }
        }

        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
            onResult(false, "verifique sua conexão")
        }
    })
}
