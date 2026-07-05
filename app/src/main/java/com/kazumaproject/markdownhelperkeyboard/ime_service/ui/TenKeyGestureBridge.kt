package com.kazumaproject.markdownhelperkeyboard.ime_service.ui

/**
 * [InputActionDispatcher] から注入可能な TenKey ジェスチャ bridge（単体テスト用）。
 */
fun interface TenKeyGestureBridge {
    fun dispatch(
        request: TapFlickInputBridge.TapFlickDispatchRequest,
        sb: StringBuilder,
        session: TapFlickInputBridge.TapFlickSessionHooks,
        surfaceActions: TapFlickInputBridge.TapFlickSurfaceActions,
    )
}

class DefaultTenKeyGestureBridge : TenKeyGestureBridge {
    private val delegate = TapFlickInputBridge()
    override fun dispatch(
        request: TapFlickInputBridge.TapFlickDispatchRequest,
        sb: StringBuilder,
        session: TapFlickInputBridge.TapFlickSessionHooks,
        surfaceActions: TapFlickInputBridge.TapFlickSurfaceActions,
    ) {
        delegate.dispatch(request, sb, session, surfaceActions)
    }
}