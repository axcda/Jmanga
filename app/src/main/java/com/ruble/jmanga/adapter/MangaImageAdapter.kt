package com.ruble.jmanga.adapter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.ruble.jmanga.App
import com.ruble.jmanga.R
import com.ruble.jmanga.cloudflare.CloudflareSolver
import com.ruble.jmanga.glide.GlideApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class MangaImageAdapter : ListAdapter<String, MangaImageAdapter.ViewHolder>(MangaImageDiffCallback()) {
    private val TAG = "MangaImageAdapter"
    
    // 添加加载状态控制
    private var firstImageLoaded = false
    private var loadingInProgress = false
    
    // 设置一个静态的Cookie管理器
    companion object {
        private val cookieManager = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // 注意：setAcceptThirdPartyCookies需要一个WebView参数，不能在这里调用
        }
        
        // 漫画图片标准尺寸比例
        private const val IMAGE_WIDTH = 318
        private const val IMAGE_HEIGHT = 453
        private const val IMAGE_RATIO = IMAGE_HEIGHT.toFloat() / IMAGE_WIDTH.toFloat()
        
        // 增加安全边距，防止图片被截断
        private const val HEIGHT_SAFETY_MARGIN = 200
        
        // 添加图片之间的分隔间距
        private const val IMAGE_SPACING = 0
    }

    // 添加待加载队列
    private val pendingLoadRequests = mutableListOf<Pair<ViewHolder, Int>>()
    
    // 添加图片加载完成监听器
    interface OnImageLoadListener {
        fun onImageLoaded(position: Int, success: Boolean)
    }
    
    private var imageLoadListener: OnImageLoadListener? = null
    
    fun setOnImageLoadListener(listener: OnImageLoadListener) {
        this.imageLoadListener = listener
    }
    
    // 处理图片加载完成事件
    fun notifyImageLoaded(position: Int, success: Boolean) {
        imageLoadListener?.onImageLoaded(position, success)
        
        if (position == 0 && success) {
            firstImageLoaded = true
            // 第一张图片加载完成后，处理待加载队列
            processPendingRequests()
        }
    }
    
    private fun processPendingRequests() {
        if (pendingLoadRequests.isNotEmpty() && !loadingInProgress) {
            loadingInProgress = true
            val (holder, position) = pendingLoadRequests.removeAt(0)
            holder.loadImage(getItem(position), position, true)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manga_image, parent, false)
        return ViewHolder(view, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageUrl = getItem(position)
        
        // 优先加载第一张图片，其他图片等待第一张加载完成
        if (position == 0 || firstImageLoaded) {
            holder.loadImage(imageUrl, position, true)
        } else {
            // 保存请求到待加载队列
            pendingLoadRequests.add(Pair(holder, position))
            // 预先显示占位图
            holder.showPlaceholder()
        }
        
        // 移除所有间距设置，确保无缝连接
        val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
        layoutParams.topMargin = 0
        layoutParams.bottomMargin = 0
        holder.itemView.layoutParams = layoutParams
    }

    class ViewHolder(itemView: View, private val adapter: MangaImageAdapter) : RecyclerView.ViewHolder(itemView) {
        private val mangaImage: ImageView = itemView.findViewById(R.id.manga_image)
        private val mangaWebView: WebView = itemView.findViewById(R.id.manga_webview)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.image_progress)
        private val TAG = "MangaImageViewHolder"
        private var isRetrying = false
        private var hasCompletedCfChallenge = false
        
        // 显示占位图
        fun showPlaceholder() {
            progressBar.visibility = View.VISIBLE
            mangaWebView.visibility = View.INVISIBLE
            mangaImage.visibility = View.INVISIBLE
        }
        
        // 定义为类成员而不是局部类避免内存泄漏和引用问题
        private val jsInterface = WebViewJsInterface()
        
        inner class WebViewJsInterface {
            private var hasSetHeight = false
            
            @JavascriptInterface
            fun onHeightChanged(height: Int) {
                Log.d(TAG, "JS报告的图片高度: $height")
                if (height > 0 && !hasSetHeight) {
                    hasSetHeight = true  // 标记已设置高度，防止重复设置
                    mangaWebView.post {
                        val layoutParams = mangaWebView.layoutParams
                        // 一次性添加足够的安全边距，防止图片被截断
                        val finalHeight = height + HEIGHT_SAFETY_MARGIN + 100
                        layoutParams.height = finalHeight
                        mangaWebView.layoutParams = layoutParams
                        mangaWebView.requestLayout()
                        
                        // 确保内容可见
                        mangaWebView.visibility = View.VISIBLE
                        
                        // 记录已应用高度
                        Log.d(TAG, "已应用WebView高度: ${finalHeight}（包含安全边距）")
                        
                        // 强制RecyclerView更新该项的布局
                        (mangaWebView.parent as? View)?.let { parentView ->
                            parentView.requestLayout()
                            // 给整体布局一个固定的tag，防止被重复计算
                            parentView.tag = "height_fixed_${finalHeight}"
                        }
                    }
                }
            }
            
            @JavascriptInterface
            fun onImageLoadComplete(success: Boolean, position: Int) {
                Log.d(TAG, "图片加载完成通知: position=$position, success=$success")
                mangaWebView.post {
                    progressBar.visibility = View.GONE
                    // 通知适配器图片加载完成
                    adapter.notifyImageLoaded(position, success)
                    // 重置加载标志
                    adapter.loadingInProgress = false
                    // 处理下一个待加载请求
                    adapter.processPendingRequests()
                }
            }
        }
        
        // 修改bind方法为loadImage方法
        fun loadImage(imageUrl: String, position: Int, shouldLoad: Boolean) {
            if (!shouldLoad) {
                showPlaceholder()
                return
            }
            
            Log.d(TAG, "加载图片: $imageUrl, position: $position")
            progressBar.visibility = View.VISIBLE
            mangaWebView.visibility = View.VISIBLE
            
            // 重置重试标志
            isRetrying = false
            hasCompletedCfChallenge = false
            
            // 预先计算并设置一个基于屏幕宽度和已知图片比例的高度
            val screenWidth = itemView.context.resources.displayMetrics.widthPixels
            // 增加预估高度的安全边距
            val estimatedHeight = (screenWidth * IMAGE_RATIO).toInt() + HEIGHT_SAFETY_MARGIN * 2
            
            Log.d(TAG, "预估图片高度: $estimatedHeight (基于屏幕宽度: $screenWidth，包含安全边距)")
            
            // 重置WebView高度 - 使用预估高度
            val params = mangaWebView.layoutParams
            params.height = estimatedHeight
            mangaWebView.layoutParams = params
            
            // 配置WebView
            mangaWebView.settings.apply {
                // 启用JavaScript支持，以便处理可能的Cloudflare挑战
                javaScriptEnabled = true
                // 启用DOM存储
                domStorageEnabled = true
                // 允许混合内容
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // 设置缓存模式
                cacheMode = WebSettings.LOAD_DEFAULT
                // 允许缩放
                builtInZoomControls = true
                displayZoomControls = false
                // 设置默认字体大小
                defaultFontSize = 16
                // 自适应屏幕
                useWideViewPort = true
                loadWithOverviewMode = true
                // 启用数据库
                databaseEnabled = true
                // 设置更高级的用户代理
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                // 允许通用访问
                allowContentAccess = true
                allowFileAccess = true
                // 启用地理位置 (可能需要的权限)
                setGeolocationEnabled(true)
                // 启用应用缓存 - 已废弃，不再使用
                // 在API 21 (Lollipop)之后，应用缓存API已被废弃
                // 添加额外设置
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }
            
            // 确保WebView可见
            mangaWebView.visibility = View.VISIBLE
            
            // 启用JavaScript接口
            mangaWebView.addJavascriptInterface(jsInterface, "ImageHeightInterface")
            
            // 先尝试加载HTML包装的图片（会触发拦截）
            val htmlData = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        html, body {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            height: auto;
                            background: transparent;
                            overflow: visible; /* 允许内容超出容器 */
                        }
                        .container {
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            width: 100%;
                            padding: 0;
                            margin: 0;
                            overflow: visible; /* 允许内容超出容器 */
                            position: relative; /* 确保定位上下文 */
                            border: none;
                        }
                        img {
                            width: 100%;
                            height: auto;
                            max-width: 100%;
                            display: block;
                            margin: 0;
                            padding: 0;
                        }
                    </style>
                    <script>
                        // 跟踪是否已经计算过高度
                        var heightCalculated = false;
                        
                        function reportImageHeight() {
                            try {
                                var img = document.getElementById('mangaImage');
                                if (img && img.complete && !heightCalculated) {
                                    heightCalculated = true; // 防止重复计算
                                    
                                    // 获取图片原始尺寸
                                    var naturalWidth = img.naturalWidth || 318;  // 默认宽度
                                    var naturalHeight = img.naturalHeight || 453; // 默认高度
                                    
                                    // 获取当前显示宽度
                                    var displayWidth = img.offsetWidth;
                                    
                                    // 按比例计算正确高度，一次性增加足够的安全边距
                                    var ratio = naturalHeight / naturalWidth;
                                    var displayHeight = Math.floor(displayWidth * ratio) + 300;
                                    
                                    console.log('图片自然尺寸: ' + naturalWidth + 'x' + naturalHeight);
                                    console.log('计算显示高度: ' + displayHeight + '（包含安全边距）');
                                    
                                    // 设置明确的固定高度
                                    document.body.style.height = displayHeight + 'px';
                                    document.body.style.minHeight = displayHeight + 'px';
                                    
                                    // 设置图片容器的固定高度
                                    var container = document.querySelector('.container');
                                    if (container) {
                                        container.style.height = (displayHeight - 100) + 'px';
                                        container.style.minHeight = (displayHeight - 100) + 'px';
                                    }
                                    
                                    // 传递计算出的高度
                                    if (window.ImageHeightInterface) {
                                        window.ImageHeightInterface.onHeightChanged(displayHeight);
                                        
                                        // 通知加载完成
                                        window.ImageHeightInterface.onImageLoadComplete(true, $position);
                                    }
                                }
                            } catch (e) {
                                console.error('Height calculation error:', e);
                            }
                        }
                        
                        // 安全地检查图片可见性
                        function checkImageVisibility() {
                            try {
                                var img = document.getElementById('mangaImage');
                                if (!img) return;
                                
                                var rect = img.getBoundingClientRect();
                                console.log('图片位置: 顶部=' + rect.top + ', 底部=' + rect.bottom + ', 高度=' + rect.height);
                                console.log('视口高度: ' + window.innerHeight);
                            } catch (e) {
                                console.error('Image visibility check error:', e);
                            }
                        }
                        
                        // 确保只运行一次
                        var onloadFired = false;
                        window.onload = function() {
                            if (!onloadFired) {
                                onloadFired = true;
                                // 延迟计算高度，确保图片加载完成
                                setTimeout(reportImageHeight, 300);
                            }
                        };
                        
                        // 为图片加载注册事件
                        document.addEventListener('DOMContentLoaded', function() {
                            try {
                                var img = document.getElementById('mangaImage');
                                if (img) {
                                    img.onload = reportImageHeight;
                                    img.onerror = function() {
                                        console.error('图片加载失败');
                                        if (window.ImageHeightInterface) {
                                            window.ImageHeightInterface.onImageLoadComplete(false, $position);
                                        }
                                    };
                                }
                            } catch (e) {
                                console.error('Event registration error:', e);
                            }
                        });
                        
                        // 处理图片加载错误
                        function handleImageError() {
                            console.error('图片加载失败');
                            if (window.ImageHeightInterface) {
                                window.ImageHeightInterface.onImageLoadComplete(false, $position);
                            }
                        }
                    </script>
                </head>
                <body>
                    <div class="container">
                        <img id="mangaImage" src="$imageUrl" alt="漫画图片" 
                            onload="reportImageHeight()" 
                            onerror="handleImageError()" />
                    </div>
                </body>
                </html>
            """.trimIndent()
            
            // 启用Cookie存储
            CookieManager.getInstance().setAcceptThirdPartyCookies(mangaWebView, true)
            
            // 设置WebChromeClient处理进度
            mangaWebView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    Log.d(TAG, "WebView加载进度: $newProgress%")
                    if (newProgress < 100) {
                        progressBar.visibility = View.VISIBLE
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }
            }
            
            // 设置WebViewClient处理页面加载和Cloudflare挑战
            mangaWebView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar.visibility = View.VISIBLE
                    Log.d(TAG, "WebView开始加载: $url")
                }
                
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    Log.d(TAG, "收到SSL错误: ${error?.primaryError}")
                    // 在生产环境中应该谨慎处理SSL错误，这里为了开发测试目的接受所有证书
                    handler?.proceed()
                }
                
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    request?.let {
                        val requestUrl = it.url.toString()
                        // 只拦截图片请求，其他请求正常处理
                        if (requestUrl == imageUrl) {
                            Log.d(TAG, "拦截图片请求: $requestUrl")
                            try {
                                // 使用URLConnection直接获取图片数据
                                val connection = URL(requestUrl).openConnection() as HttpURLConnection
                                connection.connectTimeout = 30000
                                connection.readTimeout = 30000
                                connection.requestMethod = "GET"
                                
                                // 添加必要的请求头
                                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                                connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                                connection.setRequestProperty("Referer", "https://godamanga.online/")
                                
                                // 如果是HTTPS连接，处理SSL问题
                                if (connection is javax.net.ssl.HttpsURLConnection) {
                                    try {
                                        // 创建允许所有主机名的主机名验证器
                                        val hostnameVerifier = HostnameVerifier { _, _ -> true }
                                        connection.hostnameVerifier = hostnameVerifier
                                        
                                        // 创建信任所有证书的SSLContext
                                        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                                            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                                        })
                                        
                                        val sslContext = SSLContext.getInstance("TLS")
                                        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                                        connection.sslSocketFactory = sslContext.socketFactory
                                    } catch (e: Exception) {
                                        Log.e(TAG, "设置SSL信任管理器失败", e)
                                    }
                                }
                                
                                // 获取当前域名的Cookie
                                val cookie = CookieManager.getInstance().getCookie(requestUrl)
                                if (!cookie.isNullOrEmpty()) {
                                    connection.setRequestProperty("Cookie", cookie)
                                }
                                
                                connection.connect()
                                
                                // 检查响应
                                val responseCode = connection.responseCode
                                Log.d(TAG, "图片请求响应码: $responseCode")
                                
                                if (responseCode == HttpURLConnection.HTTP_OK) {
                                    val inputStream = connection.inputStream
                                    val contentType = connection.contentType ?: "image/webp"
                                    
                                    // 返回自定义的WebResourceResponse
                                    return WebResourceResponse(
                                        contentType,
                                        "UTF-8",
                                        inputStream
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "拦截请求时出错", e)
                                if (!isRetrying) {
                                    isRetrying = true
                                    directLoadImage(imageUrl, position)
                                }
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "WebView加载完成: $url")
                    
                    // 页面加载完成后强制执行高度计算
                    view?.evaluateJavascript("reportImageHeight();", null)
                    
                    // 创建一个递归函数，确保多次尝试计算高度
                    fun retryHeightCalculation(attempts: Int, delay: Long) {
                        if (attempts <= 0) return
                        
                        view?.postDelayed({
                            view.evaluateJavascript("reportImageHeight();", null)
                            // 递归调用自身，减少尝试次数
                            retryHeightCalculation(attempts - 1, delay + 500)
                        }, delay)
                    }
                    
                    // 调用递归函数，尝试多次计算高度，延迟间隔递增
                    retryHeightCalculation(5, 500)
                    
                    if (url == "about:blank") {
                        // 直接加载原始图片URL
                        managedLoadUrl(imageUrl, position)
                        return
                    }
                    
                    // 检查是否是Cloudflare挑战页面
                    view?.evaluateJavascript("""
                        (function() {
                            if (document.querySelector('#challenge-running') || 
                                document.querySelector('.cf-browser-verification') ||
                                document.querySelector('#cf-please-wait')) {
                                return "challenge";
                            } else if (document.querySelector('img')) {
                                return "image";
                            } else {
                                return "unknown";
                            }
                        })();
                    """.trimIndent()) { result ->
                        Log.d(TAG, "页面检测结果: $result")
                        when {
                            result.contains("challenge") -> {
                                // 如果是Cloudflare挑战页面，等待挑战完成
                                Log.d(TAG, "检测到Cloudflare挑战，等待完成...")
                                // 保持进度条显示
                                progressBar.visibility = View.VISIBLE
                                
                                // 执行额外的JavaScript解决挑战
                                if (!hasCompletedCfChallenge) {
                                    hasCompletedCfChallenge = true
                                    
                                    // 尝试直接加载，可能会绕过挑战
                                    directLoadImage(imageUrl, position)
                                    
                                    // 可以尝试点击"我是人类"按钮
                                    view.evaluateJavascript("""
                                        (function() {
                                            var interval = setInterval(function() {
                                                var btn = document.querySelector('.cf-button');
                                                if (btn) {
                                                    btn.click();
                                                    clearInterval(interval);
                                                    return "clicked";
                                                }
                                                return "waiting";
                                            }, 500);
                                        })();
                                    """.trimIndent()) { clickResult ->
                                        Log.d(TAG, "尝试点击挑战按钮: $clickResult")
                                    }
                                }
                            }
                            result.contains("image") -> {
                                // 找到图片元素，已成功加载
                                Log.d(TAG, "检测到图片元素，加载成功")
                                progressBar.visibility = View.GONE
                                
                                // 调整WebView高度
                                adjustWebViewHeight(view)
                            }
                            else -> {
                                // 未知页面状态，尝试直接加载原始URL
                                Log.d(TAG, "未知页面状态，尝试其他加载方式")
                                if (!isRetrying) {
                                    isRetrying = true
                                    // 尝试直接加载原始URL
                                    directLoadImage(imageUrl, position)
                                }
                            }
                        }
                    }
                }
                
                private fun adjustWebViewHeight(view: WebView?) {
                    view?.evaluateJavascript("""
                        (function() {
                            const img = document.querySelector('img');
                            if (img && img.complete) {
                                // 获取图片自然尺寸
                                const naturalWidth = img.naturalWidth || 318;
                                const naturalHeight = img.naturalHeight || 453;
                                
                                // 获取当前显示宽度
                                const displayWidth = img.offsetWidth;
                                
                                // 按比例计算正确高度，加上额外的安全边距
                                const displayHeight = Math.floor((naturalHeight / naturalWidth) * displayWidth) + 50;
                                
                                console.log('图片自然尺寸: ' + naturalWidth + 'x' + naturalHeight);
                                console.log('计算显示高度: ' + displayHeight + '（包含安全边距）');
                                
                                // 返回计算出的高度（包含安全边距）
                                return displayHeight;
                            }
                            
                            // 如果图片尚未加载完成，设置一个监听器
                            const img = document.querySelector('img');
                            if (img) {
                                img.onload = function() {
                                    // 使用自然尺寸比例
                                    const naturalWidth = img.naturalWidth || 318;
                                    const naturalHeight = img.naturalHeight || 453;
                                    const displayWidth = img.offsetWidth;
                                    // 添加安全边距
                                    const calcHeight = Math.floor((naturalHeight / naturalWidth) * displayWidth) + 50;
                                    
                                    window.ImageHeightInterface.onHeightChanged(calcHeight);
                                };
                            }
                            
                            // 返回一个默认高度，基于已知比例，加上安全边距
                            const screenWidth = window.innerWidth;
                            return Math.floor(screenWidth * (453 / 318)) + 50;
                        })();
                    """.trimIndent()) { height ->
                        try {
                            val webViewHeight = height.replace("\"", "").toInt()
                            Log.d(TAG, "设置WebView高度: $webViewHeight（包含安全边距）")
                            if (webViewHeight > 0) {
                                mangaWebView.layoutParams.height = webViewHeight
                                mangaWebView.requestLayout()
                            } else {
                                // 如果计算高度无效，使用预估高度
                                val screenWidth = itemView.context.resources.displayMetrics.widthPixels
                                // 添加安全边距
                                val safeEstimatedHeight = (screenWidth * IMAGE_RATIO).toInt() + HEIGHT_SAFETY_MARGIN
                                mangaWebView.layoutParams.height = safeEstimatedHeight
                                mangaWebView.requestLayout()
                                Log.d(TAG, "使用预估高度: $safeEstimatedHeight（包含安全边距）")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "设置WebView高度失败", e)
                        }
                    }
                }
                
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Log.e(TAG, "WebView加载错误: $errorCode, $description, URL: $failingUrl")
                    
                    if (!isRetrying) {
                        isRetrying = true
                        // 尝试直接加载图片
                        directLoadImage(imageUrl, position)
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(itemView.context, "图片加载失败", Toast.LENGTH_SHORT).show()
                        // 通知加载失败
                        adapter.notifyImageLoaded(position, false)
                        adapter.loadingInProgress = false
                        adapter.processPendingRequests()
                    }
                }
            }
            
            // 加载HTML内容
            mangaWebView.loadDataWithBaseURL(imageUrl, htmlData, "text/html", "UTF-8", null)
        }
        
        @SuppressLint("SetJavaScriptEnabled")
        fun bind(imageUrl: String, position: Int) {
            // 委托给loadImage方法
            loadImage(imageUrl, position, position == 0 || adapter.firstImageLoaded)
        }
        
        // 管理URL加载，处理可能的特殊URL
        private fun managedLoadUrl(url: String, position: Int) {
            Log.d(TAG, "受管理的URL加载: $url")
            
            // 检查是否是WebP URL
            if (url.endsWith(".webp", ignoreCase = true)) {
                // 使用HTML直接嵌入WebP图片
                val webpHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body {
                                margin: 0;
                                padding: 0;
                                background: transparent;
                                overflow: visible; /* 允许内容超出容器 */
                            }
                            img {
                                width: 100%;
                                height: auto;
                                display: block;
                            }
                        </style>
                        <script>
                            function reportImageLoaded() {
                                var img = document.querySelector('img');
                                if (img && img.complete) {
                                    var naturalWidth = img.naturalWidth || 318;
                                    var naturalHeight = img.naturalHeight || 453;
                                    var displayWidth = img.offsetWidth;
                                    // 添加安全边距
                                    var displayHeight = Math.floor((naturalHeight / naturalWidth) * displayWidth) + 50;
                                    
                                    window.ImageHeightInterface.onHeightChanged(displayHeight);
                                    window.ImageHeightInterface.onImageLoadComplete(true, $position);
                                }
                            }
                            
                            function handleImageError() {
                                window.ImageHeightInterface.onImageLoadComplete(false, $position);
                            }
                            
                            window.onload = function() {
                                setTimeout(reportImageLoaded, 300);
                                // 多次延迟检查，确保图片完全加载
                                setTimeout(reportImageLoaded, 1000);
                                setTimeout(reportImageLoaded, 2000);
                            };
                        </script>
                    </head>
                    <body>
                        <img src="$url" alt="漫画图片" onload="reportImageLoaded()" onerror="handleImageError()" />
                    </body>
                    </html>
                """.trimIndent()
                
                mangaWebView.loadDataWithBaseURL(url, webpHtml, "text/html", "UTF-8", null)
            } else {
                // 直接加载URL
                mangaWebView.loadUrl(url)
            }
        }
        
        // 直接加载图片
        private fun directLoadImage(imageUrl: String, position: Int) {
            Log.d(TAG, "尝试直接加载图片: $imageUrl")
            
            // 在后台执行HTTP请求以获取图片
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 首先尝试使用Cloudflare解决器
                    val result = CloudflareSolver.getInstance(itemView.context).solve(imageUrl)
                    
                    withContext(Dispatchers.Main) {
                        when (result) {
                            is CloudflareSolver.Result.Success -> {
                                Log.d(TAG, "Cloudflare解决成功，加载图片")
                                // 使用成功解决后的Cookie进行加载
                                val cookies = result.data.headers("Set-Cookie")
                                if (cookies.isNotEmpty()) {
                                    // 保存Cookie以备后用
                                    val cookieManager = CookieManager.getInstance()
                                    for (cookie in cookies) {
                                        cookieManager.setCookie(imageUrl, cookie)
                                    }
                                    cookieManager.flush()
                                }
                                
                                // 再次尝试加载WebView
                                managedLoadUrl(imageUrl, position)
                            }
                            is CloudflareSolver.Result.Error -> {
                                Log.e(TAG, "Cloudflare解决失败，尝试使用Glide: ${result.exception.message}")
                                tryLoadWithGlide(imageUrl, position)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "直接加载图片失败", e)
                    withContext(Dispatchers.Main) {
                        tryLoadWithGlide(imageUrl, position)
                    }
                }
            }
        }
        
        private fun tryLoadWithGlide(imageUrl: String, position: Int) {
            Log.d(TAG, "尝试使用Glide加载图片: $imageUrl")
            
            // 显示ImageView，隐藏WebView
            mangaImage.visibility = View.VISIBLE
            mangaWebView.visibility = View.GONE
            
            // 设置请求选项
            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .format(DecodeFormat.PREFER_RGB_565)
                .downsample(DownsampleStrategy.CENTER_INSIDE)
                .timeout(30000)
            
            // 使用Glide加载图片
            GlideApp.with(itemView.context)
                .load(imageUrl)
                .apply(requestOptions)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        Log.e(TAG, "Glide加载图片失败: $imageUrl", e)
                        Toast.makeText(itemView.context, "图片加载失败", Toast.LENGTH_SHORT).show()
                        
                        // 通知加载失败
                        adapter.notifyImageLoaded(position, false)
                        adapter.loadingInProgress = false
                        adapter.processPendingRequests()
                        return false
                    }
                    
                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressBar.visibility = View.GONE
                        Log.d(TAG, "Glide加载图片成功: position: $position")
                        
                        // 通知加载成功
                        adapter.notifyImageLoaded(position, true)
                        adapter.loadingInProgress = false
                        adapter.processPendingRequests()
                        return false
                    }
                })
                .into(mangaImage)
        }
    }

    private class MangaImageDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
} 