package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.usecase.SaveContactResult
import com.akib.mycompanion.domain.usecase.SaveContactUseCase
import com.akib.mycompanion.fakes.FakeContactRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SaveContactUseCaseTest {

    private val repository = FakeContactRepository()
    private val save = SaveContactUseCase(repository)

    @Test
    fun `adds a person with a cleaned name and normalised number`() = runTest {
        val result = save(id = null, name = "  Akib   Hasan ", phone = "01711-123456")

        assertThat(result).isInstanceOf(SaveContactResult.Saved::class.java)
        val saved = (result as SaveContactResult.Saved).contact
        assertThat(saved.id).isGreaterThan(0L)
        assertThat(saved.name).isEqualTo("Akib Hasan")
        assertThat(saved.phone).isEqualTo("+8801711123456")
        assertThat(repository.all).containsExactly(saved)
    }

    @Test
    fun `blank name is rejected`() = runTest {
        assertThat(save(null, "   ", "01711123456")).isEqualTo(SaveContactResult.MissingName)
        assertThat(repository.all).isEmpty()
    }

    @Test
    fun `invalid number is rejected`() = runTest {
        assertThat(save(null, "Akib", "12ab")).isEqualTo(SaveContactResult.InvalidPhone)
        assertThat(repository.all).isEmpty()
    }

    @Test
    fun `a number already saved for someone else is rejected`() = runTest {
        val mum = repository.save(Contact(name = "Mum", phone = "+8801711123456"))

        val result = save(null, "Akib", "+880 1711 123456")

        assertThat(result).isEqualTo(SaveContactResult.DuplicatePhone(mum))
        assertThat(repository.all).containsExactly(mum)
    }

    @Test
    fun `editing keeps the id and may keep the same number`() = runTest {
        val mum = repository.save(Contact(name = "Mum", phone = "+8801711123456"))

        val result = save(mum.id, "Mom", "01711123456")

        assertThat(result).isEqualTo(SaveContactResult.Saved(mum.copy(name = "Mom")))
        assertThat(repository.all).containsExactly(mum.copy(name = "Mom"))
    }

    @Test
    fun `editing to another person's number is rejected`() = runTest {
        val mum = repository.save(Contact(name = "Mum", phone = "+8801711123456"))
        val dad = repository.save(Contact(name = "Dad", phone = "+8801811123456"))

        assertThat(save(dad.id, "Dad", "01711123456")).isEqualTo(SaveContactResult.DuplicatePhone(mum))
    }
}
