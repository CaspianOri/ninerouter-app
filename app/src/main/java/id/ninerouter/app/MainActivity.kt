package id.ninerouter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Entry point of the 9Router app. The real chat / provider-management UI
 * replaces the placeholder screen in a later pass.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NineRouterApp()
        }
    }
}

/** Root composable: placeholder screen until the chat UI is implemented. */
@Composable
fun NineRouterApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "9Router")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NineRouterAppPreview() {
    NineRouterApp()
}
