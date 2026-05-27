package com.softeen.nflocospicks.di

import com.softeen.nflocospicks.data.repository.GroupRepositoryImpl
import com.softeen.nflocospicks.data.repository.UserRepositoryImpl
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository
}
