package com.joat917.qrdecoderlite.model

import android.graphics.PointF
import android.net.Uri

data class AppUiState(
    val activeSheet: Sheet? = null
)

sealed interface Sheet {
    data class Result(val imageUri: Uri) : Sheet
}

data class DecodeUiState(
    val imageUri: Uri,
    val barcodes: List<DecodedBarcode>? = null,
    val decodeError: String? = null
)

data class DecodedBarcode(
    val content: String,
    val format: String,
    val cornerPoints: List<PointF>
)

sealed interface DecodeResult {
    data class Success(val barcodes: List<DecodedBarcode>) : DecodeResult
    data class Failure(val reason: String) : DecodeResult
}
