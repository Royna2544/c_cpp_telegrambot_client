package com.royna.tgbotclient.ui.commands.uptime

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.royna.tgbotclient.data.BotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UptimeViewModel : ViewModel() {
    private var _uptimeValue = MutableLiveData<String>()
    private var _fetchInProgress = MutableLiveData<Boolean>()

    val uptimeValue: MutableLiveData<String>
        get() = _uptimeValue
    val fetchInProgress: MutableLiveData<Boolean>
        get() = _fetchInProgress

    fun execute() {
        viewModelScope.launch {
            val result : String
            _fetchInProgress.postValue(true)
            withContext(Dispatchers.IO) {
                BotRepository.getInstance().getBotInfo()
            }.onSuccess {
                result = "${it.uptime.hours}h ${it.uptime.minutes}m ${it.uptime.seconds}s"
                _uptimeValue.postValue(result)
            }.onFailure {
                _uptimeValue.postValue(it.message)
            }
            _fetchInProgress.postValue(false)
        }
    }
}