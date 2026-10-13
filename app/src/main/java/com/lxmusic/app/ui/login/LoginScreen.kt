package com.lxmusic.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalContext
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.MusicRepository
import com.lxmusic.app.ui.appViewModel
import com.lxmusic.app.ui.theme.BrandGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(app: LxApp) : ViewModel() {
    private val app: LxApp = app
    private val session = app.sessionManager
    private val repo: MusicRepository get() = app.repository

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    val savedServerUrl: String get() = session.serverUrl
    val savedUsername: String get() = session.username

    fun checkSaved(onValid: () -> Unit) {
        if (!session.hasCredentials) return
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                repo.ping()
                _uiState.value = LoginUiState.Idle
                onValid()
            } catch (_: Exception) {
                _uiState.value = LoginUiState.Idle
            }
        }
    }

    fun login(serverUrl: String, username: String, password: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.isEmpty()) {
            _uiState.value = LoginUiState.Error("请输入用户名和密码")
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                session.setServerUrl(serverUrl)
                repo.login(username, password)
                _uiState.value = LoginUiState.Idle
                onSuccess()
            } catch (e: Exception) {
                // HTTPS 的 SSL 握手失败时，自动降级用 HTTP 重试
                val isSsl = e is javax.net.ssl.SSLHandshakeException || e is javax.net.ssl.SSLException
                val httpsUrl = session.serverUrl
                if (isSsl && httpsUrl.startsWith("https://")) {
                    try {
                        val httpUrl = "http://" + httpsUrl.removePrefix("https://")
                        session.setServerUrl(httpUrl)
                        repo.login(username, password)
                        _uiState.value = LoginUiState.Idle
                        onSuccess()
                        return@launch
                    } catch (_: Exception) { /* HTTP 也失败，走下面的通用报错 */ }
                }
                val code = (e as? MusicRepository.ApiException)?.code
                _uiState.value = LoginUiState.Error(
                    when (code) {
                        40 -> "用户名或密码错误"
                        else -> (e as? MusicRepository.ApiException)?.message?.takeIf { it.isNotBlank() }
                            ?: "连接失败(${e.javaClass.simpleName}: ${e.message?.take(60)})，请检查网络和服务器地址"
                    }
                )
            }
        }
    }

    fun clearError() { _uiState.value = LoginUiState.Idle }
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onOfflineMode: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as LxApp
    val vm: LoginViewModel = appViewModel { LoginViewModel(it) }
    val uiState by vm.uiState.collectAsState()

    var serverUrl by remember { mutableStateOf(vm.savedServerUrl) }
    var username by remember { mutableStateOf(vm.savedUsername) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.checkSaved(onLoggedIn) }

    val loading = uiState is LoginUiState.Loading
    val errorMsg = (uiState as? LoginUiState.Error)?.message

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(BrandGreen, Color(0xFF0E9F6E)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "LX Music",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "随时随地，畅听你的音乐",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(36.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = BrandGreen,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = BrandGreen,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedLeadingIconColor = BrandGreen,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; vm.clearError() },
                label = { Text("服务器地址") },
                leadingIcon = { Icon(Icons.Default.Dns, null, Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; vm.clearError() },
                label = { Text("用户名") },
                leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; vm.clearError() },
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Default.Lock, null, Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, Modifier.size(20.dp)
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMsg != null) {
                Text(
                    errorMsg,
                    color = Color(0xFFFF8A8A),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.login(serverUrl, username, password, onLoggedIn) },
                enabled = !loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGreen,
                    contentColor = Color(0xFF06281A),
                    disabledContainerColor = Color(0xFF1E4A38)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFF06281A),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text("登 录", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "账号由管理员在后台开通",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    // 离线模式：不登录，直接进主界面播本地
                    app.downloadManager.setOfflineMode(true)
                    onOfflineMode()
                }
            ) {
                Text(
                    "没有网络？离线模式进入",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandGreen
                )
            }
        }
    }
}
