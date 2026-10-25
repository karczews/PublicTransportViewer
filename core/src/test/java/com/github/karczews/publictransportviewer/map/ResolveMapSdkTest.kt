package com.github.karczews.publictransportviewer.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import org.junit.Assert.assertSame
import org.junit.Test

class ResolveMapSdkTest {

    private class TestSdk(override val id: String) : MapSdk {
        override val displayName = id.uppercase()

        @Composable
        override fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier) = Unit
    }

    private val a = TestSdk("a")
    private val b = TestSdk("b")

    @Test
    fun returnsSavedSdk() {
        assertSame(b, resolveMapSdk(listOf(a, b), "b"))
    }

    @Test
    fun fallsBackToFirstForUnknownId() {
        assertSame(a, resolveMapSdk(listOf(a, b), "gone"))
    }

    @Test
    fun fallsBackToFirstForNullId() {
        assertSame(a, resolveMapSdk(listOf(a, b), null))
    }
}
