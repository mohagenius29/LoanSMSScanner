package com.mohart.loansmsscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF0B6E4F))) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    var loans by remember { mutableStateOf<List<Loan>?>(null) }
    var denied by remember { mutableStateOf(false) }

    fun runScan() {
        loans = SmsScanner.scan(context)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok
        if (ok) runScan()
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            runScan()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Loan SMS Scanner", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Your SMS never leaves this phone.", fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        val current = loans
        if (current == null) {
            Text("Allow SMS access so the app can find your loan messages (M-Shwari, KCB M-Pesa, Fuliza, Equity and more).")
            if (denied) Text("Permission was denied. Enable SMS permission in Settings to continue.", color = Color.Red)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(Manifest.permission.READ_SMS) }) { Text("Scan my SMS") }
        } else {
            val total = current.filter { !it.cleared }.sumOf { it.outstanding }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Total outstanding", color = Color.White)
                    Text("Ksh ${"%,.2f".format(total)}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current.isEmpty()) {
                    item { Text("No loan messages found in the last 120 days.") }
                }
                items(current) { loan ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(loan.lender, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (loan.cleared) "Cleared" else "Ksh ${"%,.2f".format(loan.outstanding)}",
                                color = if (loan.cleared) MaterialTheme.colorScheme.primary else Color(0xFFB3261E),
                                fontSize = 20.sp
                            )
                            Text("Last SMS: " + DateFormat.getDateInstance().format(Date(loan.lastDate)), fontSize = 12.sp)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Repayment strategy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                items(SmsScanner.strategies(current)) { tip ->
                    Text("• $tip", fontSize = 14.sp)
                }
                item {
                    Button(onClick = { runScan() }) { Text("Rescan") }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
