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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsConnectorTheme {
                // Controlador de Navegação Simples (Onboarding vs Home)
                MainAppNavigation()
            }
        }
    }
}

@Composable
fun MainAppNavigation() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE) }
    // Se "setup_completed" for true, vai direto para a Home. Se não, Onboarding.
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

// --- TELAS DO WIZARD (PASSO A PASSO) ---

@Composable
fun OnboardingWizard(onFinish: () -> Unit) {
    // Controla qual passo do Wizard estamos (0, 1 ou 2)
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
        title = "Bem-vindo ao SMS Connector",
        description = "Este aplicativo conecta seus SMS bancários e de vendas diretamente à sua Planilha Google, via e-mail.\n\nSem configuração de servidor, simples e rápido.",
        buttonText = "Começar Configuração",
        onButtonClick = onNext
    )
}

@Composable
fun StepTwoPermissions(onNext: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    // Launcher para pedir permissões de SMS
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        val readGranted = permissions[Manifest.permission.READ_SMS] ?: false
        if (smsGranted && readGranted) {
            hasPermission = true
            Toast.makeText(context, "Permissões de SMS concedidas!", Toast.LENGTH_SHORT).show()
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
        },
        isSecondaryAction = false
    )
}

@Composable
fun StepThreeBattery(onFinish: () -> Unit) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    // Verifica se já está na lista de exceção de bateria
    var isIgnoringBattery by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // Launcher para abrir a tela de configurações se precisar
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Ao voltar da tela de configurações, verifica novamente
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
                    Toast.makeText(context, "Não foi possível abrir o ajuste automaticamente. Vá nas configurações.", Toast.LENGTH_LONG).show()
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
    onButtonClick: () -> Unit,
    isSecondaryAction: Boolean = false
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
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = if (isSecondaryAction) ButtonDefaults.buttonColors(containerColor = Color.Gray) else ButtonDefaults.buttonColors()
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

    // Estados dos campos
    var email by remember { mutableStateOf(prefs.getString("target_email", "") ?: "") }
    var license by remember { mutableStateOf(prefs.getString("license_key", "") ?: "") }
    var statusText by remember { mutableStateOf("Serviço Ativo e Monitorando") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = statusText, color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
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

        Button(
            onClick = {
                prefs.edit().apply {
                    putString("target_email", email)
                    putString("license_key", license)
                    apply()
                }
                Toast.makeText(context, "Configurações Salvas!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Salvar Alterações")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botão de Reset (para testar o Wizard novamente se precisar)
        TextButton(onClick = {
            prefs.edit().remove("setup_completed").apply()
            // Reinicia a Activity para voltar ao Wizard
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }) {
            Text("Resetar App (Voltar ao Wizard)", color = Color.Gray)
        }
    }
}