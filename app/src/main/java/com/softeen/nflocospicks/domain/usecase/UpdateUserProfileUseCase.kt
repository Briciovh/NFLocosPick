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
        // Casing/symbols are preserved as typed — only the case-insensitive `usernames/{id}`
        // reservation key (computed in the repository) enforces uniqueness.
        username    = username.trim(),
        displayName = displayName?.trim(),
        photoUrl    = photoUrl
    )
}
