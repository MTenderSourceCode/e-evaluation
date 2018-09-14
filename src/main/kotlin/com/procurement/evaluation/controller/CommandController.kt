package com.procurement.evaluation.controller

import com.procurement.evaluation.model.dto.bpe.CommandMessage
import com.procurement.evaluation.model.dto.bpe.CommandType
import com.procurement.evaluation.model.dto.bpe.ResponseDto
import com.procurement.evaluation.service.CreateAwardService
import com.procurement.evaluation.service.StatusService
import com.procurement.evaluation.service.UpdateAwardService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/command")
class CommandController(private val createAwardService: CreateAwardService,
                        private val updateAwardService: UpdateAwardService,
                        private val statusService: StatusService) {

    @PostMapping
    fun command(@RequestBody commandMessage: CommandMessage): ResponseEntity<ResponseDto> {
        return ResponseEntity(execute(commandMessage), HttpStatus.OK)
    }

    fun execute(cm: CommandMessage): ResponseDto {
        return when (cm.command) {
            CommandType.CREATE_AWARDS -> createAwardService.createAwards(cm)
            CommandType.AWARD_BY_BID -> updateAwardService.awardByBid(cm)
            CommandType.AWARDS_FOR_CANS -> updateAwardService.awardsForCans(cm)
            CommandType.SET_FINAL_STATUSES -> statusService.setFinalStatuses(cm)
            CommandType.PREPARE_CANCELLATION -> statusService.prepareCancellation(cm)
            CommandType.AWARDS_CANCELLATION -> statusService.awardsCancellation(cm)
        }
    }
}


