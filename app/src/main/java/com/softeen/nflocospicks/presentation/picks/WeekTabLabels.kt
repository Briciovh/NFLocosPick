package com.softeen.nflocospicks.presentation.picks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.softeen.nflocospicks.R
import com.softeen.nflocospicks.domain.model.SeasonType
import com.softeen.nflocospicks.domain.model.SeasonWeek

@Composable
fun SeasonWeek.tabLabel(): String = when {
    isHallOfFame                       -> stringResource(R.string.week_tab_hof)
    seasonType == SeasonType.PRESEASON -> stringResource(R.string.week_tab_preseason, displayNumber)
    seasonType == SeasonType.REGULAR   -> stringResource(R.string.week_tab_regular, weekNumber)
    weekNumber == 1                    -> stringResource(R.string.week_tab_wildcard)
    weekNumber == 2                    -> stringResource(R.string.week_tab_divisional)
    weekNumber == 3                    -> stringResource(R.string.week_tab_conference)
    else                                -> stringResource(R.string.week_tab_superbowl)
}
