package com.softeen.nflocospicks.presentation.common

private const val APP_LINK_HOST = "nflocospicks.web.app"

/** Builds the shareable App Link a group admin sends to invite someone. Must
 *  match AndroidManifest.xml's join intent-filter host/pathPrefix exactly. */
fun buildGroupInviteLink(inviteCode: String): String =
    "https://$APP_LINK_HOST/join?code=$inviteCode"
