package online.paychek.app.ui.checkout

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import online.paychek.app.MainActivity

/**
 * Full-screen in-app checkout WebView.
 * Back returns to the previous screen (typically Dashboard / subscription).
 * Does not alter payment APIs — only hosts the same checkout URL.
 */
class CheckoutActivity : FragmentActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (3 * resources.displayMetrics.density).toInt()
            )
            max = 100
            progress = 0
            isIndeterminate = false
        }
        root.addView(webView)
        root.addView(progress)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        val rawUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (rawUrl.isBlank()) {
            finish()
            return
        }
        val url = appendNativeFlag(rawUrl)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "$userAgentString PayChekApp/1.0"
        }
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return handleSpecialUri(uri)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url.isNullOrBlank()) return false
                return handleSpecialUri(Uri.parse(url))
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        webView.loadUrl(url)
    }

    private fun handleSpecialUri(uri: Uri): Boolean {
        if (uri.scheme == "paychek") {
            // Deliver deep link to MainActivity (billing success), then close WebView.
            val deep = Intent(Intent.ACTION_VIEW, uri).apply {
                setClass(this@CheckoutActivity, MainActivity::class.java)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(deep)
            finish()
            return true
        }
        // Keep http(s) inside WebView; open tel/mailto externally.
        val scheme = uri.scheme?.lowercase()
        if (scheme == "tel" || scheme == "mailto" || scheme == "sms") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: Exception) { /* no handler */ }
            return true
        }
        return false
    }

    override fun onDestroy() {
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (_: Exception) { /* ignore */ }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "checkout_url"

        fun intent(context: Context, url: String): Intent =
            Intent(context, CheckoutActivity::class.java).putExtra(EXTRA_URL, url)

        fun open(context: Context, url: String) {
            context.startActivity(intent(context, url))
        }

        private fun appendNativeFlag(url: String): String {
            return try {
                val uri = Uri.parse(url)
                if (uri.getQueryParameter("native") == "1") return url
                uri.buildUpon().appendQueryParameter("native", "1").build().toString()
            } catch (_: Exception) {
                if (url.contains("?")) "$url&native=1" else "$url?native=1"
            }
        }
    }
}
