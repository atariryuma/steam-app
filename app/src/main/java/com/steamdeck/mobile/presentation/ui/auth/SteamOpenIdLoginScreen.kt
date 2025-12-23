package com.steamdeck.mobile.presentation.ui.auth

import com.steamdeck.mobile.core.logging.AppLogger

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.steamdeck.mobile.R
import com.steamdeck.mobile.presentation.theme.SteamColorPalette

/**
 * Steam OpenID authentication screen (official ToS compliant)
 *
 * Uses Valve official OpenID 2.0 authentication
 * - Unlike QR code auth, this is ToS compliant and secure
 * - Displays official Steam login page via WebView
 * - Retrieves SteamID64 from callback URL
 *
 * Official documentation:
 * - https://partner.steamgames.com/doc/features/auth
 * - https://steamcommunity.com/dev
 *
 * Best Practice:
 * - JavaScript enabled (required for Steam login form)
 * - DOM Storage enabled (session management)
 * - Intercept redirect for callback processing
 */
@Composable
fun SteamOpenIdLoginScreen(
 authUrl: String,
 callbackUrl: String = "http://127.0.0.1:8080/auth/callback",
 onAuthCallback: (String) -> Unit,
 onError: (String) -> Unit = {},
 onCancel: () -> Unit
) {
 var isLoading by remember { mutableStateOf(true) }
 var loadProgress by remember { mutableStateOf(0) }
 var webViewRef by remember { mutableStateOf<WebView?>(null) }

 Box(
  modifier = Modifier
   .fillMaxSize()
   .background(SteamColorPalette.Dark) // Steam dark blue
 ) {
  // WebView (fullscreen)
  AndroidView(
   factory = { context ->
    WebView(context).apply {
     settings.apply {
      javaScriptEnabled = true // Required for Steam login form
      domStorageEnabled = true // Session management
      setSupportMultipleWindows(false)
      loadWithOverviewMode = true
      useWideViewPort = true
     }

     webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(
       view: WebView?,
       request: WebResourceRequest?
      ): Boolean {
       val url = request?.url.toString()
       AppLogger.d("SteamOpenIdLogin", "URL redirect: $url")

       // Detect callback URL (localhost/127.0.0.1 callback interception)
       // Check both localhost and 127.0.0.1 for compatibility
       if (url.contains("127.0.0.1:8080/auth/callback") ||
           url.contains("localhost:8080/auth/callback")) {
        AppLogger.i("SteamOpenIdLogin", "✅ Callback detected: $url")
        onAuthCallback(url)
        return true // Prevent WebView from trying to load localhost
       }

       return false
      }

      override fun onPageFinished(view: WebView?, url: String?) {
       super.onPageFinished(view, url)
       isLoading = false
      }

      override fun onReceivedError(
       view: WebView?,
       errorCode: Int,
       description: String?,
       failingUrl: String?
      ) {
       super.onReceivedError(view, errorCode, description, failingUrl)
       onError("Page load error: $description")
      }
     }

     // Load authentication URL
     loadUrl(authUrl)

     // Store WebView reference for back navigation
     webViewRef = this
    }
   },
   modifier = Modifier.fillMaxSize()
  )

  // Progress bar (displayed at top)
  if (isLoading) {
   LinearProgressIndicator(
    progress = { loadProgress / 100f },
    modifier = Modifier
     .fillMaxWidth()
     .align(Alignment.TopCenter),
    color = SteamColorPalette.Blue
   )
  }

  // Loading overlay (only on initial load)
  if (isLoading) {
   Box(
    modifier = Modifier
     .fillMaxSize()
     .background(SteamColorPalette.Dark.copy(alpha = 0.9f)),
    contentAlignment = Alignment.Center
   ) {
    CircularProgressIndicator(
     color = SteamColorPalette.Blue
    )
   }
  }

  // Back button (conditional - only show when page history exists)
  if (!isLoading && webViewRef?.canGoBack() == true) {
   FloatingActionButton(
    onClick = { webViewRef?.goBack() },
    modifier = Modifier
     .align(Alignment.TopStart)
     .padding(16.dp),
    containerColor = SteamColorPalette.Medium
   ) {
    Icon(
     imageVector = Icons.AutoMirrored.Filled.ArrowBack,
     contentDescription = stringResource(R.string.content_desc_back_button),
     tint = androidx.compose.ui.graphics.Color.White
    )
   }
  }

  // Cancel button (always visible - top right)
  IconButton(
   onClick = onCancel,
   modifier = Modifier
    .align(Alignment.TopEnd)
    .padding(16.dp)
  ) {
   Surface(
    shape = CircleShape,
    color = SteamColorPalette.Medium,
    modifier = Modifier.size(40.dp)
   ) {
    Box(
     contentAlignment = Alignment.Center,
     modifier = Modifier.fillMaxSize()
    ) {
     Icon(
      imageVector = Icons.Default.Close,
      contentDescription = stringResource(R.string.content_desc_cancel_login),
      tint = androidx.compose.ui.graphics.Color.White,
      modifier = Modifier.size(24.dp)
     )
    }
   }
  }
 }
}
