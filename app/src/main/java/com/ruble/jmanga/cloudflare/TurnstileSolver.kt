package com.ruble.jmanga.cloudflare

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

class TurnstileSolver private constructor(private val context: Context) {
    companion object {
        private const val TAG = "TurnstileSolver"
        private const val TIMEOUT = 30000L // 30秒超时
        
        @Volatile
        private var instance: TurnstileSolver? = null
        
        fun getInstance(context: Context): TurnstileSolver {
            return instance ?: synchronized(this) {
                instance ?: TurnstileSolver(context).also { instance = it }
            }
        }
    }

//    @SuppressLint("SetJavaScriptEnabled")
    private val webView: WebView by lazy {
        WebView(context).apply {
            // 基本设置
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                
                // 性能优化
                cacheMode = WebSettings.LOAD_NO_CACHE
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                
                // 禁用不需要的功能
                loadsImagesAutomatically = false
                blockNetworkImage = true
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }
            
            // 启用硬件加速
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // 设置背景透明
            setBackgroundColor(0)
            
            // 禁用滚动条
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            
            // 禁用缩放
            settings.setSupportZoom(false)
            
            addJavascriptInterface(TurnstileJsInterface(), "TurnstileSolver")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // 注入监听脚本
                    view.evaluateJavascript("""
                        function waitForTurnstile() {
                            const observer = new MutationObserver((mutations) => {
                                for (const mutation of mutations) {
                                    if (mutation.type === 'attributes' && mutation.attributeName === 'value') {
                                        const token = document.querySelector('[name="cf-turnstile-response"]')?.value;
                                        if (token) {
                                            TurnstileSolver.onTokenReceived(token);
                                        }
                                    }
                                }
                            });
                            
                            observer.observe(document.body, {
                                attributes: true,
                                childList: true,
                                subtree: true
                            });
                            
                            // 自动点击 turnstile
                            setTimeout(() => {
                                const turnstile = document.querySelector('[data-hcaptcha-widget-id]');
                                if (turnstile) {
                                    turnstile.click();
                                }
                            }, 1000);
                        }
                        waitForTurnstile();
                    """.trimIndent(), null)
                }
                
                // 添加SSL错误处理
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    Log.d(TAG, "WebView收到SSL错误: ${error?.primaryError}")
                    // 在生产环境中应该谨慎处理SSL错误，这里为了开发测试目的接受所有证书
                    handler?.proceed()
                }
                
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e(TAG, "WebView加载错误: $errorCode, $description, URL: $failingUrl")
                }
            }
            
            // 设置最小尺寸以确保验证码可见
            minimumHeight = 100
            minimumWidth = 100
        }
    }
    
    private val tasks = mutableMapOf<String, CompletableDeferred<String>>()

    suspend fun solve(url: String): String = withContext(Dispatchers.IO) {
        try {
            val taskId = UUID.randomUUID().toString()
            val deferred = CompletableDeferred<String>()
            tasks[taskId] = deferred

            withContext(Dispatchers.Main) {
                webView.loadUrl(url)
            }

            // 等待结果或超时
            withTimeout(TIMEOUT) {
                val result = deferred.await()
                tasks.remove(taskId)
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "解决 Turnstile 失败", e)
            throw e
        }
    }

    inner class TurnstileJsInterface {
        @JavascriptInterface
        fun onTokenReceived(token: String) {
            Log.d(TAG, "收到 Turnstile 令牌")
            tasks.values.firstOrNull()?.complete(token)
        }
    }

    fun cleanup() {
        webView.apply {
            stopLoading()
            clearCache(true)
            clearHistory()
            destroy()
        }
        tasks.clear()
    }
} 