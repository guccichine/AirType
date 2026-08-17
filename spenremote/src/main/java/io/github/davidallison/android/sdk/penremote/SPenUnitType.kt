package io.github.davidallison.android.sdk.penremote

enum class SPenUnitType(val code: Int) {
    TYPE_BUTTON(0),
    TYPE_AIR_MOTION(1);

    companion object {
        fun fromCode(code: Int): SPenUnitType? =
            entries.firstOrNull { it.code == code }
    }
}
