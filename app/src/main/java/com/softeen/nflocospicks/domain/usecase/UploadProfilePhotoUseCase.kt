package com.softeen.nflocospicks.domain.usecase

import android.net.Uri
import com.softeen.nflocospicks.domain.repository.UserRepository
import javax.inject.Inject

class UploadProfilePhotoUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(uid: String, uri: Uri): Result<String> =
        userRepository.uploadProfilePhoto(uid, uri)
}
