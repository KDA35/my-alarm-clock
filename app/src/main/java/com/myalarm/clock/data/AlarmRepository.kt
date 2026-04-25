package com.myalarm.clock.data

import com.myalarm.clock.alarm.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler
) {
    fun observeAll(): Flow<List<Alarm>> = dao.observeAll()

    suspend fun getById(id: Long): Alarm? = dao.getById(id)

    suspend fun save(alarm: Alarm): Long {
        val id = if (alarm.id == 0L) {
            dao.insert(alarm)
        } else {
            dao.update(alarm)
            alarm.id
        }
        val saved = alarm.copy(id = id)
        if (saved.enabled) {
            scheduler.schedule(saved)
        } else {
            scheduler.cancel(id)
        }
        return id
    }

    suspend fun delete(alarm: Alarm) {
        scheduler.cancel(alarm.id)
        dao.delete(alarm)
    }

    suspend fun toggleEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        val alarm = dao.getById(id) ?: return
        if (enabled) {
            scheduler.schedule(alarm)
        } else {
            scheduler.cancel(id)
        }
    }

    suspend fun rescheduleAll() {
        dao.getAllEnabled().forEach { scheduler.schedule(it) }
    }
}
