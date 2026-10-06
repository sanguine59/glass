package com.example.myapplication

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    BackendHarness(Modifier.padding(insets))
                }
            }
        }
    }
}

@Composable
private fun BackendHarness(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val node = remember(context) { NodeRuntime(context) }
    val prefix = remember(node) { PrefixInstaller(context, node) }
    val installer = remember(node) { WorkspaceInstaller(context, node) }

    var log by remember { mutableStateOf(node.diagnostics() + "\n" + prefix.diagnostics()) }
    var busy by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf(false) }

    var serverRunning by remember { mutableStateOf(false) }

    fun append(line: String) {
        log = (log + "\n" + line).takeLast(20_000)
    }

    val devServer = remember(node) {
        DevServer(node, scope) { line ->
            append(line)
            if (line.startsWith("[vite exited")) serverRunning = false
        }
    }

    fun task(label: String, block: suspend () -> String) {
        if (busy) return
        busy = true
        log = "$label ...\n"
        scope.launch {
            val result = runCatching { block() }
            append(result.getOrElse { "FAILED: ${it.javaClass.simpleName}: ${it.message}" })
            busy = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("glass backend probe", style = MaterialTheme.typography.titleLarge)

        Button(
            onClick = {
                task("install prefix") {
                    prefix.install(force = true) { append(it) }.getOrThrow() +
                        "\n\n" + prefix.diagnostics()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("1. install node prefix (libs)") }

        Button(
            onClick = {
                task("node: platform / arch") {
                    node.eval("console.log(process.platform, process.arch, process.version)").describe()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("2. node -- platform / arch") }

        Button(
            onClick = {
                task("install workspace") {
                    installer.install(force = true) { append(it) }.getOrThrow()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("3. install workspace") }

        Button(
            onClick = { task("tsc --noEmit") { node.typecheck().describe() } },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("4. typecheck (tsc)") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = {
                    task("start vite") {
                        devServer.start().fold(
                            onSuccess = {
                                serverUrl = devServer.url
                                serverRunning = true
                                it
                            },
                            onFailure = { throw it },
                        )
                    }
                },
                enabled = !busy && !serverRunning,
                modifier = Modifier.fillMaxWidth(0.5f),
            ) { Text("5. start vite") }

            OutlinedButton(
                onClick = {
                    devServer.stop()
                    serverUrl = null
                    serverRunning = false
                    showPreview = false
                    append("[stopped]")
                },
                enabled = serverRunning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("stop") }
        }

        serverUrl?.let { url ->
            OutlinedButton(
                onClick = { showPreview = !showPreview },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (showPreview) "hide preview" else "preview $url") }
        }

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        if (showPreview && serverUrl != null) {
            Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadUrl(serverUrl!!)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = log,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}
