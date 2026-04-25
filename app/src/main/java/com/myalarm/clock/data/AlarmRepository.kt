package com.myalarm.clock.data

import com.myalarm.clock.alarm.AlarmScheduler
import com.myalarm.clock.util.AppLogger
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val dao: AlarmDao,
    private val scheduler: AlarmScheduler,
    private val logger: AppLogger
) {
    companion object {
        private const val TAG = "Repo"
    }

    fun observeAll(): Flow<List<Alarm>> = dao.observeAll()

    suspend fun getById(id: Long): Alarm? = dao.getById(id)

    suspend fun save(alarm: Alarm): Long {
        logger.d(TAG, "Save alarm id=${alarm.id} (${if (alarm.id == 0L) "insert" else "update"})")
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
        logger.d(TAG, "Delete alarm id=${alarm.id}")
        scheduler.cancel(alarm.id)
        dao.delete(alarm)
    }

    suspend fun toggleEnabled(id: Long, enabled: Boolean) {
        logger.d(TAG, "Toggle alarm id=$id enabled=$enabled")
        dao.setEnabled(id, enabled)
        val alarm = dao.getById(id) ?: return
        if (enabled) {
            scheduler.schedule(alarm)
        } else {
            scheduler.cancel(id)
        }
    }

    suspend fun rescheduleAll(): Int {
        val list = dao.getAllEnabled()
        list.forEach { scheduler.schedule(it) }
        return list.size
    }
}
