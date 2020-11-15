package com.procurement.evaluation.infrastructure.handler.v2.base

import com.procurement.evaluation.application.service.Logger
import com.procurement.evaluation.infrastructure.api.v2.ApiResponse2
import com.procurement.evaluation.infrastructure.api.v2.ApiResponseV2Generator.generateResponseOnFailure
import com.procurement.evaluation.infrastructure.api.v2.ApiSuccessResponse2
import com.procurement.evaluation.infrastructure.fail.Failure
import com.procurement.evaluation.infrastructure.handler.v2.model.CommandDescriptor
import com.procurement.evaluation.lib.functional.Validated

abstract class AbstractValidationHandlerV2(private val logger: Logger) : AbstractHandlerV2<ApiResponse2>() {

    override fun handle(descriptor: CommandDescriptor): ApiResponse2 =
        when (val result = execute(descriptor)) {
            is Validated.Ok -> {
                if (logger.isDebugEnabled)
                    logger.debug("${action.key} has been executed.")
                ApiSuccessResponse2(version = version, id = descriptor.id)
            }

            is Validated.Error ->
                generateResponseOnFailure(fail = result.reason, version = version, id = descriptor.id, logger = logger)
        }

    abstract fun execute(descriptor: CommandDescriptor): Validated<Failure>
}
