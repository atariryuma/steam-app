package com.steamdeck.mobile.presentation.ui.auth

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.steamdeck.mobile.presentation.theme.SteamColorPalette

/**
 * Steam OpenIDauthentication画面（公式規約準拠）
 *
 * Valve公式 OpenID 2.0authenticationuse
 * - QRコードauthentication 異なり、規約準拠 安全な方法
 * - WebView steamcommunity.com 公式ログインページ表示
 * - コールバックURL SteamID64retrieve
 *
 * 公式ドキュメント:
 * - https://partner.steamgames.com/doc/features/auth
 * - https://steamcommunity.com/dev
 *
 * Best Practice:
 * - JavaScript有効化（Steamログインフォーム動作 必要）
 * - DOM Storage有効化（セッション管理）
 * - リダイレクトインターセプトしてコールバック処理
 */
@Composable
fun SteamOpenIdLoginScreen(
 authUrl: String,
 callbackUrl: String = "http://127.0.0.1:8080/auth/callback",
 onAuthCallback: (String) -> Unit,
 onError: (String) -> Unit = {}
) {
 var isLoading by remember { mutableStateOf(true) }
 var loadProgress by remember { mutableStateOf(0) }

 Box(
  modifier = Modifier
   .fillMaxSize()
   .background(SteamColorPalette.Dark) // Steam dark blue
 ) {
  // WebView（フルスクリーン）
  AndroidView(
   factory = { context ->
    WebView(context).apply {
     settings.apply {
      javaScriptEnabled = true // Steamログインフォーム 必要
      domStorageEnabled = true // セッション管理
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
       android.util.Log.d("SteamOpenIdLogin", "URL redirect: $url")

       // コールバックURL 検出 (localhost/127.0.0.1 callback interception)
       // Check both localhost and 127.0.0.1 for compatibility
       if (url.contains("127.0.0.1:8080/auth/callback") ||
           url.contains("localhost:8080/auth/callback")) {
        android.util.Log.i("SteamOpenIdLogin", "✅ Callback detected: $url")
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
       onError("ページ読み込みエラー: $description")
      }
     }

     // authenticationURL読み込み
     loadUrl(authUrl)
    }
   },
   modifier = Modifier.fillMaxSize()
  )

  // プログレスバー（上部 表示）
  if (isLoading) {
   LinearProgressIndicator(
    progress = { loadProgress / 100f },
    modifier = Modifier
     .fillMaxWidth()
     .align(Alignment.TopCenter),
    color = SteamColorPalette.Blue
   )
  }

  // ローディングオーバーレイ（初回読み込み時 み）
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
 }
}
