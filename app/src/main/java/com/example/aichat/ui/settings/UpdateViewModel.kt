package com.example.aichat.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.data.remote.dto.GitHubRelease
import com.example.aichat.data.repository.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository
) : ViewModel() {
    companion object {
        private const val TAG = "UpdateViewModel"
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    val downloadProgress = updateRepository.downloadProgress
    val isDownloading = updateRepository.isDownloading

    sealed class UpdateState {
        data object Idle : UpdateState()
        data object Checking : UpdateState()
        data class UpdateAvailable(val release: GitHubRelease) : UpdateState()
        data object UpToDate : UpdateState()
        data class Error(val message: String) : UpdateState()
        data class Downloaded(val apkFile: File) : UpdateState()
    }

    /**
     * 检查更新（静默模式：仅在有新版本时通知，否则不弹窗）。
     */
    fun checkForUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            try {
                val release = updateRepository.checkForUpdate()
                _updateState.value = if (release != null) {
                    UpdateState.UpdateAvailable(release)
                } else {
                    if (silent) UpdateState.Idle else UpdateState.UpToDate
                }
            } catch (e: Exception) {
                Log.w(TAG, "检查更新失败: ${e.message}")
                _updateState.value = if (silent) UpdateState.Idle else UpdateState.Error(e.message ?: "检查失败")
            }
        }
    }

    /**
     * 下载并安装 APK。
     */
    fun downloadAndInstall(release: GitHubRelease) {
        viewModelScope.launch {
            try {
                val apkFile = updateRepository.downloadApk(release)
                if (apkFile != null) {
                    _updateState.value = UpdateState.Downloaded(apkFile)
                    updateRepository.installApk(apkFile)
                } else {
                    _updateState.value = UpdateState.Error("下载失败")
                }
            } catch (e: Exception) {
                Log.w(TAG, "下载更新失败: ${e.message}")
                _updateState.value = UpdateState.Error(e.message ?: "下载失败")
            }
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }
}
