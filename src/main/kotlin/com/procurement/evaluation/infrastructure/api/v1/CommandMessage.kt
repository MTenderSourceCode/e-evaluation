package com.procurement.evaluation.infrastructure.api.v1

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.JsonNode
import com.procurement.evaluation.domain.model.Cpid
import com.procurement.evaluation.domain.model.Ocid
import com.procurement.evaluation.domain.model.Owner
import com.procurement.evaluation.domain.model.ProcurementMethod
import com.procurement.evaluation.domain.model.Token
import com.procurement.evaluation.domain.model.award.AwardId
import com.procurement.evaluation.domain.model.enums.OperationType
import com.procurement.evaluation.domain.model.lot.LotId
import com.procurement.evaluation.domain.model.tryOwner
import com.procurement.evaluation.domain.util.extension.toLocalDateTime
import com.procurement.evaluation.exception.ErrorException
import com.procurement.evaluation.exception.ErrorType
import com.procurement.evaluation.infrastructure.api.Action
import com.procurement.evaluation.infrastructure.api.ApiVersion
import com.procurement.evaluation.infrastructure.api.command.id.CommandId
import com.procurement.evaluation.model.dto.ocds.Phase
import java.time.LocalDateTime

data class CommandMessage @JsonCreator constructor(

    val version: ApiVersion,
    val id: CommandId,
    val command: CommandTypeV1,
    val context: Context,
    val data: JsonNode
)

val CommandMessage.commandId: CommandId
    get() = this.id

val CommandMessage.action: Action
    get() = this.command

val CommandMessage.cpid: Cpid
    get() = this.context.cpid
        ?.let {
            Cpid.tryCreateOrNull(it)
                ?: throw ErrorException(
                    error = ErrorType.INVALID_ATTRIBUTE,
                    message = "Cannot parse 'cpid' attribute '${it}'."
                )
        }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'cpid' attribute in context.")

val CommandMessage.ocid: Ocid
    get() = this.context.ocid
        ?.let {
            Ocid.tryCreateOrNull(it)
                ?: throw ErrorException(
                    error = ErrorType.INVALID_ATTRIBUTE,
                    message = "Cannot parse 'ocid' attribute '${it}'."
                )
        }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'ocid' attribute in context.")

val CommandMessage.token: Token
    get() = this.context.token
        ?.let { id ->
            try {
                Token.fromString(id)
            } catch (exception: Exception) {
                throw ErrorException(error = ErrorType.INVALID_FORMAT_TOKEN)
            }
        }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'token' attribute in context.")

val CommandMessage.owner: Owner
    get() = this.context.owner
        ?.let { value ->
            value.tryOwner()
                .onFailure {
                    throw ErrorException(
                        error = ErrorType.INVALID_FORMAT_OF_ATTRIBUTE,
                        message = "Cannot parse 'owner' attribute '${value}'."
                    )
                }
        }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'owner' attribute in context.")

val CommandMessage.phase: Phase
    get() = this.context.phase
        ?.let { Phase.creator(it) }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'phase' attribute in context.")

val CommandMessage.country: String
    get() = this.context.country
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'country' attribute in context.")

val CommandMessage.pmd: ProcurementMethod
    get() = this.context.pmd
        ?.let { ProcurementMethod.fromString(it) }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'pmd' attribute in context.")

val CommandMessage.startDate: LocalDateTime
    get() = this.context.startDate
        ?.toLocalDateTime()
        ?.orThrow { it.reason }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'startDate' attribute in context.")

val CommandMessage.operationType: OperationType
    get() = this.context.operationType
        ?.let { OperationType.creator(it) }
        ?: throw ErrorException(
            error = ErrorType.CONTEXT,
            message = "Missing the 'operationType' attribute in context."
        )

val CommandMessage.lotId: LotId
    get() = this.context.id
        ?.let {
            try {
                LotId.fromString(it)
            } catch (exception: Exception) {
                throw ErrorException(error = ErrorType.INVALID_FORMAT_LOT_ID)
            }
        }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'id' attribute in context.")

val CommandMessage.awardId: AwardId
    get() = this.context.id
        ?.let {
            try {
                AwardId.fromString(it)
            } catch (exception: Exception) {
                throw ErrorException(error = ErrorType.INVALID_FORMAT_AWARD_ID)
            }
        }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'id' attribute in context.")

data class Context @JsonCreator constructor(
    val operationId: String,
    val requestId: String?,
    val cpid: String?,
    val ocid: String?,
    val stage: String?,
    val prevStage: String?,
    val processType: String?,
    val operationType: String?,
    val phase: String?,
    val owner: String?,
    val country: String?,
    val language: String?,
    val pmd: String?,
    val token: String?,
    val startDate: String?,
    val endDate: String?,
    val id: String?,
    val awardCriteria: String?
)
