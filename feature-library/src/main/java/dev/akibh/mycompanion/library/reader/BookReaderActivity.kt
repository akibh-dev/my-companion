package dev.akibh.mycompanion.library.reader

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.akibh.mycompanion.library.R
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.ui.theme.CompanionTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

/**
 * Reads one book: Readium's navigator fills the screen, the Compose controls sit over it. A tap in
 * the middle shows or hides the controls (and the system bars); taps near the edges turn pages.
 */
@AndroidEntryPoint
class BookReaderActivity : AppCompatActivity() {

    private val viewModel: BookReaderViewModel by viewModels()
    private val systemDark = MutableStateFlow(false)
    private var attachedGeneration = 0
    private var locatorJob: Job? = null
    private var container: FragmentContainerView? = null

    private val locate = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::relink)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // Readium's fragments can't be rebuilt by Android (they need the open book), so nothing is
        // restored: the book reopens at its saved place instead.
        super.onCreate(null)
        systemDark.value = resources.configuration.isNight()

        val container = FragmentContainerView(this).apply { id = R.id.library_reader_container }
        this.container = container
        // Keep pages clear of the camera cutout; the bars come and go over the page.
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom)
            insets
        }
        val chrome = ComposeView(this).apply {
            setContent {
                val look by viewModel.look.collectAsStateWithLifecycle()
                val night by systemDark.collectAsStateWithLifecycle()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val format = (state as? ReaderState.Ready)?.session?.book?.format
                CompanionTheme(darkTheme = Navigators.pageIsDark(look, night)) {
                    ReaderScreen(
                        viewModel = viewModel,
                        pageColor = Color(Navigators.pageColor(look, night, format)),
                        onBack = ::finish,
                        onGo = ::go,
                        onGoLink = ::go,
                        onSearchHit = ::goToHit,
                        onLocate = { locate.launch(BookFormat.PICKER_TYPES) },
                    )
                }
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                addView(chrome, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            },
        )

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) { viewModel.state.collect(::render) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                combine(viewModel.look, systemDark) { look, dark -> look to dark }.collect { (look, dark) ->
                    Navigators.submit(navigatorFragment(), look, dark)
                    container?.setBackgroundColor(Navigators.pageColor(look, dark, currentFormat()))
                    if (look.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    bars(viewModel.chromeVisible.value, Navigators.pageIsDark(look, dark))
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.chromeVisible.collect {
                    bars(
                        it,
                        Navigators.pageIsDark(viewModel.look.value, systemDark.value)
                    )
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDark.value = newConfig.isNight()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resume()
    }

    override fun onPause() {
        viewModel.pause()
        super.onPause()
    }

    private fun render(state: ReaderState) {
        if (state is ReaderState.Ready) {
            if (state.session.generation != attachedGeneration) attach(state.session)
        } else {
            navigatorFragment()?.let { fragment -> supportFragmentManager.commitNow { remove(fragment) } }
            attachedGeneration = 0
            locatorJob?.cancel()
            viewModel.releaseRetired()
        }
    }

    private fun attach(session: ReaderSession) {
        supportFragmentManager.fragmentFactory = Navigators.factory(session, viewModel.look.value, systemDark.value)
        supportFragmentManager.commitNow {
            replace(R.id.library_reader_container, Navigators.fragmentClass(session.book.format), null, NAVIGATOR)
        }
        attachedGeneration = session.generation
        viewModel.releaseRetired()
        container?.setBackgroundColor(Navigators.pageColor(viewModel.look.value, systemDark.value, session.book.format))

        val navigator = navigatorFragment() as? VisualNavigator ?: return
        (navigator as? OverflowableNavigator)?.let {
            navigator.addInputListener(DirectionalNavigationAdapter(it, animatedTransition = true))
        }
        navigator.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    viewModel.toggleChrome()
                    return true
                }

                override fun onDrag(event: DragEvent): Boolean {
                    viewModel.activity()
                    return false
                }
            },
        )
        locatorJob?.cancel()
        locatorJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { navigator.currentLocator.collect(viewModel::onLocator) }
        }
    }

    private fun currentFormat(): BookFormat? = (viewModel.state.value as? ReaderState.Ready)?.session?.book?.format

    private fun navigatorFragment(): Fragment? = supportFragmentManager.findFragmentByTag(NAVIGATOR)

    private fun go(locator: Locator) {
        (navigatorFragment() as? Navigator)?.go(locator, animated = false)
    }

    @OptIn(ExperimentalReadiumApi::class)
    /** Jumps to a search match and marks it until the next search or until the reader closes. */
    private fun goToHit(locator: Locator) {
        go(locator)
        val navigator = navigatorFragment() as? DecorableNavigator ?: return
        lifecycleScope.launch {
            navigator.applyDecorations(
                listOf(Decoration("search", locator, Decoration.Style.Highlight(tint = SEARCH_TINT))),
                group = "search",
            )
        }
    }

    private fun go(link: Link) {
        (navigatorFragment() as? Navigator)?.go(link, animated = false)
    }

    /** System bars follow the controls: shown with them, hidden (swipe to peek) while reading. */
    private fun bars(visible: Boolean, darkPage: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = !darkPage
            isAppearanceLightNavigationBars = !darkPage
            if (visible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun Configuration.isNight() =
        (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        private const val NAVIGATOR = "navigator"

        /** The brand red, see-through, so the words stay readable under it. */
        private const val SEARCH_TINT = 0x66FF3B3B

        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, BookReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
