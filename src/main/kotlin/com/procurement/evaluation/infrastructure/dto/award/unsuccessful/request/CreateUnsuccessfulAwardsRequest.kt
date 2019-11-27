package com.procurement.evaluation.infrastructure.dto.award.unsuccessful.request


import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class CreateUnsuccessfulAwardsRequest(
    @field:JsonProperty("lots") @param:JsonProperty("lots") val lots: List<Lot>
) {
    data class Lot(
        @field:JsonProperty("id") @param:JsonProperty("id") val id: UUID
    )
}