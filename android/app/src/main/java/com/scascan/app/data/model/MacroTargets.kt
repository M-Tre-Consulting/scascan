package com.scascan.app.data.model

data class MacroTargets(val proteinG: Int, val carbsG: Int, val fatG: Int) {
    companion object {
        val EMPTY = MacroTargets(0, 0, 0)
    }
}
