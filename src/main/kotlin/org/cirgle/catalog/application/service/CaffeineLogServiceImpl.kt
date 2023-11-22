package org.cirgle.catalog.application.service

import org.cirgle.catalog.domain.exception.UserNotFoundException
import org.cirgle.catalog.domain.model.*
import org.cirgle.catalog.domain.repository.CaffeineLogRepository
import org.cirgle.catalog.domain.service.CaffeineLogService
import org.cirgle.catalog.infrastructure.persistence.entity.CaffeineLogDetailEntity
import org.cirgle.catalog.infrastructure.persistence.entity.user.ConsumedMenuTypeEntity
import org.cirgle.catalog.infrastructure.persistence.entity.user.UserDetailEntity
import org.cirgle.catalog.infrastructure.persistence.repository.jpa.JpaCaffeineLogDetailRepository
import org.cirgle.catalog.infrastructure.persistence.repository.jpa.JpaConsumedMenuTypeRepository
import org.cirgle.catalog.infrastructure.persistence.repository.jpa.JpaUserDetailRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Service
class CaffeineLogServiceImpl(
    private val jpaUserDetailRepository: JpaUserDetailRepository,
    private val jpaCaffeineLogDetailRepository: JpaCaffeineLogDetailRepository,
    private val jpaConsumedMenuTypeRepository: JpaConsumedMenuTypeRepository,
    private val caffeineLogRepository: CaffeineLogRepository,
) : CaffeineLogService {

    @Transactional
    override fun initializeCaffeineLog(userId: UUID) {

        val userDetail: UserDetailEntity = jpaUserDetailRepository.findById(userId).getOrNull()
            ?: throw UserNotFoundException()

        val maxCaffeine = if(LocalDate.now().year - userDetail.birthday.year < 14) 100
                            else if(LocalDate.now().year - userDetail.birthday.year < 19) 150
                            else if(LocalDate.now().year - userDetail.birthday.year < 65) 300
                            else 200

        val todayCaffeineLog = TodayCaffeineLog(
            lastCommitted = LocalDate.now().minusDays(1),
            consumedCaffeine = 0,
            maxCaffeine = maxCaffeine,
        )
        caffeineLogRepository.updateTodayCaffeineLog(userId, todayCaffeineLog)

        val caffeineLogDetail = CaffeineLogDetailEntity(
            userId = userId,
            maxCaffeine = todayCaffeineLog.maxCaffeine,
        )
        jpaCaffeineLogDetailRepository.save(caffeineLogDetail)
    }

    override fun setMaxCaffeine(userId: UUID, value: Int) {
        val caffeineLogDetail = jpaCaffeineLogDetailRepository.findById(userId).getOrNull()
            ?: throw UserNotFoundException()

        jpaCaffeineLogDetailRepository.save(
            caffeineLogDetail.copy(
                maxCaffeine = value,
            )
        )
    }

    @Transactional
    override fun consumeCaffeineMenu(userId: UUID, caffeineMenu: CaffeineMenu) {

        val today = LocalDate.now()
        val todayCaffeineLog = caffeineLogRepository.getTodayCaffeineLog(userId)

        val newTodayCaffeineLog = todayCaffeineLog.copy(
            lastCommitted = today,
            consumedCaffeine = (if(todayCaffeineLog.lastCommitted == LocalDate.now())
                todayCaffeineLog.consumedCaffeine else 0) + caffeineMenu.caffeine
        )
        val consumedMenuType =
            jpaConsumedMenuTypeRepository.findByUserIdAndMenuTypeAndDate(userId, caffeineMenu.type, today)
                ?: ConsumedMenuTypeEntity(userId = userId, date = today, menuType = caffeineMenu.type)

        val newConsumedMenuType = consumedMenuType.copy(
            consumedCaffeine = consumedMenuType.consumedCaffeine + caffeineMenu.caffeine
        )
        jpaConsumedMenuTypeRepository.save(newConsumedMenuType)
        caffeineLogRepository.updateTodayCaffeineLog(userId, newTodayCaffeineLog)
    }

    override fun getTodayCaffeineLog(userId: UUID): TodayCaffeineLog {
        return caffeineLogRepository.getTodayCaffeineLog(userId)
    }

    override fun getCaffeineLogDetail(userId: UUID): CaffeineLogDetail {
        return jpaCaffeineLogDetailRepository.findById(userId).getOrNull()?.toDomain()
            ?: throw UserNotFoundException()
    }

    override fun findAllConsumedMenuType(
        userId: UUID,
        menuType: MenuType,
        start: LocalDate,
        end: LocalDate
    ): List<ConsumedMenuType> {
        return jpaConsumedMenuTypeRepository
            .findAllByUserIdAndMenuTypeAndDateBetween(userId, menuType, start, end)
            .map {
                it.toDomain()
            }
    }

    override fun findAllCaffeineLog(userId: UUID, start: LocalDate, end: LocalDate): List<DailyCaffeineLog> {
        return caffeineLogRepository.findAllCaffeineLog(userId, start, end)
    }

    private fun CaffeineLogDetailEntity.toDomain() = CaffeineLogDetail(
        maxCaffeine = maxCaffeine,
        streak = streak,
        maxStreak = maxStreak,
    )
}