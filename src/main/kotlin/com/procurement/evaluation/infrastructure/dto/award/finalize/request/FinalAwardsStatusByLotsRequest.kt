package com.procurement.evaluation.infrastructure.dto.award.finalize.request

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class FinalAwardsStatusByLotsRequest(
    @field:JsonProperty("lots") @param:JsonProperty("lots") val lots: List<Lot>
) {

    data class Lot(
        @field:JsonProperty("id") @param:JsonProperty("id") val id: UUID
    )
}
