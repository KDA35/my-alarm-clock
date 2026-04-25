package com.myalarm.clock.data

import android.content.Context
import com.myalarm.clock.alarm.AlarmScheduler
import com.myalarm.clock.util.AppLogger
import com.myalarm.clock.widget.NextAlarmWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val alarmDao: AlarmDao,
    private val groupDao: AlarmGroupDao,
    private val scheduler: AlarmScheduler,
    private val logger: AppLogger,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Repo"
        const val TEST_GROUP_NAME = "🧪 Тесты"
    }

    fun observeAll(): Flow<List<Alarm>> = alarmDao.observeAll()

    fun observeGroups(): Flow<List<AlarmGroup>> = groupDao.observeAll()

    suspend fun getById(id: Long): Alarm? = alarmDao.getById(id)

    suspend fun getAllEnabled(): List<Alarm> = alarmDao.getAllEnabled()

    suspend fun save(alarm: Alarm): Long {
        logger.d(TAG, "Save alarm id=${alarm.id} (${if (alarm.id == 0L) "insert" else "update"})")
        val id = if (alarm.id == 0L) {
            alarmDao.insert(alarm)
        } else {
            alarmDao.update(alarm)
            alarm.id
        }
        val saved = alarm.copy(id = id)
        if (saved.enabled) scheduler.schedule(saved) else scheduler.cancel(id)
        notifyWidget()
        return id
    }

    suspend fun delete(alarm: Alarm) {
        logger.d(TAG, "Delete alarm id=${alarm.id}")
        scheduler.cancel(alarm.id)
        alarmDao.delete(alarm)
        notifyWidget()
    }

    suspend fun toggleEnabled(id: Long, enabled: Boolean) {
        logger.d(TAG, "Toggle alarm id=$id enabled=$enabled")
        alarmDao.setEnabled(id, enabled)
        val alarm = alarmDao.getById(id) ?: return
        if (enabled) scheduler.schedule(alarm) else scheduler.cancel(id)
        notifyWidget()
    }

    suspend fun rescheduleAll(): Int {
        val list = alarmDao.getAllEnabled()
        list.forEach { scheduler.schedule(it) }
        notifyWidget()
        return list.size
    }

    suspend fun createGroup(name: String): Long {
        val id = groupDao.insert(AlarmGroup(name = name))
        logger.i(TAG, "Created group id=$id name='$name'")
        return id
    }

    suspend fun renameGroup(groupId: Long, newName: String) {
        val group = groupDao.getById(groupId) ?: return
        groupDao.update(group.copy(name = newName))
        logger.i(TAG, "Renamed group id=$groupId to '$newName'")
    }

    suspend fun deleteGroup(group: AlarmGroup) {
        val alarms = alarmDao.getByGroupId(group.id)
        alarms.forEach { alarmDao.update(it.copy(groupId = null)) }
        groupDao.delete(group)
        logger.i(TAG, "Deleted group id=${group.id}, ${alarms.size} alarms unlinked")
        notifyWidget()
    }

    suspend fun toggleGroupEnabled(groupId: Long, enabled: Boolean) {
        groupDao.setEnabled(groupId, enabled)
        alarmDao.setEnabledForGroup(groupId, enabled)
        val alarms = alarmDao.getByGroupId(groupId)
        alarms.forEach {
            if (enabled) scheduler.schedule(it) else scheduler.cancel(it.id)
        }
        logger.i(TAG, "Toggled group id=$groupId enabled=$enabled (${alarms.size} alarms)")
        notifyWidget()
    }

    suspend fun assignToGroup(alarmId: Long, groupId: Long?) {
        val alarm = alarmDao.getById(alarmId) ?: return
        alarmDao.update(alarm.copy(groupId = groupId))
        logger.i(TAG, "Alarm id=$alarmId linked to group id=$groupId")
    }

    suspend fun ensureTestGroup(): Long {
        val existing = groupDao.getByName(TEST_GROUP_NAME)
        if (existing != null) return existing.id
        val id = groupDao.insert(AlarmGroup(name = TEST_GROUP_NAME, enabled = true))
        logger.i(TAG, "Auto-created test group id=$id")
        return id
    }

    suspend fun deleteAllTestAlarms(): Int {
        val testGroup = groupDao.getByName(TEST_GROUP_NAME) ?: return 0
        val alarms = alarmDao.getByGroupId(testGroup.id)
        alarms.forEach { delete(it) }
        logger.i(TAG, "Deleted ${alarms.size} test alarms")
        return alarms.size
    }

    private fun notifyWidget() {
        runCatching { NextAlarmWidget.requestUpdate(context) }
            .onFailure { logger.w(TAG, "Widget update failed: ${it.message}") }
    }
}
