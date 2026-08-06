package com.softeen.nflocospicks.domain.usecase

import com.softeen.nflocospicks.domain.repository.UserRepository
import javax.inject.Inject

class UpdateUserProfileUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(
        uid: String,
        username: String,
        displayName: String? = null,
        photoUrl: String? = null
    ): Result<Unit> = userRepository.updateProfile(
        uid         = uid,
        username    = username.trim().lowercase(),
        displayName = displayName?.trim(),
        photoUrl    = photoUrl
    )
}
