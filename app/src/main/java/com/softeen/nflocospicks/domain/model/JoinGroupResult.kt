package com.softeen.nflocospicks.domain.model

/** Outcome of [com.softeen.nflocospicks.domain.repository.GroupRepository.joinGroup]:
 *  the resulting [Group] plus whether the user was already a member before this
 *  call (vs. newly added). */
data class JoinGroupResult(val group: Group, val alreadyMember: Boolean)
