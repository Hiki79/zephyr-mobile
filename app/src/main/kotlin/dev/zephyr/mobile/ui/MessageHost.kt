package dev.zephyr.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.zephyr.mobile.ZephyrState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Both entry points render feedback; only the foreground activity consumes it. */
@Composable
fun MessageHost(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                ZephyrState.toasts.collectLatest {
                    message = it
                    delay(4_000)
                    message = null
                }
            } finally { message = null }
        }
    }
    message?.let {
        Box(modifier.padding(horizontal = 16.dp).background(Z.ink, RoundedCornerShape(Z.radiusSm)).padding(13.dp)) {
            Text(it, fontSize = 12.5.sp, color = Color.White, lineHeight = 18.sp)
        }
    }
}
