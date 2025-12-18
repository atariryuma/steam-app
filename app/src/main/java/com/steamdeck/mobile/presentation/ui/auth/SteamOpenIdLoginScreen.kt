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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Steam OpenID認証画面（公式規約準拠）
 *
 * Valve公式のOpenID 2.0認証を使用
 * - QRコード認証と異なり、規約準拠の安全な方法
 * - WebViewでsteamcommunity.comの公式ログインページを表示
 * - コールバックURLでSteamID64を取得
 *
 * 公式ドキュメント:
 * - https://partner.steamgames.com/doc/features/auth
 * - https://steamcommunity.com/dev
 *
 * Best Practice:
 * - JavaScript有効化（Steamログインフォーム動作に必要）
 * - DOM Storage有効化（セッション管理）
 * - リダイレクトをインターセプトしてコールバック処理
 */
@Composable
fun SteamOpenIdLoginScreen(
    authUrl: String,
    callbackScheme: String = "steamdeckmobile",
    onAuthCallback: (String) -> Unit,
    onError: (String) -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B2838)) // Steam dark blue
    ) {
        // WebView（フルスクリーン）
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true // Steamログインフォームに必要
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

                            // コールバックURLの検出
                            if (url.startsWith("$callbackScheme://")) {
                                onAuthCallback(url)
                                return true
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

                    // 認証URL読み込み
                    loadUrl(authUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // プログレスバー（上部に表示）
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color(0xFF66C0F4)
            )
        }

        // ローディングオーバーレイ（初回読み込み時のみ）
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1B2838).copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF66C0F4)
                )
            }
        }
    }
}
