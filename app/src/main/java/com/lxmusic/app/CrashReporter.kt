package com.lxmusic.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.app.ui.theme.BrandGreen
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志：App 崩溃时把堆栈写入内部存储，
 * 下次启动弹出对话框，用户一键复制发给开发者定位。
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(context, thread, throwable)
            } catch (_: Exception) { }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        // 也把 cause 链打出来
        var cause = throwable.cause
        while (cause != null) {
            sw.append("\nCaused by: ")
            cause.printStackTrace(PrintWriter(sw))
            cause = cause.cause
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val header = buildString {
            appendLine("时间: $time")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
            appendLine("线程: ${thread.name}")
            appendLine("---")
        }
        File(context.filesDir, FILE_NAME).writeText(header + sw.toString())
    }

    fun readCrash(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f.readText().take(8000) else null
    }

    fun clearCrash(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}

@Composable
fun CrashDialogIfNeeded() {
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        crashLog = CrashReporter.readCrash(context)
    }

    val log = crashLog ?: return
    AlertDialog(
        onDismissRequest = { },
        title = { Text("检测到上次崩溃") },
        text = {
            Column {
                Text(
                    "把下面的日志复制发给开发者，即可精准定位问题：",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B94A9)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    log,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFD0D8E8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("crash", log))
                    CrashReporter.clearCrash(context)
                    crashLog = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("复制日志并关闭", color = Color(0xFF06281A))
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    CrashReporter.clearCrash(context)
                    crashLog = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3550))
            ) {
                Text("忽略", color = Color.White)
            }
        }
    )
}
