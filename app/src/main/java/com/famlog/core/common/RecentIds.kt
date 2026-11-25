package com.famlog.core.common

/** 최근에 본 ID를 [capacity]개까지 기억한다. 같은 푸시가 두 번 와도 알림을 한 번만 띄우려고 쓴다 */
class RecentIds(private val capacity: Int) {

    private val ids = LinkedHashSet<Long>()

    /** 처음 보는 ID면 기억하고 true. 이미 봤으면 false */
    @Synchronized
    fun add(id: Long): Boolean {
        if (!ids.add(id)) return false
        if (ids.size > capacity) ids.remove(ids.first())
        return true
    }
}
