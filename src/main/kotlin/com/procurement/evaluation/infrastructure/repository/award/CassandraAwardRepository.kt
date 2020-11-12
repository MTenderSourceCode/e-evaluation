package com.procurement.evaluation.infrastructure.repository.award

import com.datastax.driver.core.BatchStatement
import com.datastax.driver.core.BoundStatement
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Row
import com.datastax.driver.core.Session
import com.datastax.driver.core.Statement
import com.procurement.evaluation.application.exception.ReadEntityException
import com.procurement.evaluation.application.exception.SaveEntityException
import com.procurement.evaluation.application.repository.award.AwardRepository
import com.procurement.evaluation.application.repository.award.model.AwardEntity
import com.procurement.evaluation.domain.functional.Result
import com.procurement.evaluation.domain.functional.Result.Companion.failure
import com.procurement.evaluation.domain.functional.asSuccess
import com.procurement.evaluation.domain.model.Cpid
import com.procurement.evaluation.domain.model.Ocid
import com.procurement.evaluation.domain.model.Owner
import com.procurement.evaluation.domain.model.Token
import com.procurement.evaluation.infrastructure.extension.cassandra.tryExecute
import com.procurement.evaluation.infrastructure.fail.Fail
import com.procurement.evaluation.infrastructure.repository.Database
import com.procurement.evaluation.model.dto.ocds.AwardStatus
import com.procurement.evaluation.model.dto.ocds.AwardStatusDetails
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class CassandraAwardRepository(private val session: Session) : AwardRepository {
    companion object {

        private const val FIND_BY_CPID_CQL = """
               SELECT ${Database.Awards.CPID},
                      ${Database.Awards.OCID},
                      ${Database.Awards.TOKEN_ENTITY},
                      ${Database.Awards.OWNER},
                      ${Database.Awards.STATUS},
                      ${Database.Awards.STATUS_DETAILS},
                      ${Database.Awards.JSON_DATA}
                 FROM ${Database.KEYSPACE}.${Database.Awards.TABLE_NAME}
                WHERE ${Database.Awards.CPID}=?
            """

        private const val FIND_BY_CPID_AND_OCID_CQL = """
               SELECT ${Database.Awards.CPID},
                      ${Database.Awards.OCID},
                      ${Database.Awards.TOKEN_ENTITY},
                      ${Database.Awards.OWNER},
                      ${Database.Awards.STATUS},
                      ${Database.Awards.STATUS_DETAILS},
                      ${Database.Awards.JSON_DATA}
                 FROM ${Database.KEYSPACE}.${Database.Awards.TABLE_NAME}
                WHERE ${Database.Awards.CPID}=?
                  AND ${Database.Awards.OCID}=?
            """

        private const val FIND_BY_CPID_AND_OCID_AND_TOKEN_CQL = """
               SELECT ${Database.Awards.CPID},
                      ${Database.Awards.OCID},
                      ${Database.Awards.TOKEN_ENTITY},
                      ${Database.Awards.OWNER},
                      ${Database.Awards.STATUS},
                      ${Database.Awards.STATUS_DETAILS},
                      ${Database.Awards.JSON_DATA}
                 FROM ${Database.KEYSPACE}.${Database.Awards.TABLE_NAME}
                WHERE ${Database.Awards.CPID}=?
                  AND ${Database.Awards.OCID}=?
                  AND ${Database.Awards.TOKEN_ENTITY}=?
            """

        private const val SAVE_NEW_AWARD_CQL = """
               INSERT INTO ${Database.KEYSPACE}.${Database.Awards.TABLE_NAME}(
                      ${Database.Awards.CPID},
                      ${Database.Awards.OCID},
                      ${Database.Awards.TOKEN_ENTITY},
                      ${Database.Awards.OWNER},
                      ${Database.Awards.STATUS},
                      ${Database.Awards.STATUS_DETAILS},
                      ${Database.Awards.JSON_DATA}
               )
               VALUES(?,?,?,?,?,?,?)
               IF NOT EXISTS
            """

        private const val UPDATE_AWARD_STATUSES_CQL = """
               UPDATE ${Database.KEYSPACE}.${Database.Awards.TABLE_NAME}
                  SET ${Database.Awards.STATUS}=?,
                      ${Database.Awards.STATUS_DETAILS}=?,
                      ${Database.Awards.JSON_DATA}=?
                WHERE ${Database.Awards.CPID}=?
                  AND ${Database.Awards.OCID}=?
                  AND ${Database.Awards.TOKEN_ENTITY}=?
               IF EXISTS
            """
    }

    private val preparedFindByCpidCQL = session.prepare(FIND_BY_CPID_CQL)
    private val preparedFindByCpidAndOcidCQL = session.prepare(FIND_BY_CPID_AND_OCID_CQL)
    private val preparedFindByCpidAndOcidAndTokenCQL = session.prepare(FIND_BY_CPID_AND_OCID_AND_TOKEN_CQL)
    private val preparedSaveNewAwardCQL = session.prepare(SAVE_NEW_AWARD_CQL)
    private val preparedUpdatedAwardStatusesCQL = session.prepare(UPDATE_AWARD_STATUSES_CQL)

    override fun findBy(cpid: Cpid): List<AwardEntity> {
        val query = preparedFindByCpidCQL.bind()
            .apply {
                setString(Database.Awards.CPID, cpid.toString())
            }

        val resultSet = load(query)
        return resultSet.map { convertToAwardEntity(it) }
    }

    protected fun load(statement: BoundStatement): ResultSet = try {
        session.execute(statement)
    } catch (exception: Exception) {
        throw ReadEntityException(message = "Error read Award(s) from the database.", cause = exception)
    }

    private fun convertToAwardEntity(row: Row): AwardEntity = AwardEntity(
        cpid = Cpid.tryCreateOrNull(row.getString(Database.Awards.CPID))!!,
        token = UUID.fromString(row.getString(Database.Awards.TOKEN_ENTITY)),
        ocid = Ocid.tryCreateOrNull(row.getString(Database.Awards.OCID))!!,
        owner = Owner.fromString(row.getString(Database.Awards.OWNER)),
        status = AwardStatus.creator(row.getString(Database.Awards.STATUS)),
        statusDetails = AwardStatusDetails.creator(row.getString(Database.Awards.STATUS_DETAILS)),
        jsonData = row.getString(Database.Awards.JSON_DATA)
    )

    override fun findBy(cpid: Cpid, ocid: Ocid, token: Token): AwardEntity? {
        val query = preparedFindByCpidAndOcidAndTokenCQL.bind()
            .apply {
                setString(Database.Awards.CPID, cpid.toString())
                setString(Database.Awards.OCID, ocid.toString())
                setString(Database.Awards.TOKEN_ENTITY, token.toString())
            }

        val resultSet = load(query)
        return resultSet.one()?.let { convertToAwardEntity(it) }
    }

    override fun saveNew(cpid: Cpid, award: AwardEntity) {
        val statement = statementForAwardSave(cpid, award)

        val result = saveNew(statement)
        if (!result.wasApplied())
            throw SaveEntityException(message = "An error occurred when writing a record(s) of the award by cpid '$cpid' and ocid '${award.ocid}' to the database. Record is already.")
    }

    private fun statementForAwardSave(
        cpid: Cpid,
        award: AwardEntity
    ): BoundStatement = preparedSaveNewAwardCQL.bind()
        .apply {
            setString(Database.Awards.CPID, cpid.toString())
            setString(Database.Awards.OCID, award.ocid.toString())
            setString(Database.Awards.TOKEN_ENTITY, award.token.toString())
            setString(Database.Awards.OWNER, award.owner.toString())
            setString(Database.Awards.STATUS, award.status.toString())
            setString(Database.Awards.STATUS_DETAILS, award.statusDetails.toString())
            setString(Database.Awards.JSON_DATA, award.jsonData)
        }

    private fun saveNew(statement: BoundStatement): ResultSet = try {
        session.execute(statement)
    } catch (exception: Exception) {
        throw SaveEntityException(message = "Error writing new award to database.", cause = exception)
    }

    override fun saveNew(cpid: Cpid, awards: Collection<AwardEntity>) {
        val statements = BatchStatement().apply {
            for (award in awards) {
                add(statementForAwardSave(cpid = cpid, award = award))
            }
        }
        val result = saveNewAwards(statements)
        if (!result.wasApplied())
            throw SaveEntityException(message = "An error occurred when writing a record(s) of the award(s) by cpid '$cpid' to the database.")
    }

    private fun saveNewAwards(statement: BatchStatement): ResultSet = try {
        session.execute(statement)
    } catch (exception: Exception) {
        throw SaveEntityException(message = "Error writing new award(s) to database.", cause = exception)
    }

    override fun update(cpid: Cpid, updatedAward: AwardEntity) {
        val statement = statementForUpdateAward(cpid = cpid, updatedAward = updatedAward)
        val result = executeUpdating(statement)
        if (!result.wasApplied())
            throw SaveEntityException(message = "An error occurred when writing a record(s) of the award by cpid '$cpid' and ocid '${updatedAward.ocid}' and token to the database. Record is already.")
    }

    override fun update(cpid: Cpid, updatedAwards: Collection<AwardEntity>) {
        val statements = BatchStatement().apply {
            for (updatedAward in updatedAwards) {
                add(statementForUpdateAward(cpid = cpid, updatedAward = updatedAward))
            }
        }
        val result = executeUpdating(statements)
        if (!result.wasApplied())
            throw SaveEntityException(message = "An error occurred when writing a record(s) of the awards by cpid '$cpid' to the database. Record(s) is not exists.")
    }

    override fun findBy(cpid: Cpid, ocid: Ocid): Result<List<AwardEntity>, Fail.Incident.Database> {
        val query = preparedFindByCpidAndOcidCQL.bind()
            .apply {
                setString(Database.Awards.CPID, cpid.toString())
                setString(Database.Awards.OCID, ocid.toString())
            }

        val resultSet = query.tryExecute(session)
            .doReturn { error -> return failure(error) }
        return resultSet.map { convertToAwardEntity(it) }.asSuccess()
    }

    override fun trySave(cpid: Cpid, awards: Collection<AwardEntity>): Result<Unit, Fail.Incident> {
        val statements = BatchStatement()
            .apply {
                for (award in awards) {
                    add(statementForAwardSave(cpid = cpid, award = award))
                }
            }
        val result = statements.tryExecute(session = session)
            .orForwardFail { error -> return error }

        if (!result.wasApplied())
            return failure(
                Fail.Incident.Database.RecordIsNotExist(description = "An error occurred when writing a record(s) of the awards by cpid '$cpid' to the database. Record(s) is not exists.")
            )

        return Unit.asSuccess()
    }

    override fun tryUpdate(cpid: Cpid, updatedAward: AwardEntity): Result<Boolean, Fail.Incident> {
        val statement = statementForUpdateAward(cpid = cpid, updatedAward = updatedAward)
        val result = statement.tryExecute(session = session)
            .orForwardFail { error -> return error }
        return result.wasApplied().asSuccess()
    }

    private fun statementForUpdateAward(cpid: Cpid, updatedAward: AwardEntity): BoundStatement =
        preparedUpdatedAwardStatusesCQL.bind()
            .apply {
                setString(Database.Awards.CPID, cpid.toString())
                setString(Database.Awards.OCID, updatedAward.ocid.toString())
                setString(Database.Awards.TOKEN_ENTITY, updatedAward.token.toString())
                setString(Database.Awards.STATUS, updatedAward.status.toString())
                setString(Database.Awards.STATUS_DETAILS, updatedAward.statusDetails.toString())
                setString(Database.Awards.JSON_DATA, updatedAward.jsonData)
            }

    private fun executeUpdating(statement: Statement): ResultSet = try {
        session.execute(statement)
    } catch (exception: Exception) {
        throw SaveEntityException(message = "Error writing updated award to database.", cause = exception)
    }
}
