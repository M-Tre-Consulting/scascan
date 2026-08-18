package com.mtreconsulting.scascan.ui.camera

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Analysis has moved to AnalysisManager (background singleton).
// This ViewModel is retained for future camera-specific state if needed.
@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel()
