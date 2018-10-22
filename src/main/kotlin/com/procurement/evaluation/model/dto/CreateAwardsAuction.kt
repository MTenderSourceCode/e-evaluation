package com.procurement.evaluation.model.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.procurement.evaluation.model.dto.ocds.Bid
import com.procurement.evaluation.model.dto.ocds.Lot

data class CreateAwardsAuctionRq @JsonCreator constructor(

        val tender: TenderAuction,

        val bidsData: Set<BidsData>
)

data class TenderAuction @JsonCreator constructor(

        val id: String?,

        val title: String?,

        val description: String?,

        val awardCriteria: String,

        val lots: List<Lot>
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BidsData @JsonCreator constructor(

        var owner: String?,

        var bids: Set<Bid>
)
