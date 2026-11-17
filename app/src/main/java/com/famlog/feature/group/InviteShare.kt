package com.famlog.feature.group

import android.content.Context
import android.content.Intent
import com.famlog.R

/** 초대 코드를 공유 시트로 보낸다 (GRP-02). 링크는 App Links 도메인이 정해지면 붙인다 */
fun Context.shareInvite(groupName: String, code: String) {
    val displayCode = normalizeInviteCode(code).chunked(4).joinToString("-")
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_share_text, groupName, displayCode))
    startActivity(Intent.createChooser(send, getString(R.string.invite_share_chooser)))
}
