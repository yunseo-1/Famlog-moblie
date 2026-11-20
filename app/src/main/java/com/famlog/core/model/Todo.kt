package com.famlog.core.model

import java.time.Instant
import java.time.LocalDate

/** 할 일 (TODO-01). 수정할 때 [version]을 함께 보낸다 */
data class Todo(
    val id: Long,
    val groupId: Long,
    val title: String,
    val assigneeId: Long?,
    val dueDate: LocalDate?,
    val done: Boolean,
    val doneAt: Instant?,
    val doneBy: Long?,
    val createdBy: Long?,
    val version: Long,
    /** 채팅에서 등록한 할 일이면 원문 (ARC-06) */
    val source: ItemSource? = null,
)

/** 할 일 목록 한 페이지. [nextCursor]가 있으면 더 있다 */
data class TodoPage(val items: List<Todo>, val nextCursor: String?)

/** 제목·담당자·기한 고치기. 담당자·기한을 null로 보내면 지운다 */
data class TodoEdit(val title: String, val assigneeId: Long?, val dueDate: LocalDate?)
