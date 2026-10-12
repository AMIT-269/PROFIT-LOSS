package com.amit.profitloss

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

private val LoginNavy = Color(0xFF172554)
private val LoginBlue = Color(0xFF315BCE)

@Composable
fun PhoneLoginScreen(onSignedIn: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = remember { FirebaseAuth.getInstance() }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun finishSignIn(credential: PhoneAuthCredential) {
        busy = true
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                busy = false
                val uid = result.user?.uid
                if (uid != null) onSignedIn(uid) else { isError = true; message = "Login succeeded but account ID was not returned." }
            }
            .addOnFailureListener {
                busy = false
                isError = true
                message = it.localizedMessage ?: "Could not verify OTP. Please try again."
            }
    }

    fun requestOtp(resend: Boolean = false) {
        val raw = phone.trim()
        val normalized = if (raw.startsWith("+")) "+" + raw.drop(1).filter { it.isDigit() } else "+91" + raw.filter { it.isDigit() }
        if (normalized.length !in 11..16 || normalized.drop(1).any { !it.isDigit() }) {
            isError = true
            message = "Enter a valid mobile number with country code, e.g. +919876543210."
            return
        }
        if (activity == null) {
            isError = true
            message = "Unable to open phone verification on this screen."
            return
        }
        busy = true
        message = ""
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                finishSignIn(credential)
            }
            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                busy = false
                isError = true
                message = e.localizedMessage ?: "SMS verification failed. Check Firebase phone-auth setup."
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                busy = false
                verificationId = id
                resendToken = token
                isError = false
                message = "OTP sent. Check your SMS messages."
            }
        }
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(normalized)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resend && resendToken != null) builder.setForceResendingToken(resendToken!!)
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
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
            Text("Sign in to securely sync your trading records", color = Color(0xFF69758C), fontSize = 13.sp)
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(16) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mobile number") },
                placeholder = { Text("+91 9876543210") },
                leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(14.dp)
            )
            if (verificationId != null) {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("SMS OTP") },
                    placeholder = { Text("Enter 6-digit code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp)
                )
            }
            if (message.isNotBlank()) Text(message, color = if (isError) Color(0xFFCC3348) else Color(0xFF138A55), fontSize = 12.sp)
            Button(
                onClick = {
                    if (verificationId == null) requestOtp()
                    else {
                        val id = verificationId
                        if (id == null || otp.length < 6) {
                            isError = true
                            message = "Enter the 6-digit OTP."
                        } else {
                            finishSignIn(PhoneAuthProvider.getCredential(id, otp))
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LoginBlue)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (verificationId == null) "SEND OTP" else "VERIFY & SIGN IN", fontWeight = FontWeight.Bold)
            }
            if (verificationId != null) {
                TextButton(onClick = { requestOtp(resend = true) }, enabled = !busy) { Text("Resend OTP") }
            }
            Text("Each mobile number has its own private cloud data.", color = Color(0xFF69758C), fontSize = 11.sp)
        }
    }
}
