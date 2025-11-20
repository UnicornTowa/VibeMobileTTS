package com.unicorntowa.VoskTTSMobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unicorntowa.VoskTTSMobile.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel: TtsViewModel by viewModels()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TtsScreen(viewModel)
                }
            }
        }
    }
}

fun Float.format(digits: Int) = "%.${digits}f".format(this)

@Composable
fun TtsScreen(viewModel: TtsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Model selector
        ModelSelector(
            currentModel = viewModel.currentModelType,
            onModelSwitch = { viewModel.switchModel() },
            enabled = !viewModel.isModelLoading
        )

        when {
            viewModel.initializationError != null -> {
                Text("Error: ${viewModel.initializationError}", color = MaterialTheme.colorScheme.error)
            }
            viewModel.isModelLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(viewModel.loadingMessage)
                }
            }
            !viewModel.isInitialized -> {
                Text("Initializing...")
            }
            else -> {
                OutlinedTextField(
                    value = viewModel.inputText,
                    onValueChange = { viewModel.onInputTextChanged(it) },
                    label = { Text("Enter text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )

                Button(
                    onClick = { viewModel.synthesizeText() },
                    modifier = Modifier.padding(top = 8.dp),
                    enabled = viewModel.isInitialized && viewModel.inputText.isNotBlank()
                ) {
                    Text("Synthesize")
                }

                viewModel.synthesisTime?.let {
                    Text("Synthesis time: $it ms", modifier = Modifier.padding(top = 8.dp))
                }
                viewModel.audioDurationMs?.let {
                    Text("Audio Duration: ${it} ms", modifier = Modifier.padding(top = 8.dp))
                }
                viewModel.rtf?.let {
                    Text("RTF: ${it.format(2)}", modifier = Modifier.padding(top = 8.dp))
                }
                viewModel.numTokens?.let {
                    Text("Number of tokens: $it", modifier = Modifier.padding(top = 8.dp))
                }
                viewModel.tokensPerSecond?.let {
                    Text(
                        "Generation Speed: %.1f tokens/sec".format(it),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (viewModel.hasSynthesizedAudio) {
                    Player(
                        isPlaying = viewModel.isPlaying,
                        onPlayPauseClicked = { viewModel.onPlayPauseClicked() }
                    )
                }
            }
        }
    }
}


@Composable
fun ModelSelector(
    currentModel: ModelType,
    onModelSwitch: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onModelSwitch,
            enabled = enabled
        ) {
            Text("◄", fontSize = 24.sp)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current Model:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = currentModel.displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(
            onClick = onModelSwitch,
            enabled = enabled
        ) {
            Text("►", fontSize = 24.sp)
        }
    }
}

@Composable
fun Player(isPlaying: Boolean, onPlayPauseClicked: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPlayPauseClicked) {
            Icon(
                painter = painterResource(
                    if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play_arrow
                ),
                contentDescription = null
            )
        }
        Text(if (isPlaying) "Playing" else "Stopped")
    }
}

@Preview(showBackground = true)
@Composable
fun TtsScreenPreview() {
    MyApplicationTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Preview Mode")
        }
    }
}
