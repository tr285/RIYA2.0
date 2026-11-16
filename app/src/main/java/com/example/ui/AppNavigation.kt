package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.RiyaTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

@Composable
fun AppNavigation() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        RiyaTheme(darkTheme = true) {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        }
    } else {
        val uid = user.uid
        val displayName = user.displayName ?: user.email?.substringBefore('@') ?: "RIYA User"
        val email = user.email
        key(uid) {
            RiyaMainShell(
                currentUserId = uid,
                displayName = displayName,
                email = email
            )
        }
    }
}
