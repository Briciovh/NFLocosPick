package com.softeen.nflocospicks.di

import com.softeen.nflocospicks.data.repository.GroupRepositoryImpl
import com.softeen.nflocospicks.data.repository.MockSessionRepositoryImpl
import com.softeen.nflocospicks.data.repository.UserPreferencesRepositoryImpl
import com.softeen.nflocospicks.data.repository.HistoryRepositoryImpl
import com.softeen.nflocospicks.data.repository.LeaderboardRepositoryImpl
import com.softeen.nflocospicks.data.repository.PickRepositoryImpl
import com.softeen.nflocospicks.data.repository.ScheduleRepositoryImpl
import com.softeen.nflocospicks.data.repository.ScoringRepositoryImpl
import com.softeen.nflocospicks.data.repository.UserRepositoryImpl
import com.softeen.nflocospicks.domain.repository.GroupRepository
import com.softeen.nflocospicks.domain.repository.MockSessionRepository
import com.softeen.nflocospicks.domain.repository.UserPreferencesRepository
import com.softeen.nflocospicks.domain.repository.HistoryRepository
import com.softeen.nflocospicks.domain.repository.LeaderboardRepository
import com.softeen.nflocospicks.domain.repository.PickRepository
import com.softeen.nflocospicks.domain.repository.ScheduleRepository
import com.softeen.nflocospicks.domain.repository.ScoringRepository
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

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindPickRepository(impl: PickRepositoryImpl): PickRepository

    @Binds
    @Singleton
    abstract fun bindScoringRepository(impl: ScoringRepositoryImpl): ScoringRepository

    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(impl: LeaderboardRepositoryImpl): LeaderboardRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindMockSessionRepository(impl: MockSessionRepositoryImpl): MockSessionRepository
}
