package com.procurement.evaluation.service;

import com.procurement.evaluation.exception.ErrorException;
import com.procurement.evaluation.exception.ErrorType;
import com.procurement.evaluation.model.dto.AwardsResponseDto;
import com.procurement.evaluation.model.dto.UpdateAwardRequestDto;
import com.procurement.evaluation.model.dto.UpdateAwardResponseDto;
import com.procurement.evaluation.model.dto.bpe.ResponseDto;
import com.procurement.evaluation.model.dto.ocds.Award;
import com.procurement.evaluation.model.dto.ocds.Lot;
import com.procurement.evaluation.model.dto.ocds.Period;
import com.procurement.evaluation.model.dto.ocds.Status;
import com.procurement.evaluation.model.entity.AwardEntity;
import com.procurement.evaluation.repository.AwardRepository;
import com.procurement.evaluation.utils.DateUtil;
import com.procurement.evaluation.utils.JsonUtil;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import static java.util.stream.Collectors.groupingBy;

@Service
public class AwardServiceImpl implements AwardService {

    private final AwardRepository awardRepository;
    private final JsonUtil jsonUtil;
    private final DateUtil dateUtil;
    private final PeriodService periodService;

    public AwardServiceImpl(final AwardRepository awardRepository,
                            final JsonUtil jsonUtil,
                            final DateUtil dateUtil,
                            final PeriodService periodService) {
        this.awardRepository = awardRepository;
        this.jsonUtil = jsonUtil;
        this.dateUtil = dateUtil;
        this.periodService = periodService;
    }

    @Override
    public ResponseDto updateAward(final String cpId,
                                   final String stage,
                                   final String token,
                                   final String owner,
                                   final UpdateAwardRequestDto dataDto) {
        final Award awardDto = dataDto.getAward();
        switch (awardDto.getStatusDetails()) {
            case ACTIVE:
                final AwardEntity entity = Optional.ofNullable(awardRepository.getByCpIdAndStageAndToken(cpId, stage, UUID.fromString(token)))
                        .orElseThrow(() -> new ErrorException(ErrorType.DATA_NOT_FOUND));
                if (!entity.getOwner().equals(owner)) throw new ErrorException(ErrorType.INVALID_OWNER);
                final Award award = jsonUtil.toObject(Award.class, entity.getJsonData());
                if (Objects.nonNull(awardDto.getDescription())) award.setDescription(awardDto.getDescription());
                if (Objects.nonNull(awardDto.getStatusDetails())) award.setStatusDetails(awardDto.getStatusDetails());
                if (Objects.nonNull(awardDto.getDocuments())) award.setDocuments(awardDto.getDocuments());
                award.setDate(dateUtil.localNowUTC());
                entity.setJsonData(jsonUtil.toJson(award));
                awardRepository.save(entity);
                return getResponseDtoForAward(award);
            case UNSUCCESSFUL:
                final List<AwardEntity> entities = Optional.ofNullable(awardRepository.getAllByCpidAndStage(cpId, stage))
                        .orElseThrow(() -> new ErrorException(ErrorType.DATA_NOT_FOUND));

                Map<Award, AwardEntity> awardsFromEntities = getMapAwardsFromEntities(entities);
                final List<Award> updatedAwards = new ArrayList<>();
                // UNSUCCESSFUL AWARD
                final Award updatableAward = Optional.of(
                        awardsFromEntities.keySet().stream()
                                .filter(a -> a.getId().equals(awardDto.getId()))
                                .findFirst()
                                .get())
                        .orElseThrow(() -> new ErrorException(ErrorType.DATA_NOT_FOUND));
                AwardEntity updatedAwardEntity = awardsFromEntities.get(updatableAward);
                if (awardDto.getDescription() != null) updatableAward.setDescription(awardDto.getDescription());
                if (awardDto.getStatusDetails() != null) updatableAward.setStatusDetails(awardDto.getStatusDetails());
                if (awardDto.getDocuments() != null) updatableAward.setDocuments(awardDto.getDocuments());
                updatedAwardEntity.setJsonData(jsonUtil.toJson(updatableAward));
                awardRepository.save(updatedAwardEntity);
                updatedAwards.add(updatableAward);

                // NEXT AWARD BY LOT
                Award nextAwardByLot = awardsFromEntities.keySet().stream().filter(a -> a.getRelatedLots().equals(updatableAward
                        .getRelatedLots())).sorted(new SortedByValue()).findFirst().get();
                if (nextAwardByLot != null) {
                    AwardEntity nextAwardByLotEntity = awardsFromEntities.get(nextAwardByLot);
                    nextAwardByLot.setStatusDetails(Status.CONSIDERATION);
                    nextAwardByLotEntity.setJsonData(jsonUtil.toJson(updatableAward));
                    awardRepository.save(nextAwardByLotEntity);
                    updatedAwards.add(nextAwardByLot);
                }
                return getResponseDtoForAwards(updatedAwards);

            default:
                throw new ErrorException(ErrorType.INVALID_STATUS_DETAILS);
        }

    }

    @Override
    public ResponseDto getAwards(final String cpId,
                                 final String stage,
                                 final String country,
                                 final String pmd) {
        final List<AwardEntity> awardEntities = Optional.ofNullable(awardRepository.getAllByCpidAndStage(cpId, stage))
                .orElseThrow(() -> new ErrorException(ErrorType.DATA_NOT_FOUND));
        final List<Award> activeAwards = getActiveAwardsFromEntities(awardEntities);
        return new ResponseDto<>(true, null, new AwardsResponseDto(activeAwards, null, null));
    }

    @Override
    public ResponseDto endAwardPeriod(final String cpId,
                                      final String stage,
                                      final String country,
                                      final String pmd,
                                      final LocalDateTime endPeriod) {
        final Period awardPeriod = periodService.saveEndOfPeriod(cpId, stage, endPeriod);
        final List<AwardEntity> awardEntities = awardRepository.getAllByCpidAndStage(cpId, stage);
        if (awardEntities.isEmpty()) throw new ErrorException(ErrorType.DATA_NOT_FOUND);
        final List<Award> awards = getAwardsFromEntities(awardEntities);
        setAwardsStatusFromStatusDetails(awards, endPeriod);
        final List<Lot> unsuccessfulLots = getUnsuccessfulLotsFromAwards(awards, country, pmd);
        return new ResponseDto<>(true, null, new AwardsResponseDto(awards, awardPeriod, unsuccessfulLots));
    }

    private List<Lot> getUnsuccessfulLotsFromAwards(final List<Award> awards,
                                                    final String country,
                                                    final String pmd) {
        final List<String> unsuccessfulRelatedLotsFromAward = getUnsuccessfulRelatedLotsIdFromAwards(awards);
        final Map<String, Long> uniqueLots = getUniqueLots(unsuccessfulRelatedLotsFromAward);
        final List<String> unsuccessfulLots = getUnsuccessfulLots(uniqueLots);
        return unsuccessfulLots.stream().map(Lot::new).collect(Collectors.toList());
    }

    private List<String> getUnsuccessfulRelatedLotsIdFromAwards(final List<Award> awards) {
        return awards.stream()
                .filter(award ->
                        (award.getStatus().equals(Status.UNSUCCESSFUL) && award.getStatusDetails().equals(Status.EMPTY)))
                .flatMap(award -> award.getRelatedLots().stream())
                .collect(Collectors.toList());
    }

    private Map<String, Long> getUniqueLots(final List<String> lots) {
        return lots.stream()
                .collect(groupingBy(Function.identity(), Collectors.counting()));
    }

    private List<String> getUnsuccessfulLots(final Map<String, Long> uniqueLots) {
        return uniqueLots.entrySet()
                .stream()
                .filter(map -> map.getValue() > 0)
                .map(map -> map.getKey())
                .collect(Collectors.toList());
    }

    private List<Award> getAwardsFromEntities(final List<AwardEntity> awardEntities) {
        return awardEntities.stream()
                .map(e -> jsonUtil.toObject(Award.class, e.getJsonData()))
                .collect(Collectors.toList());
    }

    private List<Award> getActiveAwardsFromEntities(final List<AwardEntity> awardEntities) {
        return awardEntities.stream()
                .map(e -> jsonUtil.toObject(Award.class, e.getJsonData()))
                .filter(award -> (award.getStatus().equals(Status.ACTIVE) && award.getStatusDetails().equals(Status.EMPTY)))
                .collect(Collectors.toList());
    }

    private void setAwardsStatusFromStatusDetails(final List<Award> awards, final LocalDateTime endPeriod) {
        awards.forEach(a -> {
            if (a.getStatusDetails() != Status.EMPTY) {
                a.setDate(endPeriod);
                a.setStatus(a.getStatusDetails());
                a.setStatusDetails(Status.EMPTY);
            }
        });
    }

    private Map<Award, AwardEntity> getMapAwardsFromEntities(final List<AwardEntity> awardEntities) {
        final Map<Award, AwardEntity> awardsFromEntities = new HashMap<>();
        awardEntities.forEach(e -> {
            final Award award = jsonUtil.toObject(Award.class, e.getJsonData());
            awardsFromEntities.put(award, e);
        });
        return awardsFromEntities;
    }

    private ResponseDto getResponseDtoForAward(final Award award) {
        return new ResponseDto<>(true, null,
                new UpdateAwardResponseDto(
                        Collections.singletonList(award),
                        award.getRelatedBid(), award
                        .getRelatedLots().get(0))
        );
    }

    private ResponseDto getResponseDtoForAwards(final List<Award> awards) {
        return new ResponseDto<>(true, null,
                new UpdateAwardResponseDto(
                        awards, awards.get(0).getRelatedBid(),
                        awards.get(0).getRelatedLots().get(0)));
    }

    private class SortedByValue implements Comparator<Award> {

        public int compare(final Award obj1, final Award obj2) {
            final double val1 = obj1.getValue().getAmount();
            final double val2 = obj2.getValue().getAmount();
            if (val1 > val2) {
                return 1;
            } else if (val1 < val2) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}