package com.lxmusic.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lxmusic.app.LxApp

/** 手工 DI：从 Application 里取依赖构造 ViewModel */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline creator: (LxApp) -> VM
): VM {
    val app = LocalContext.current.applicationContext as LxApp
    return viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = creator(app) as T
    })
}
