package com.famlog.core.model

import java.time.Instant

/** 내 데이터 내보내기 상태 (ACC-02). REQUESTED·PROCESSING은 준비 중 */
enum class DataExportStatus {
    REQUESTED, PROCESSING, READY, FAILED, EXPIRED;

    val preparing: Boolean get() = this == REQUESTED || this == PROCESSING

    companion object {
        fun of(value: String?): DataExportStatus = entries.firstOrNull { it.name == value } ?: REQUESTED
    }
}

/**
 * 내보내기 요청 하나. [downloadUrl]은 READY일 때 상세 조회에만 오는 10분짜리 링크라 저장·로그하지 않는다.
 * [expiresAt]은 준비된 뒤 7일, 지나면 서버가 파일을 지우고 EXPIRED가 된다
 */
data class DataExport(
    val id: Long,
    val status: DataExportStatus,
    val requestedAt: Instant,
    val readyAt: Instant? = null,
    val expiresAt: Instant? = null,
    val sizeBytes: Long? = null,
    val downloadUrl: String? = null,
    val fileName: String? = null,
) {
    /** 다운로드 링크는 서명이 든 주소라 남기지 않는다 */
    override fun toString(): String = "DataExport(id=$id, status=$status)"
}
