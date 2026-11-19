package com.famlog.core.data

import com.famlog.core.model.Coordinates

/**
 * 현재 위치 한 번 (PLC-04 내 주변). 대략적 위치(ACCESS_COARSE_LOCATION)만 쓰고 계속 추적하지 않는다.
 * 권한이 없거나 위치를 못 받으면 null. 받은 좌표는 저장·로그하지 않는다 (사양서 8.6).
 */
interface LocationSource {
    suspend fun current(): Coordinates?
}
