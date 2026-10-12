package com.amit.profitloss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

private val LoginNavy = Color(0xFF172554)
private val LoginBlue = Color(0xFF315BCE)

@Composable
fun PhoneLoginScreen(onSignedIn: (String) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isRegistration by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun normalizedIndianMobile(): String? {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 10 -> digits
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            else -> null
        }
    }

    fun submit() {
        val mobile = normalizedIndianMobile()
        when {
            mobile == null -> {
                isError = true
                message = "Enter a valid 10-digit Indian mobile number."
            }
            pin.length != 4 || pin.any { !it.isDigit() } -> {
                isError = true
                message = "Enter your 4-digit PIN."
            }
            isRegistration && confirmPin != pin -> {
                isError = true
                message = "PIN and Confirm PIN do not match."
            }
            else -> {
                busy = true
                message = ""
                isError = false
                // Firebase Email/Password requires at least 6 password characters.
                // The synthetic email is internal; users only enter their mobile number and PIN.
                val internalEmail = "mobile" + mobile + "@profitloss.app"
                val internalPassword = "PL" + pin
                val task = if (isRegistration) {
                    auth.createUserWithEmailAndPassword(internalEmail, internalPassword)
                } else {
                    auth.signInWithEmailAndPassword(internalEmail, internalPassword)
                }
                task.addOnSuccessListener { result ->
                    busy = false
                    val uid = result.user?.uid
                    if (uid != null) onSignedIn(uid)
                    else {
                        isError = true
                        message = "Account was opened but its ID was not returned. Please try again."
                    }
                }.addOnFailureListener {
                    busy = false
                    isError = true
                    message = if (isRegistration) {
                        "Registration failed. This mobile number may already be registered. Try Login, or check Firebase Email/Password sign-in is enabled."
                    } else {
                        "Mobile number or PIN is incorrect. If you are new, choose Registration first."
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFF4F6FC)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp).background(Color.White, RoundedCornerShape(26.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(70.dp).background(Brush.linearGradient(listOf(LoginNavy, LoginBlue)), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Text("PROFIT • LOSS", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = LoginNavy)
            Text(
                if (isRegistration) "Create your account to back up your trading records"
                else "Sign in to restore your trading records",
                color = Color(0xFF69758C), fontSize = 13.sp
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mobile number") },
                placeholder = { Text("9876543210") },
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("4-digit PIN") },
                placeholder = { Text("Enter your PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(14.dp)
            )
            if (isRegistration) {
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm PIN") },
                    placeholder = { Text("Re-enter your PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            if (message.isNotBlank()) {
                Text(message, color = if (isError) Color(0xFFCC3348) else Color(0xFF138A55), fontSize = 12.sp)
            }
            Button(
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LoginBlue)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (isRegistration) "REGISTER" else "LOGIN", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = {
                isRegistration = !isRegistration
                confirmPin = ""
                message = ""
                isError = false
            }, enabled = !busy) {
                Text(if (isRegistration) "Already registered? Login" else "New user? Register")
            }
            Text("Your trading records are stored separately for each account.", color = Color(0xFF69758C), fontSize = 11.sp)
        }
    }
}
