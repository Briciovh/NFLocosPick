package com.softeen.nflocospicks.domain.usecase

import android.net.Uri
import com.softeen.nflocospicks.domain.repository.GroupRepository
import javax.inject.Inject

class UploadGroupPhotoUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend operator fun invoke(groupId: String, uri: Uri): Result<String> =
        groupRepository.uploadGroupPhoto(groupId, uri)
}
