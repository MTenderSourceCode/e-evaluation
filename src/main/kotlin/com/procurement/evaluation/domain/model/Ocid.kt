package com.procurement.evaluation.domain.model

import com.fasterxml.jackson.annotation.JsonValue
import com.procurement.evaluation.domain.model.enums.Stage

class Ocid private constructor(private val value: String) {

    fun getStage() = value.split("-")[4]

    override fun equals(other: Any?): Boolean {
        return if (this !== other)
            other is Ocid
                && this.value == other.value
        else
            true
    }

    override fun hashCode(): Int = value.hashCode()

    @JsonValue
    override fun toString(): String = value

    companion object {
        private val STAGES: String
            get() = Stage.allowedValues.joinToString(separator = "|", prefix = "(", postfix = ")") { it.toUpperCase() }

        private val regex = "^[a-z]{4}-[a-z0-9]{6}-[A-Z]{2}-[0-9]{13}-$STAGES-[0-9]{13}\$".toRegex()

        val pattern: String
            get() = regex.pattern

        fun tryCreateOrNull(value: String): Ocid? = if (value.matches(regex)) Ocid(value = value) else null
    }
}
