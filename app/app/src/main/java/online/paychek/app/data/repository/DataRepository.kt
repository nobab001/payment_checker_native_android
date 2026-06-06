package online.paychek.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Placeholder data repository — will be replaced by proper
 * domain repository implementations (AuthRepository, PaymentRepository, etc.)
 */
interface DataRepository {
    val data: Flow<List<String>>
}

class DefaultDataRepository : DataRepository {
    override val data: Flow<List<String>> = flow { emit(listOf("Paychek")) }
}
