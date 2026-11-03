package com.kismet.mobile.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.window.layout.FoldingFeature

class WindowLayoutViewModel : ViewModel() {

    private val _foldingFeature = MutableLiveData<FoldingFeature?>(null)
    val foldingFeature: LiveData<FoldingFeature?> = _foldingFeature

    fun update(feature: FoldingFeature?) {
        _foldingFeature.value = feature
    }

    val isTabletop: Boolean
        get() {
            val f = _foldingFeature.value ?: return false
            return f.state == FoldingFeature.State.HALF_OPENED &&
                f.orientation == FoldingFeature.Orientation.HORIZONTAL
        }

    val isBookMode: Boolean
        get() {
            val f = _foldingFeature.value ?: return false
            return f.state == FoldingFeature.State.FLAT &&
                f.orientation == FoldingFeature.Orientation.VERTICAL
        }
}
