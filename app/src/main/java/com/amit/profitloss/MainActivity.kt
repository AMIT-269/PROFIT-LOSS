package com.amit.profitloss

import android.app.DatePickerDialog
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

private val Navy = Color(0xFF172554)
private val Blue = Color(0xFF315BCE)
private val Green = Color(0xFF138A55)
private val Red = Color(0xFFCC3348)
private val Orange = Color(0xFFE98226)
private val Purple = Color(0xFF7655C9)
private val CanvasColor = Color(0xFFF4F6FC)
private val Ink = Color(0xFF182238)
private val Muted = Color(0xFF69758C)
private val iso = DateTimeFormatter.ISO_LOCAL_DATE
private val prettyDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

data class Trade(val id: String = UUID.randomUUID().toString(), val date: String, val profit: Double, val loss: Double, val brokerage: Double) {
    val net: Double get() = if (profit > 0) profit - brokerage else if (loss > 0) -(loss + brokerage) else 0.0
    val brokeragePaid: Double get() = if (profit > 0 || loss > 0) brokerage else 0.0
}
private fun cash(n: Double) = "₹" + String.format(Locale.US, "%,d", n.roundToLong())
private fun showDate(s: String) = runCatching { LocalDate.parse(s, iso).format(prettyDate) }.getOrDefault(s)
private fun profits(ts: List<Trade>) = ts.filter { it.net > 0 }.sumOf { it.net }
private fun losses(ts: List<Trade>) = -ts.filter { it.net < 0 }.sumOf { it.net }
private fun net(ts: List<Trade>) = ts.sumOf { it.net }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent { ProfitLossRoot() }
    }
}

@Composable
private fun ProfitLossRoot() {
    val context = LocalContext.current
    var signedInUid by remember { mutableStateOf(FirebaseApp.getApps(context).firstOrNull()?.let { FirebaseAuth.getInstance(it).currentUser?.uid }) }
    if (FirebaseApp.getApps(context).isEmpty()) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Blue)) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Firebase setup required", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
                Spacer(Modifier.height(10.dp))
                Text("Add app/google-services.json from your Firebase project, then enable Phone Authentication and Cloud Firestore.", color = Muted)
            }
        }
    } else if (signedInUid == null) {
        PhoneLoginScreen(onSignedIn = { signedInUid = it })
    } else {
        MainScreen(uid = signedInUid!!, onSignOut = { FirebaseAuth.getInstance().signOut(); signedInUid = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(uid: String, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(uid) {
        val accountPrefs = context.getSharedPreferences("trade_store_$uid", Context.MODE_PRIVATE)
        if (!accountPrefs.contains("trades")) {
            val legacyPrefs = context.getSharedPreferences("trade_store", Context.MODE_PRIVATE)
            if (legacyPrefs.contains("trades")) {
                accountPrefs.edit().putString("trades", legacyPrefs.getString("trades", "[]")).apply()
                legacyPrefs.edit().remove("trades").apply()
            }
        }
        accountPrefs
    }
    var trades by remember { mutableStateOf(readTrades(prefs)) }
    var date by remember { mutableStateOf(LocalDate.now().format(iso)) }
    var tab by remember { mutableIntStateOf(0) }
    var profitInput by remember { mutableStateOf("") }
    var lossInput by remember { mutableStateOf("") }
    var brokerageInput by remember { mutableStateOf("87") }
    var formDate by remember { mutableStateOf(date) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                val ordered = trades.sortedWith(compareBy<Trade> { it.date }.thenBy { trades.indexOf(it) })
                val csv = buildString {
                    appendLine("Date,Trade No,Profit,Loss,Brokerage,Net Result")
                    ordered.forEach { t ->
                        val number = trades.filter { it.date == t.date }.indexOf(t) + 1
                        appendLine("${t.date},$number,${t.profit},${t.loss},${t.brokerage},${t.net}")
                    }
                }
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
            }.onSuccess { error = "CSV export completed." }.onFailure { error = "CSV export failed: ${it.localizedMessage}" }
        }
    }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(tradesToJson(trades)) } }
                .onSuccess { error = "JSON backup saved." }.onFailure { error = "Backup failed: ${it.localizedMessage}" }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Could not read selected file.")
                JSONArray(raw)
                parseTrades(raw)
            }.onSuccess { imported -> trades = imported; error = "Backup restored. Cloud sync will update after restore." }
                .onFailure { error = "Restore failed: choose a valid Profit-Loss JSON backup." }
        }
    }
    var cloudReady by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(uid) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) trades = parseTrades(document.getString("tradesJson") ?: "[]")
                cloudReady = true
            }
            .addOnFailureListener { error = "Cloud restore failed. Check internet and Firestore rules." }
    }
    LaunchedEffect(trades, cloudReady) {
        writeTrades(prefs, trades)
        if (cloudReady) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .set(mapOf("tradesJson" to tradesToJson(trades)))
                .addOnFailureListener { error = "Cloud backup failed. Your local data is still saved." }
        }
    }

    val todayTrades = trades.filter { it.date == date }.sortedBy { trades.indexOf(it) }
    val start = LocalDate.parse(date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = start.plusDays(6)
    val weekly = trades.filter { val d = LocalDate.parse(it.date); !d.isBefore(start) && !d.isAfter(end) }
    val allNet = net(trades)
    val allProfit = profits(trades)
    val allLoss = losses(trades)

    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Purple, background = CanvasColor, surface = Color.White)) {
        Scaffold(
            containerColor = CanvasColor,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Navy, Blue))).statusBarsPadding().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(48.dp).background(Color.White.copy(alpha = .15f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Savings, null, tint = Color(0xFF6EE7B7), modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PROFIT • LOSS", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Text("TRADING REGISTER", color = Color(0xFFD7E3FF), fontSize = 11.sp, letterSpacing = 2.sp)
                    }
                    IconButton(onClick = onSignOut) { Icon(Icons.Default.Logout, "Sign out", tint = Color.White) }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Add, null) }, label = { Text("Entry") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.History, null) }, label = { Text("History") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Assessment, null) }, label = { Text("Reports") })
                }
            }
        ) { padding ->
            when (tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Daily dashboard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text("Track every trade clearly", fontSize = 13.sp, color = Muted)
                            }
                            DateButton(date) {
                                val d = LocalDate.parse(date)
                                DatePickerDialog(context, { _, y, m, day -> val selected = LocalDate.of(y, m + 1, day).format(iso); date = selected; formDate = selected }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ColorCard("NET PROFIT", cash(profits(todayTrades)), Green, Color(0xFFE4F8EC), Modifier.weight(1f))
                            ColorCard("NET LOSS", cash(losses(todayTrades)), Red, Color(0xFFFFE9EC), Modifier.weight(1f))
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Navy, Color(0xFF3C55A5))), RoundedCornerShape(22.dp)).padding(20.dp)) {
                            Text("TODAY'S NET RESULT", color = Color(0xFFD5E1FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(signed(net(todayTrades)), color = if (net(todayTrades) < 0) Color(0xFFFFB8C1) else Color.White, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
                            Text(todayTrades.size.toString() + " trades  •  Brokerage " + cash(todayTrades.sumOf { it.brokeragePaid }), color = Color(0xFFD5E1FF), fontSize = 12.sp)
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (editingId == null) "Add new trade" else "Edit trade", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                                Text("MANUAL INPUT", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                AmountField("Profit (₹)", profitInput, { profitInput = it; if (it.isNotBlank()) lossInput = "" }, Green, Modifier.weight(1f))
                                AmountField("Loss (₹)", lossInput, { lossInput = it; if (it.isNotBlank()) profitInput = "" }, Red, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
                                Column(Modifier.weight(1f)) {
                                    Text("Brokerage per trade (₹)", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(5.dp))
                                    OutlinedTextField(brokerageInput, { brokerageInput = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(13.dp))
                                    Text("Default ₹87 • editable", color = Orange, fontSize = 11.sp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Trade date", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(5.dp))
                                    OutlinedButton(onClick = {
                                        val d = LocalDate.parse(formDate)
                                        DatePickerDialog(context, { _, y, m, day -> val selected = LocalDate.of(y, m + 1, day).format(iso); formDate = selected; date = selected }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                                    }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp), shape = RoundedCornerShape(13.dp)) {
                                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(showDate(formDate), fontSize = 10.sp)
                                    }
                                }
                            }
                            val p = profitInput.toDoubleOrNull() ?: 0.0
                            val l = lossInput.toDoubleOrNull() ?: 0.0
                            val b = brokerageInput.toDoubleOrNull() ?: 87.0
                            val preview = if (p > 0 && l == 0.0) p - b else if (l > 0 && p == 0.0) -(l + b) else 0.0
                            Row(Modifier.fillMaxWidth().background(if (preview < 0) Color(0xFFFFEBEE) else Color(0xFFE8F7EF), RoundedCornerShape(13.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("NET RESULT PREVIEW", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold)
                                    Text("Profit − brokerage / Loss + brokerage", fontSize = 10.sp, color = Muted)
                                }
                                Text(signed(preview), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (preview < 0) Red else Green)
                            }
                            if (error.isNotBlank()) Text(error, color = Red, fontSize = 12.sp)
                            Button(onClick = {
                                val pVal = profitInput.toDoubleOrNull() ?: 0.0
                                val lVal = lossInput.toDoubleOrNull() ?: 0.0
                                val bVal = brokerageInput.toDoubleOrNull()
                                when {
                                    pVal < 0 || lVal < 0 -> error = "Amount cannot be negative."
                                    pVal > 0 && lVal > 0 -> error = "Enter Profit OR Loss, not both."
                                    pVal == 0.0 && lVal == 0.0 -> error = "Enter a Profit or Loss amount."
                                    bVal == null || bVal < 0 -> error = "Enter valid brokerage."
                                    else -> {
                                        val t = Trade(id = editingId ?: UUID.randomUUID().toString(), date = formDate, profit = pVal, loss = lVal, brokerage = bVal)
                                        trades = if (editingId == null) trades + t else trades.map { if (it.id == t.id) t else it }
                                        date = formDate
                                        profitInput = ""; lossInput = ""; brokerageInput = "87"; editingId = null; error = ""
                                    }
                                }
                            }, Modifier.fillMaxWidth().height(51.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                                Icon(if (editingId == null) Icons.Default.Add else Icons.Default.Edit, null)
                                Spacer(Modifier.width(7.dp))
                                Text(if (editingId == null) "SAVE TRADE" else "UPDATE TRADE", fontWeight = FontWeight.Bold)
                            }
                            if (editingId != null) TextButton(onClick = { editingId = null; profitInput = ""; lossInput = ""; brokerageInput = "87"; error = "" }, modifier = Modifier.align(Alignment.End)) { Text("Cancel edit") }
                        }
                    }
                    item { Text("Today's trades", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
                    if (todayTrades.isEmpty()) item { EmptyCard("No trades yet", "Add your first trade above to start the daily register.") }
                    items(todayTrades.reversed(), key = { it.id }) { t ->
                        TradeCard(t, todayTrades.indexOf(t) + 1, onEdit = {
                            editingId = t.id; profitInput = if (t.profit > 0) t.profit.toString().removeSuffix(".0") else ""
                            lossInput = if (t.loss > 0) t.loss.toString().removeSuffix(".0") else ""
                            brokerageInput = t.brokerage.toString().removeSuffix(".0"); formDate = t.date; error = ""
                        }, onDelete = { trades = trades.filterNot { it.id == t.id } })
                    }
                }
                1 -> {
                    val history = trades.sortedWith(compareByDescending<Trade> { it.date }.thenByDescending { trades.indexOf(it) })
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Text("Trade history", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("All saved entries • edit or delete anytime", color = Muted, fontSize = 13.sp)
                        }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ColorCard("ALL PROFIT", cash(allProfit), Green, Color(0xFFE4F8EC), Modifier.weight(1f))
                            ColorCard("ALL LOSS", cash(allLoss), Red, Color(0xFFFFE9EC), Modifier.weight(1f))
                        } }
                        if (history.isEmpty()) item { EmptyCard("No saved trades", "Your trade history will appear here.") }
                        items(history, key = { it.id }) { t ->
                            TradeCard(t, trades.filter { it.date == t.date }.indexOf(t) + 1, onEdit = {
                                date = t.date; formDate = t.date; editingId = t.id
                                profitInput = if (t.profit > 0) t.profit.toString().removeSuffix(".0") else ""
                                lossInput = if (t.loss > 0) t.loss.toString().removeSuffix(".0") else ""
                                brokerageInput = t.brokerage.toString().removeSuffix(".0"); error = ""; tab = 0
                            }, onDelete = { trades = trades.filterNot { it.id == t.id } })
                        }
                    }
                }
                else -> {
                    val dailyGroups = trades.groupBy { it.date }.toSortedMap()
                    var cumulativeBalance = 0.0
                    val dailyRows = dailyGroups.map { (day, dayTrades) ->
                        cumulativeBalance += net(dayTrades)
                        Triple(day, dayTrades, cumulativeBalance)
                    }.reversed()
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        item {
                            Text("Reports & balance", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("Weekly report and all-time running total", color = Muted, fontSize = 13.sp)
                        }
                        item {
                            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("BACKUP & EXPORT", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { csvExportLauncher.launch("profit-loss-trades.csv") }, modifier = Modifier.weight(1f)) { Text("Export CSV", fontSize = 11.sp) }
                                    OutlinedButton(onClick = { backupExportLauncher.launch("profit-loss-backup.json") }, modifier = Modifier.weight(1f)) { Text("Backup JSON", fontSize = 11.sp) }
                                }
                                OutlinedButton(onClick = { backupImportLauncher.launch(arrayOf("application/json", "text/json", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Restore JSON Backup") }
                                if (error.isNotBlank()) Text(error, color = if (error.contains("failed", true)) Red else Green, fontSize = 11.sp)
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Purple, Color(0xFF9B65D6))), RoundedCornerShape(22.dp)).padding(20.dp)) {
                                Text("ALL-TIME RUNNING BALANCE", color = Color.White.copy(alpha = .85f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Text(signed(allNet), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                                Text("All saved days combined", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                            }
                        }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ColorCard("TOTAL PROFIT", cash(allProfit), Green, Color(0xFFE4F8EC), Modifier.weight(1f))
                            ColorCard("TOTAL LOSS", cash(allLoss), Red, Color(0xFFFFE9EC), Modifier.weight(1f))
                        } }
                        item {
                            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("WEEKLY REPORT", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                                Text(showDate(start.format(iso)) + " – " + showDate(end.format(iso)), color = Ink, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MiniReport("Net profit", cash(profits(weekly)), Green, Modifier.weight(1f))
                                    MiniReport("Net loss", cash(losses(weekly)), Red, Modifier.weight(1f))
                                }
                                HorizontalDivider(color = Color(0xFFE8EAF1))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Weekly net result", color = Muted, fontSize = 12.sp)
                                        Text(weekly.size.toString() + " trades", color = Muted, fontSize = 11.sp)
                                    }
                                    Text(signed(net(weekly)), color = if (net(weekly) < 0) Red else Green, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { date = start.minusWeeks(1).format(iso) }, modifier = Modifier.weight(1f)) { Text("← Previous") }
                                    OutlinedButton(onClick = { date = start.plusWeeks(1).format(iso) }, modifier = Modifier.weight(1f)) { Text("Next →") }
                                }
                            }
                        }
                        item { Text("DAILY RUNNING TOTAL", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
                        items(dailyRows, key = { it.first }) { row ->
                            val dayTrades = row.second
                            val dailyNet = net(dayTrades)
                            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(showDate(row.first), color = Ink, fontWeight = FontWeight.Bold)
                                    Text(dayTrades.size.toString() + " trades • brokerage " + cash(dayTrades.sumOf { it.brokeragePaid }), color = Muted, fontSize = 11.sp)
                                    Text("Running balance " + signed(row.third), color = Purple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(signed(dailyNet), color = if (dailyNet < 0) Red else Green, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun signed(n: Double) = (if (n > 0) "+" else if (n < 0) "−" else "") + cash(abs(n))

private fun parseTrades(json: String): List<Trade> = runCatching {
    val a = JSONArray(json)
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        Trade(o.optString("id", UUID.randomUUID().toString()), o.getString("date"), o.optDouble("profit", 0.0), o.optDouble("loss", 0.0), o.optDouble("brokerage", 87.0))
    }
}.getOrDefault(emptyList())

private fun readTrades(prefs: android.content.SharedPreferences): List<Trade> = parseTrades(prefs.getString("trades", "[]") ?: "[]")

private fun tradesToJson(trades: List<Trade>): String {
    val a = JSONArray()
    trades.forEach { a.put(JSONObject().put("id", it.id).put("date", it.date).put("profit", it.profit).put("loss", it.loss).put("brokerage", it.brokerage)) }
    return a.toString()
}

private fun writeTrades(prefs: android.content.SharedPreferences, trades: List<Trade>) {
    prefs.edit().putString("trades", tradesToJson(trades)).apply()
}

@Composable
private fun ColorCard(title: String, amount: String, accent: Color, bg: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp)
        Text(amount, color = accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}
@Composable
private fun DateButton(date: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(13.dp), shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, null, tint = Blue, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(showDate(date), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
private fun AmountField(label: String, value: String, onChange: (String) -> Unit, accent: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
        Spacer(Modifier.height(5.dp))
        OutlinedTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' }) }, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), placeholder = { Text("0") }, shape = RoundedCornerShape(13.dp))
    }
}
@Composable
private fun TradeCard(t: Trade, number: Int, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(17.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(40.dp).background(if (t.net < 0) Color(0xFFFFE9EC) else Color(0xFFE4F8EC), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = if (t.net < 0) Red else Green, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Trade " + number + " • " + showDate(t.date), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Profit " + cash(t.profit) + "  |  Loss " + cash(t.loss), color = Muted, fontSize = 11.sp)
            Text("Brokerage " + cash(t.brokerage), color = Orange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(signed(t.net), color = if (t.net < 0) Red else Green, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(29.dp)) { Icon(Icons.Default.Edit, "Edit", tint = Blue, modifier = Modifier.size(16.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(29.dp)) { Icon(Icons.Default.Delete, "Delete", tint = Red, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
@Composable
private fun MiniReport(label: String, amount: String, color: Color, modifier: Modifier) {
    Column(modifier.background(color.copy(alpha = .09f), RoundedCornerShape(13.dp)).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(amount, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}
@Composable
private fun EmptyCard(title: String, message: String) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Ink, fontWeight = FontWeight.Bold)
        Text(message, color = Muted, fontSize = 12.sp)
    }
}
