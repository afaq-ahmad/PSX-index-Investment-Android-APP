package pk.psx.wealth.feature.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioSession @Inject constructor() {
    private val _selectedPortfolioId = MutableStateFlow<Long?>(null)
    val selectedPortfolioId: StateFlow<Long?> = _selectedPortfolioId
    fun select(id: Long?) { _selectedPortfolioId.value = id }
}
