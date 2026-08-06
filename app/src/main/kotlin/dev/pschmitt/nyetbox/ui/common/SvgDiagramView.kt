package dev.pschmitt.nyetbox.ui.common

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.viewinterop.AndroidView
import dev.pschmitt.nyetbox.data.schema.NetBoxRef
import dev.pschmitt.nyetbox.scanner.NetBoxTarget
import dev.pschmitt.nyetbox.scanner.NetBoxUrlParser

private val svgOpenTagRegex = Regex("""<svg\b[^>]*>""")
private val svgWidthRegex = Regex("""\bwidth="([0-9.]+)"""")
private val svgHeightRegex = Regex("""\bheight="([0-9.]+)"""")

/**
 * The root `<svg>`'s own `width`/`height` attributes, as a width-over-height ratio - used to size
 * the WebView to exactly the diagram's rendered height instead of an arbitrary fixed box. Without
 * this, the WebView is either taller than the content (wasted space) or shorter (forcing the SVG
 * itself to scroll internally, which - since a WebView isn't a Compose nested-scroll participant -
 * then blocks the surrounding list from ever being scrolled by a drag that starts on the diagram).
 */
private fun svgAspectRatio(svg: String): Float? {
    val openTag = svgOpenTagRegex.find(svg)?.value ?: return null
    val width = svgWidthRegex.find(openTag)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
    val height = svgHeightRegex.find(openTag)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
    if (height <= 0f) return null
    return width / height
}

/**
 * NetBox's rendered SVGs carry `width`/`height` on the root `<svg>` but no `viewBox` - fine for the
 * browser tab NetBox renders them in, but it means a raw CSS `width: 100%` in the wrapper below has
 * no coordinate space to scale against, so the diagram would stay pinned at its original (tiny,
 * device-pixel) size instead of filling the WebView. Deriving a `viewBox` from the existing
 * `width`/`height` gives the SVG that scale reference without altering how it draws.
 */
private fun ensureViewBox(svg: String): String {
    val openTag = svgOpenTagRegex.find(svg)?.value ?: return svg
    if ("viewBox=" in openTag) return svg
    val width = svgWidthRegex.find(openTag)?.groupValues?.get(1) ?: return svg
    val height = svgHeightRegex.find(openTag)?.groupValues?.get(1) ?: return svg
    val withViewBox = openTag.replaceFirst("<svg", """<svg viewBox="0 0 $width $height"""")
    return svg.replaceFirst(openTag, withViewBox)
}

/**
 * Wraps the raw SVG in a minimal HTML document (rather than loading it as the top-level
 * `image/svg+xml` document as before) purely to attach a real mobile viewport meta tag - without
 * one, [android.webkit.WebSettings.setUseWideViewPort] has no page-declared viewport to honor and
 * falls back to assuming a ~980px desktop layout, shrinking the whole diagram to fit. The SVG
 * itself stays inlined directly in the body (not behind an `<img src>`) so its embedded `<a>` links
 * remain live DOM nodes that [WebViewClient.shouldOverrideUrlLoading] can still intercept.
 */
private fun wrapSvgDocument(svg: String): String =
    """
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=6, user-scalable=yes">
    <style>
      html, body { margin: 0; padding: 0; background: transparent; }
      svg { display: block; width: 100%; height: auto; }
    </style>
    </head>
    <body>${ensureViewBox(svg)}</body>
    </html>
    """
        .trimIndent()

/**
 * Renders a NetBox `?render=svg` diagram (rack elevation, cable trace) via a WebView rather than an
 * image loader - SVGs from NetBox embed `<a>` links to its own web UI (device/interface/cable
 * pages, plus an "add device here" link for empty rack slots), and only a WebView's navigation
 * callback lets those taps be intercepted at all. No JavaScript is needed or enabled - `<a>` clicks
 * reach [WebViewClient.shouldOverrideUrlLoading] on their own.
 *
 * Every navigation attempt is resolved through [NetBoxUrlParser] (the same parser used for scanned/
 * opened NetBox URLs elsewhere) and blocked from ever loading in the WebView - a resolvable link
 * calls [onNavigate] instead of navigating away from the diagram into NetBox's web UI; an
 * unresolvable one (e.g. the "add device" link on an empty rack slot) is just a no-op tap.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SvgDiagramView(
    svg: String?,
    baseUrl: String,
    onNavigate: (endpointPath: String, id: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only used while there's no SVG yet to derive a real aspect ratio from - a fixed height
    // (previously 480dp) left most of the screen unused on phones and especially tablets, so this
    // loading placeholder uses most of the available screen height instead. LocalWindowInfo's
    // pixel size (rather than the deprecated LocalConfiguration.screenHeightDp) converted through
    // the current density, per the ConfigurationScreenWidthHeight lint check.
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val diagramHeight = with(density) { (windowHeightPx * 0.75f).toDp() }
    if (svg == null) {
        Box(modifier.fillMaxWidth().height(diagramHeight), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    // Size the WebView to the diagram's own aspect ratio rather than a fixed height - see
    // [svgAspectRatio] for why a mismatched height would break scrolling past the diagram.
    val aspectRatio = remember(svg) { svgAspectRatio(svg) }
    AndroidView(
        modifier =
            modifier.fillMaxWidth().let {
                if (aspectRatio != null) it.aspectRatio(aspectRatio) else it.height(diagramHeight)
            },
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // The diagram sits inside a scrollable Compose list, whose scroll gesture detector
                // otherwise wins arbitration over a pinch that starts on the WebView, swallowing
                // the
                // second pointer before the WebView's own builtInZoomControls ever sees it. Only
                // disallow interception while a second finger is actually down - unconditionally
                // disallowing it would also block the ordinary single-finger drags that scroll the
                // surrounding list past the diagram.
                setOnTouchListener { view, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                    view.parent?.requestDisallowInterceptTouchEvent(event.pointerCount >= 2)
                    false
                }
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            when (val target = NetBoxUrlParser.parse(request.url.toString())) {
                                is NetBoxTarget.Device ->
                                    onNavigate(NetBoxRef.DEVICES_ENDPOINT_PATH, target.id)
                                is NetBoxTarget.Object -> onNavigate(target.endpointPath, target.id)
                                else -> {}
                            }
                            return true
                        }
                    }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(baseUrl, wrapSvgDocument(svg), "text/html", "utf-8", null)
        },
    )
}
