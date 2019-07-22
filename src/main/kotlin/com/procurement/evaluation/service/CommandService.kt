package com.procurement.evaluation.service

import com.procurement.evaluation.application.service.award.AwardService
import com.procurement.evaluation.application.service.award.CreateAwardContext
import com.procurement.evaluation.application.service.award.CreateAwardData
import com.procurement.evaluation.dao.HistoryDao
import com.procurement.evaluation.exception.ErrorException
import com.procurement.evaluation.exception.ErrorType
import com.procurement.evaluation.infrastructure.dto.award.create.request.CreateAwardRequest
import com.procurement.evaluation.infrastructure.dto.award.create.response.CreateAwardResponse
import com.procurement.evaluation.infrastructure.tools.toLocalDateTime
import com.procurement.evaluation.model.dto.bpe.CommandMessage
import com.procurement.evaluation.model.dto.bpe.CommandType
import com.procurement.evaluation.model.dto.bpe.ResponseDto
import com.procurement.evaluation.utils.toJson
import com.procurement.evaluation.utils.toObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class CommandService(
    private val historyDao: HistoryDao,
    private val createAwardService: CreateAwardService,
    private val updateAwardService: UpdateAwardService,
    private val statusService: StatusService,
    private val awardService: AwardService
) {

    companion object {
        private val log = LoggerFactory.getLogger(CommandService::class.java)
    }

    fun execute(cm: CommandMessage): ResponseDto {
        var historyEntity = historyDao.getHistory(cm.id, cm.command.value())
        if (historyEntity != null) {
            return toObject(ResponseDto::class.java, historyEntity.jsonData)
        }
        val response = when (cm.command) {
            CommandType.CREATE_AWARD -> {
                val context = CreateAwardContext(
                    cpid = getCPID(cm),
                    stage = getStage(cm),
                    owner = getOwner(cm),
                    startDate = getStartDate(cm),
                    lotId = getLotId(cm)
                )

                val request = toObject(CreateAwardRequest::class.java, cm.data)
                val data = CreateAwardData(
                    mdm = request.mdm.let { mdm ->
                        CreateAwardData.Mdm(
                            scales = mdm.scales.toList(),
                            schemes = mdm.schemes.toList()
                        )
                    },
                    award = request.award.let { award ->
                        CreateAwardData.Award(
                            description = award.description,
                            value = award.value.let { value ->
                                CreateAwardData.Award.Value(
                                    amount = value.amount,
                                    currency = value.currency
                                )
                            },
                            suppliers = award.suppliers.map { supplier ->
                                CreateAwardData.Award.Supplier(
                                    name = supplier.name,
                                    identifier = supplier.identifier.let { identifier ->
                                        CreateAwardData.Award.Supplier.Identifier(
                                            scheme = identifier.scheme,
                                            id = identifier.id,
                                            legalName = identifier.legalName,
                                            uri = identifier.uri
                                        )
                                    },
                                    additionalIdentifiers = supplier.additionalIdentifiers?.map { additionalIdentifier ->
                                        CreateAwardData.Award.Supplier.AdditionalIdentifier(
                                            scheme = additionalIdentifier.scheme,
                                            id = additionalIdentifier.id,
                                            legalName = additionalIdentifier.legalName,
                                            uri = additionalIdentifier.uri
                                        )
                                    },
                                    address = supplier.address.let { address ->
                                        CreateAwardData.Award.Supplier.Address(
                                            streetAddress = address.streetAddress,
                                            postalCode = address.postalCode,
                                            addressDetails = address.addressDetails.let { detail ->
                                                CreateAwardData.Award.Supplier.Address.AddressDetails(
                                                    country = detail.country.let { country ->
                                                        CreateAwardData.Award.Supplier.Address.AddressDetails.Country(
                                                            scheme = country.scheme,
                                                            id = country.id,
                                                            description = country.description,
                                                            uri = country.uri
                                                        )
                                                    },
                                                    region = detail.region.let { region ->
                                                        CreateAwardData.Award.Supplier.Address.AddressDetails.Region(
                                                            scheme = region.scheme,
                                                            id = region.id,
                                                            description = region.description,
                                                            uri = region.uri
                                                        )
                                                    },
                                                    locality = detail.region.let { locality ->
                                                        CreateAwardData.Award.Supplier.Address.AddressDetails.Locality(
                                                            scheme = locality.scheme,
                                                            id = locality.id,
                                                            description = locality.description,
                                                            uri = locality.uri
                                                        )
                                                    }
                                                )
                                            }
                                        )
                                    },
                                    contactPoint = supplier.contactPoint.let { contactPoint ->
                                        CreateAwardData.Award.Supplier.ContactPoint(
                                            name = contactPoint.name,
                                            email = contactPoint.email,
                                            telephone = contactPoint.telephone,
                                            faxNumber = contactPoint.faxNumber,
                                            url = contactPoint.url
                                        )
                                    },
                                    details = supplier.details.let { details ->
                                        CreateAwardData.Award.Supplier.Details(
                                            scale = details.scale
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
                val result = awardService.create(context, data)

                if (log.isDebugEnabled)
                    log.debug("Award was created. Result: ${toJson(result)}")

                val dataResponse = CreateAwardResponse(
                    token = result.token,
                    awardPeriod = result.awardPeriod?.let { awardPeriod ->
                        CreateAwardResponse.AwardPeriod(
                            startDate = awardPeriod.startDate
                        )
                    },
                    lotAwarded = result.lotAwarded,
                    award = result.award.let { award ->
                        CreateAwardResponse.Award(
                            id = award.id,
                            date = award.date,
                            status = award.status,
                            statusDetails = award.statusDetails,
                            relatedLots = award.relatedLots.toList(),
                            description = award.description,
                            value = award.value.let { value ->
                                CreateAwardResponse.Award.Value(
                                    amount = value.amount,
                                    currency = value.currency
                                )
                            },
                            suppliers = award.suppliers.map { supplier ->
                                CreateAwardResponse.Award.Supplier(
                                    id = supplier.id,
                                    name = supplier.name,
                                    identifier = supplier.identifier.let { identifier ->
                                        CreateAwardResponse.Award.Supplier.Identifier(
                                            scheme = identifier.scheme,
                                            id = identifier.id,
                                            legalName = identifier.legalName,
                                            uri = identifier.uri
                                        )
                                    },
                                    additionalIdentifiers = supplier.additionalIdentifiers?.map { additionalIdentifier ->
                                        CreateAwardResponse.Award.Supplier.AdditionalIdentifier(
                                            scheme = additionalIdentifier.scheme,
                                            id = additionalIdentifier.id,
                                            legalName = additionalIdentifier.legalName,
                                            uri = additionalIdentifier.uri
                                        )
                                    },
                                    address = supplier.address.let { address ->
                                        CreateAwardResponse.Award.Supplier.Address(
                                            streetAddress = address.streetAddress,
                                            postalCode = address.postalCode,
                                            addressDetails = address.addressDetails.let { detail ->
                                                CreateAwardResponse.Award.Supplier.Address.AddressDetails(
                                                    country = detail.country.let { country ->
                                                        CreateAwardResponse.Award.Supplier.Address.AddressDetails.Country(
                                                            scheme = country.scheme,
                                                            id = country.id,
                                                            description = country.description,
                                                            uri = country.uri
                                                        )
                                                    },
                                                    region = detail.region.let { region ->
                                                        CreateAwardResponse.Award.Supplier.Address.AddressDetails.Region(
                                                            scheme = region.scheme,
                                                            id = region.id,
                                                            description = region.description,
                                                            uri = region.uri
                                                        )
                                                    },
                                                    locality = detail.region.let { locality ->
                                                        CreateAwardResponse.Award.Supplier.Address.AddressDetails.Locality(
                                                            scheme = locality.scheme,
                                                            id = locality.id,
                                                            description = locality.description,
                                                            uri = locality.uri
                                                        )
                                                    }
                                                )
                                            }
                                        )
                                    },
                                    contactPoint = supplier.contactPoint.let { contactPoint ->
                                        CreateAwardResponse.Award.Supplier.ContactPoint(
                                            name = contactPoint.name,
                                            email = contactPoint.email,
                                            telephone = contactPoint.telephone,
                                            faxNumber = contactPoint.faxNumber,
                                            url = contactPoint.url
                                        )
                                    },
                                    details = supplier.details.let { details ->
                                        CreateAwardResponse.Award.Supplier.Details(
                                            scale = details.scale
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
                if (log.isDebugEnabled)
                    log.debug("Award was created. Response: ${toJson(dataResponse)}")
                ResponseDto(data = dataResponse)
            }
            CommandType.CREATE_AWARDS -> createAwardService.createAwards(cm)
            CommandType.CREATE_AWARDS_AUCTION -> createAwardService.createAwardsAuction(cm)
            CommandType.CREATE_AWARDS_AUCTION_END -> createAwardService.createAwardsAuctionEnd(cm)
            CommandType.CREATE_AWARDS_BY_LOT_AUCTION -> createAwardService.createAwardsByLotsAuction(cm)
            CommandType.AWARD_BY_BID -> updateAwardService.awardByBid(cm)
            CommandType.SET_FINAL_STATUSES -> statusService.setFinalStatuses(cm)
            CommandType.PREPARE_CANCELLATION -> statusService.prepareCancellation(cm)
            CommandType.AWARDS_CANCELLATION -> statusService.awardsCancellation(cm)
            CommandType.CHECK_AWARD_VALUE -> statusService.checkAwardValue(cm)
            CommandType.END_AWARD_PERIOD -> statusService.endAwardPeriod(cm)
            CommandType.SET_INITIAL_AWARDS_STATUS -> updateAwardService.setInitialAwardsStatuses(cm)
            CommandType.GET_AWARD_FOR_CAN -> statusService.getAwardForCan(cm)
            CommandType.GET_AWARDS_FOR_AC -> statusService.getAwardsForAc(cm)
            CommandType.GET_LOT_FOR_CHECK -> statusService.getLotForCheck(cm)
            CommandType.GET_AWARD_ID_FOR_CHECK -> statusService.getAwardIdForCheck(cm)
        }
        historyEntity = historyDao.saveHistory(cm.id, cm.command.value(), response)
        return toObject(ResponseDto::class.java, historyEntity.jsonData)
    }

    private fun getCPID(cm: CommandMessage): String = cm.context.cpid
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'cpid' attribute in context.")

    private fun getStage(cm: CommandMessage): String = cm.context.stage
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'stage' attribute in context.")

    private fun getStartDate(cm: CommandMessage): LocalDateTime = cm.context.startDate?.toLocalDateTime()
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'startDate' attribute in context.")

    private fun getOwner(cm: CommandMessage): String = cm.context.owner
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'owner' attribute in context.")

    private fun getLotId(cm: CommandMessage): UUID = cm.context.id
        ?.let { id ->
            try {
                UUID.fromString(id)
            } catch (exception: Exception) {
                throw ErrorException(error = ErrorType.INVALID_FORMAT_LOT_ID)
            }
        }
        ?: throw ErrorException(error = ErrorType.CONTEXT, message = "Missing the 'id' attribute in context.")
}