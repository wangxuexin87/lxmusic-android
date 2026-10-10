package com.lxmusic.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lxmusic.app.LxApp
import com.lxmusic.app.data.MusicRepository
import com.lxmusic.app.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(app: LxApp) : ViewModel() {
    private val repo: MusicRepository = app.repository
    private val session = app.sessionManager

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    /** 已有记住的账号时，校验一次有效性（直接用存下的凭证 ping） */
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

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.isEmpty()) {
            _uiState.value = LoginUiState.Error("请输入用户名和密码")
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                repo.login(username, password)
                _uiState.value = LoginUiState.Idle
                onSuccess()
            } catch (e: Exception) {
                val msg = (e as? MusicRepository.ApiException)?.message ?: "登录失败，请检查网络"
                _uiState.value = LoginUiState.Error(
                    if ((e as? MusicRepository.ApiException)?.code == 40) "用户名或密码错误" else msg
                )
            }
        }
    }
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val vm: LoginViewModel = appViewModel { LoginViewModel(it) }
    val uiState by vm.uiState.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.checkSaved(onLoggedIn) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("LX Music", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("yy.shangbaicn.com", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = username, onValueChange = { username = it },
            label = { Text("用户名") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("密码") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.login(username, password, onLoggedIn) },
            enabled = uiState !is LoginUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("登 录")
        }

        if (uiState is LoginUiState.Error) {
            Spacer(Modifier.height(12.dp))
            Text((uiState as LoginUiState.Error).message, color = MaterialTheme.colorScheme.error)
        }
    }
}
