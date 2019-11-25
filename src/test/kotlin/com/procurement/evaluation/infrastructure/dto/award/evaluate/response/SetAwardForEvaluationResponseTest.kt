package com.procurement.evaluation.infrastructure.dto.award.evaluate.response

import com.procurement.evaluation.infrastructure.AbstractDTOTestBase
import org.junit.jupiter.api.Test

class SetAwardForEvaluationResponseTest :
    AbstractDTOTestBase<SetAwardForEvaluationResponse>(SetAwardForEvaluationResponse::class.java) {

    @Test
    fun fully() {
        testBindingAndMapping("json/infrastructure/dto/award/evaluate/response/response_set_award_for_evaluation_full.json")
    }
}
