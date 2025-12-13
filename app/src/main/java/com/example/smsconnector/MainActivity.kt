package com.example.smsconnector

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Chama a função que desenha a tela e agora também pede as permissões.
            SmsConfigScreen()
        }
    }
}

@Composable
fun SmsConfigScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE) }

    // Launcher moderno do Android para solicitar permissões.
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Verifica se TODAS as permissões foram concedidas.
        val allPermissionsGranted = permissions.values.all { it }
        if (allPermissionsGranted) {
            Toast.makeText(context, "Permissões concedidas! O app está pronto.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissão de SMS negada. O app não funcionará.", Toast.LENGTH_LONG).show()
        }
    }

    // Efeito que executa apenas uma vez quando a tela é carregada.
    // É o local ideal para pedir as permissões.
    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
        )
    }

    var email by remember { mutableStateOf(prefs.getString("target_email", "") ?: "") }
    var license by remember { mutableStateOf(prefs.getString("license_key", "") ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Configuração SMS Gateway", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail de Destino") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = license,
            onValueChange = { license = it },
            label = { Text("Chave de Licença") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                prefs.edit().apply {
                    putString("target_email", email)
                    putString("license_key", license)
                    apply()
                }
                Toast.makeText(context, "Configurações salvas!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar e Conectar")
        }
    }
}
