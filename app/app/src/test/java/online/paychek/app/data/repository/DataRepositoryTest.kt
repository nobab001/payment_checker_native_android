package online.paychek.app.data.repository

import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataRepositoryTest {

    @Test
    fun defaultRepository_emitsData() = runTest {
        val repo = DefaultDataRepository()
        val result = repo.data.first()
        assertNotNull(result)
    }
}
