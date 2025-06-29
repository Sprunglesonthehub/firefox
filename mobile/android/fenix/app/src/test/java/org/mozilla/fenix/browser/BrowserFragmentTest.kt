/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.browser

import android.content.Context
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.navigation.NavController
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import mozilla.components.browser.state.action.RestoreCompleteAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.support.test.ext.joinBlocking
import mozilla.components.support.test.rule.MainCoroutineRule
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.FeatureFlags
import org.mozilla.fenix.FenixApplication
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.components.toolbar.BrowserToolbarView
import org.mozilla.fenix.components.toolbar.ToolbarIntegration
import org.mozilla.fenix.ext.application
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.isLargeWindow
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.onboarding.FenixOnboarding
import org.mozilla.fenix.theme.ThemeManager
import org.mozilla.fenix.utils.Settings
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserFragmentTest {

    private lateinit var store: BrowserStore
    private lateinit var testTab: TabSessionState
    private lateinit var browserFragment: BrowserFragment
    private lateinit var view: View
    private lateinit var homeActivity: HomeActivity
    private lateinit var fenixApplication: FenixApplication
    private lateinit var context: Context
    private lateinit var lifecycleOwner: MockedLifecycleOwner
    private lateinit var navController: NavController
    private lateinit var onboarding: FenixOnboarding
    private lateinit var settings: Settings

    @get:Rule
    val coroutinesTestRule = MainCoroutineRule()

    @Before
    fun setup() {
        mockkStatic("org.mozilla.fenix.ext.FragmentKt")
        context = mockk(relaxed = true)
        fenixApplication = mockk(relaxed = true)
        every { context.application } returns fenixApplication

        homeActivity = mockk(relaxed = true)
        view = mockk(relaxed = true)
        lifecycleOwner = MockedLifecycleOwner(Lifecycle.State.STARTED)
        navController = mockk(relaxed = true)
        onboarding = mockk(relaxed = true)
        settings = mockk(relaxed = true)

        browserFragment = spyk(BrowserFragment())
        every { browserFragment.view } returns view
        every { browserFragment.isAdded } returns true
        every { browserFragment.browserToolbarView } returns mockk<BrowserToolbarView>(relaxed = true)
        every { browserFragment.browserToolbarInteractor } returns mockk(relaxed = true)
        every { browserFragment.activity } returns homeActivity
        every { browserFragment.lifecycle } returns lifecycleOwner.lifecycle
        every { context.components.fenixOnboarding } returns onboarding
        every { context.components.settings } returns settings

        every { browserFragment.requireContext() } returns context
        every { browserFragment.initializeUI(any(), any()) } returns mockk()
        every { browserFragment.fullScreenChanged(any()) } returns Unit

        testTab = createTab(url = "https://mozilla.org")
        store = BrowserStore()
        every { context.components.core.store } returns store

        mockkObject(FeatureFlags)
    }

    @After
    fun tearDown() {
        unmockkObject(FeatureFlags)
        unmockkStatic("org.mozilla.fenix.ext.FragmentKt")
    }

    @Test
    fun `GIVEN fragment is added WHEN selected tab changes THEN theme is updated`() {
        browserFragment.observeTabSelection(store, false)
        verify(exactly = 0) { browserFragment.updateThemeForSession(testTab) }

        addAndSelectTab(testTab)
        verify(exactly = 1) { browserFragment.updateThemeForSession(testTab) }
    }

    @Test
    fun `GIVEN fragment is added WHEN selected tab is customTab THEN theme is not updated`() {
        browserFragment.observeTabSelection(store, true)
        verify(exactly = 0) { browserFragment.updateThemeForSession(testTab) }

        addAndSelectTab(testTab)
        verify(exactly = 0) { browserFragment.updateThemeForSession(testTab) }
    }

    @Test
    fun `GIVEN fragment is removing WHEN selected tab changes THEN theme is not updated`() {
        every { browserFragment.isRemoving } returns true
        browserFragment.observeTabSelection(store, false)

        addAndSelectTab(testTab)
        verify(exactly = 0) { browserFragment.updateThemeForSession(testTab) }
    }

    @Test
    fun `GIVEN browser UI is not initialized WHEN selected tab changes THEN browser UI is initialized`() {
        browserFragment.observeTabSelection(store, false)
        verify(exactly = 0) { browserFragment.initializeUI(view, testTab) }

        addAndSelectTab(testTab)
        verify(exactly = 1) { browserFragment.initializeUI(view, testTab) }
    }

    @Test
    fun `GIVEN browser UI is initialized WHEN selected tab changes THEN toolbar is expanded`() {
        browserFragment.browserInitialized = true
        browserFragment.observeTabSelection(store, false)

        val toolbar: BrowserToolbarView = mockk(relaxed = true)
        every { browserFragment.browserToolbarView } returns toolbar

        val newSelectedTab = createTab("https://firefox.com")
        addAndSelectTab(newSelectedTab)
        verify(exactly = 1) { toolbar.expand() }
    }

    @Test
    fun `GIVEN browser UI is initialized WHEN selected tab changes THEN full screen mode is exited`() {
        browserFragment.browserInitialized = true
        browserFragment.observeTabSelection(store, false)

        val newSelectedTab = createTab("https://firefox.com")
        addAndSelectTab(newSelectedTab)
        verify(exactly = 1) { browserFragment.fullScreenChanged(false) }
    }

    @Test
    fun `GIVEN tabs are restored WHEN there are no tabs THEN navigate to home`() {
        browserFragment.observeRestoreComplete(store, navController)
        store.dispatch(RestoreCompleteAction).joinBlocking()

        verify(exactly = 1) { navController.popBackStack(R.id.homeFragment, false) }
    }

    @Test
    fun `GIVEN tabs are restored WHEN there are tabs THEN do not navigate`() {
        addAndSelectTab(testTab)
        browserFragment.observeRestoreComplete(store, navController)
        store.dispatch(RestoreCompleteAction).joinBlocking()

        verify(exactly = 0) { navController.popBackStack(R.id.homeFragment, false) }
    }

    @Test
    fun `GIVEN tabs are restored WHEN there is no selected tab THEN navigate to home`() {
        val store = BrowserStore(initialState = BrowserState(tabs = listOf(testTab)))
        browserFragment.observeRestoreComplete(store, navController)
        store.dispatch(RestoreCompleteAction).joinBlocking()

        verify(exactly = 1) { navController.popBackStack(R.id.homeFragment, false) }
    }

    @Test
    fun `GIVEN the onboarding is finished WHEN visiting any link THEN the onboarding is not dismissed `() {
        every { onboarding.userHasBeenOnboarded() } returns true

        browserFragment.observeTabSource(store)

        val newSelectedTab = createTab("any-tab.org")
        addAndSelectTab(newSelectedTab)

        verify(exactly = 0) { onboarding.finish() }
    }

    @Test
    fun `GIVEN the onboarding is not finished WHEN visiting a link THEN the onboarding is dismissed `() {
        every { onboarding.userHasBeenOnboarded() } returns false

        browserFragment.observeTabSource(store)

        val newSelectedTab = createTab("any-tab.org")
        addAndSelectTab(newSelectedTab)

        verify(exactly = 1) { onboarding.finish() }
    }

    @Test
    fun `GIVEN the onboarding is not finished WHEN visiting an onboarding link THEN the onboarding is not dismissed `() {
        every { onboarding.userHasBeenOnboarded() } returns false

        browserFragment.observeTabSource(store)

        val newSelectedTab = createTab(BaseBrowserFragment.onboardingLinksList[0])
        addAndSelectTab(newSelectedTab)

        verify(exactly = 0) { onboarding.finish() }
    }

    @Test
    fun `GIVEN the onboarding is not finished WHEN opening a page from another app THEN the onboarding is not dismissed `() {
        every { onboarding.userHasBeenOnboarded() } returns false

        browserFragment.observeTabSource(store)

        val newSelectedTab1 = createTab("any-tab-1.org", source = SessionState.Source.External.ActionSearch(mockk()))
        val newSelectedTab2 = createTab("any-tab-2.org", source = SessionState.Source.External.ActionView(mockk()))
        val newSelectedTab3 = createTab("any-tab-3.org", source = SessionState.Source.External.ActionSend(mockk()))
        val newSelectedTab4 = createTab("any-tab-4.org", source = SessionState.Source.External.CustomTab(mockk()))

        addAndSelectTab(newSelectedTab1)
        verify(exactly = 0) { onboarding.finish() }

        addAndSelectTab(newSelectedTab2)
        verify(exactly = 0) { onboarding.finish() }

        addAndSelectTab(newSelectedTab3)
        verify(exactly = 0) { onboarding.finish() }

        addAndSelectTab(newSelectedTab4)
        verify(exactly = 0) { onboarding.finish() }
    }

    @Test
    fun `GIVEN the onboarding is not finished WHEN visiting an link after redirect THEN the onboarding is not dismissed `() {
        every { onboarding.userHasBeenOnboarded() } returns false

        val newSelectedTab: TabSessionState = mockk(relaxed = true)
        every { newSelectedTab.content.loadRequest?.triggeredByRedirect } returns true

        browserFragment.observeTabSource(store)
        addAndSelectTab(newSelectedTab)

        verify(exactly = 0) { onboarding.finish() }
    }

    @Test
    fun `WHEN isPullToRefreshEnabledInBrowser is disabled THEN pull down refresh is disabled`() {
        every { context.settings().isPullToRefreshEnabledInBrowser } returns true
        assertTrue(browserFragment.shouldPullToRefreshBeEnabled(false))

        every { context.settings().isPullToRefreshEnabledInBrowser } returns false
        assertTrue(!browserFragment.shouldPullToRefreshBeEnabled(false))
    }

    @Test
    fun `WHEN in fullscreen THEN pull down refresh is disabled`() {
        every { context.settings().isPullToRefreshEnabledInBrowser } returns true
        assertTrue(browserFragment.shouldPullToRefreshBeEnabled(false))
        assertTrue(!browserFragment.shouldPullToRefreshBeEnabled(true))
    }

    @Test
    fun `WHEN fragment is not attached THEN toolbar invalidation does nothing`() {
        val browserToolbarView: BrowserToolbarView = mockk(relaxed = true)
        val browserToolbar: BrowserToolbar = mockk(relaxed = true)
        val toolbarIntegration: ToolbarIntegration = mockk(relaxed = true)
        every { browserToolbarView.toolbar } returns browserToolbar
        every { browserToolbarView.toolbarIntegration } returns toolbarIntegration
        every { browserFragment.context } returns null
        browserFragment._browserToolbarView = browserToolbarView
        browserFragment.safeInvalidateBrowserToolbarView()

        verify(exactly = 0) { browserToolbar.invalidateActions() }
        verify(exactly = 0) { toolbarIntegration.invalidateMenu() }
    }

    @Test
    @Suppress("TooGenericExceptionCaught")
    fun `WHEN fragment is attached and toolbar view is null THEN toolbar invalidation is safe`() {
        every { browserFragment.context } returns mockk(relaxed = true)
        try {
            browserFragment.safeInvalidateBrowserToolbarView()
        } catch (e: Exception) {
            fail("Exception thrown when invalidating toolbar")
        }
    }

    @Test
    fun `WHEN fragment and view are attached THEN toolbar invalidation is triggered`() {
        val browserToolbarView: BrowserToolbarView = mockk(relaxed = true)
        val browserToolbar: BrowserToolbar = mockk(relaxed = true)
        val toolbarIntegration: ToolbarIntegration = mockk(relaxed = true)
        every { browserToolbarView.toolbar } returns browserToolbar
        every { browserToolbarView.toolbarIntegration } returns toolbarIntegration
        every { browserFragment.context } returns mockk(relaxed = true)
        browserFragment._browserToolbarView = browserToolbarView
        browserFragment.safeInvalidateBrowserToolbarView()

        verify(exactly = 1) { browserToolbar.invalidateActions() }
        verify(exactly = 1) { toolbarIntegration.invalidateMenu() }
    }

    @Test
    fun `WHEN toolbar is initialized THEN onConfigurationChanged sets toolbar actions for size in fragment`() {
        val browserToolbarView: BrowserToolbarView = mockk(relaxed = true)
        every { browserFragment.reinitializeEngineView() } just Runs

        browserFragment._browserToolbarView = null
        browserFragment.onConfigurationChanged(mockk(relaxed = true))
        verify(exactly = 0) { browserFragment.onUpdateToolbarForConfigurationChange(any()) }
        verify(exactly = 0) { browserFragment.updateTabletToolbarActions(any()) }
        verify(exactly = 0) { browserFragment.reinitializeEngineView() }

        browserFragment._browserToolbarView = browserToolbarView

        mockkObject(ThemeManager.Companion)
        every { ThemeManager.resolveAttribute(any(), context) } returns mockk(relaxed = true)

        mockkStatic(AppCompatResources::class)
        every { AppCompatResources.getDrawable(context, any()) } returns mockk()

        browserFragment.onConfigurationChanged(mockk(relaxed = true))
        verify(exactly = 1) { browserFragment.onUpdateToolbarForConfigurationChange(any()) }
        verify(exactly = 1) { browserFragment.updateTabletToolbarActions(any()) }
        verify(exactly = 1) { browserFragment.reinitializeEngineView() }

        unmockkObject(ThemeManager.Companion)
        unmockkStatic(AppCompatResources::class)
    }

    @Test
    fun `WHEN fragment configuration changed THEN menu is dismissed`() {
        val browserToolbarView: BrowserToolbarView = mockk(relaxed = true)
        every { browserFragment.context } returns null
        every { browserFragment.reinitializeEngineView() } just Runs
        browserFragment._browserToolbarView = browserToolbarView

        mockkObject(ThemeManager.Companion)
        every { ThemeManager.resolveAttribute(any(), context) } returns mockk(relaxed = true)

        mockkStatic(AppCompatResources::class)
        every { AppCompatResources.getDrawable(context, any()) } returns mockk()

        browserFragment.onConfigurationChanged(mockk(relaxed = true))

        verify(exactly = 1) { browserToolbarView.dismissMenu() }

        unmockkObject(ThemeManager.Companion)
        unmockkStatic(AppCompatResources::class)
    }

    @Test
    fun `WHEN fragment configuration screen size changes between tablet and mobile size THEN tablet action items added and removed`() {
        val browserToolbarView: BrowserToolbarView = mockk(relaxed = true)
        val browserToolbar: BrowserToolbar = mockk(relaxed = true)
        val leadingAction: BrowserToolbar.Button = mockk(relaxed = true)
        browserFragment.leadingAction = leadingAction
        browserFragment._browserToolbarView = browserToolbarView
        every { browserToolbarView.toolbar } returns browserToolbar
        every { browserToolbarView.updateMenuVisibility(any()) } just Runs
        every { browserFragment.reinitializeEngineView() } just Runs

        mockkObject(ThemeManager.Companion)
        every { ThemeManager.resolveAttribute(any(), context) } returns mockk(relaxed = true)

        mockkStatic(AppCompatResources::class)
        every { AppCompatResources.getDrawable(context, any()) } returns mockk()

        every { browserFragment.isLargeWindow() } returns true
        browserFragment.onConfigurationChanged(mockk(relaxed = true))
        verify(exactly = 3) { browserToolbar.addNavigationAction(any()) }

        every { browserFragment.isLargeWindow() } returns false
        browserFragment.onConfigurationChanged(mockk(relaxed = true))
        verify(exactly = 3) { browserToolbar.removeNavigationAction(any()) }

        unmockkObject(ThemeManager.Companion)
        unmockkStatic(AppCompatResources::class)
    }

    @Test
    fun `WHEN fragment configuration change enables tablet size twice THEN tablet action items are only added once`() {
        val browserToolbarView: BrowserToolbarView = mockk(relaxed = true)
        val browserToolbar: BrowserToolbar = mockk(relaxed = true)
        val leadingAction: BrowserToolbar.Button = mockk(relaxed = true)
        browserFragment.leadingAction = leadingAction
        browserFragment._browserToolbarView = browserToolbarView
        every { browserToolbarView.toolbar } returns browserToolbar
        every { browserToolbarView.updateMenuVisibility(any()) } just Runs
        every { browserFragment.reinitializeEngineView() } just Runs

        mockkObject(ThemeManager.Companion)
        every { ThemeManager.resolveAttribute(any(), context) } returns mockk(relaxed = true)

        mockkStatic(AppCompatResources::class)
        every { AppCompatResources.getDrawable(context, any()) } returns mockk()

        every { browserFragment.isLargeWindow() } returns true
        browserFragment.onConfigurationChanged(mockk(relaxed = true))
        verify(exactly = 3) { browserToolbar.addNavigationAction(any()) }

        browserFragment.onConfigurationChanged(mockk(relaxed = true))
        verify(exactly = 3) { browserToolbar.addNavigationAction(any()) }

        unmockkObject(ThemeManager.Companion)
        unmockkStatic(AppCompatResources::class)
    }

    @Test
    fun `WHEN fragment configuration change sets mobile size twice THEN tablet action items are not added or removed`() {
        val browserToolbarView: BrowserToolbarView = mockk(relaxed = true)
        val browserToolbar: BrowserToolbar = mockk(relaxed = true)
        val leadingAction: BrowserToolbar.Button = mockk(relaxed = true)
        browserFragment.leadingAction = leadingAction
        browserFragment._browserToolbarView = browserToolbarView
        every { browserToolbarView.toolbar } returns browserToolbar
        every { browserToolbarView.updateMenuVisibility(any()) } just Runs
        every { browserFragment.reinitializeEngineView() } just Runs

        mockkObject(ThemeManager.Companion)
        every { ThemeManager.resolveAttribute(any(), context) } returns mockk(relaxed = true)

        mockkStatic(AppCompatResources::class)
        every { AppCompatResources.getDrawable(context, any()) } returns mockk()

        every { browserFragment.isLargeWindow() } returns false
        browserFragment.onConfigurationChanged(mockk(relaxed = true))
        verify(exactly = 0) { browserToolbar.addNavigationAction(any()) }
        verify(exactly = 0) { browserToolbar.removeNavigationAction(any()) }

        browserFragment.onConfigurationChanged(mockk(relaxed = true))
        verify(exactly = 0) { browserToolbar.addNavigationAction(any()) }
        verify(exactly = 0) { browserToolbar.removeNavigationAction(any()) }

        unmockkObject(ThemeManager.Companion)
        unmockkStatic(AppCompatResources::class)
    }

    private fun addAndSelectTab(tab: TabSessionState) {
        store.dispatch(TabListAction.AddTabAction(tab)).joinBlocking()
        store.dispatch(TabListAction.SelectTabAction(tab.id)).joinBlocking()
    }

    internal class MockedLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this).apply {
            currentState = initialState
        }
    }

    @Test
    fun `WHEN updating the last browse activity THEN update the associated preference`() {
        val settings: Settings = mockk(relaxed = true)

        every { browserFragment.context } returns context
        every { context.settings() } returns settings

        browserFragment.updateLastBrowseActivity()

        verify(exactly = 1) { settings.lastBrowseActivity = any() }
    }

    @Test
    fun `GIVEN redesign feature is off and configuration is portrait WHEN updating navigation icons THEN only leading action is added`() {
        mockThemeManagerAndAppCompatResources()

        val isLandscape = false
        browserFragment.updateBrowserToolbarLeadingAndNavigationActions(
            context = context,
            isLandscape = isLandscape,
            isTablet = false,
            isPrivate = false,
            feltPrivateBrowsingEnabled = false,
        )

        verify(exactly = 1) { browserFragment.addLeadingAction(any(), any()) }
        verify(exactly = 0) { browserFragment.addTabletActions(any()) }
        verify(exactly = 0) { browserFragment.addNavigationActions(any()) }

        unmockThemeManagerAndAppCompatResources()
    }

    @Test
    fun `GIVEN redesign feature is off and configuration is landscape WHEN updating navigation icons THEN only leading action is added`() {
        mockThemeManagerAndAppCompatResources()

        val isLandscape = true
        browserFragment.updateBrowserToolbarLeadingAndNavigationActions(
            context = context,
            isLandscape = isLandscape,
            isTablet = false,
            isPrivate = false,
            feltPrivateBrowsingEnabled = false,
        )

        verify(exactly = 1) { browserFragment.addLeadingAction(any(), any()) }
        verify(exactly = 0) { browserFragment.addTabletActions(any()) }
        verify(exactly = 0) { browserFragment.addNavigationActions(any()) }

        unmockThemeManagerAndAppCompatResources()
    }

    @Test
    fun `GIVEN tablet WHEN updating navigation icons THEN leading action and navigation buttons are added in order`() {
        mockThemeManagerAndAppCompatResources()

        val isTablet = true
        val isLandscape = true
        browserFragment.updateBrowserToolbarLeadingAndNavigationActions(
            context = context,
            isLandscape = isLandscape,
            isTablet = isTablet,
            isPrivate = false,
            feltPrivateBrowsingEnabled = false,
        )

        verifyOrder {
            browserFragment.addLeadingAction(any(), any())
            browserFragment.addNavigationActions(any())
        }

        unmockThemeManagerAndAppCompatResources()
    }

    @Test
    fun `GIVEN orientation is portrait and it is not tablet WHEN updating navigation icons THEN no action is removed`() {
        mockThemeManagerAndAppCompatResources()

        val isLandscape = false
        val isTablet = false

        browserFragment.updateBrowserToolbarLeadingAndNavigationActions(
            context = context,
            isLandscape = isLandscape,
            isPrivate = false,
            isTablet = isTablet,
            feltPrivateBrowsingEnabled = false,
        )

        verify(exactly = 0) { browserFragment.removeNavigationActions() }

        unmockThemeManagerAndAppCompatResources()
    }

    @Test
    fun `GIVEN orientation is portrait and it is tablet WHEN updating navigation actions THEN navigation actions are added`() {
        mockThemeManagerAndAppCompatResources()

        val isLandscape = false
        val isTablet = true

        browserFragment.updateBrowserToolbarLeadingAndNavigationActions(
            context = context,
            isLandscape = isLandscape,
            isPrivate = false,
            isTablet = isTablet,
            feltPrivateBrowsingEnabled = false,
        )

        verify(exactly = 1) { browserFragment.addLeadingAction(any(), any()) }
        verify(exactly = 1) { browserFragment.addNavigationActions(any()) }

        unmockThemeManagerAndAppCompatResources()
    }

    private fun mockThemeManagerAndAppCompatResources() {
        mockkObject(ThemeManager.Companion)
        every { ThemeManager.resolveAttribute(any(), context) } returns mockk(relaxed = true)

        mockkStatic(AppCompatResources::class)
        every { AppCompatResources.getDrawable(context, any()) } returns mockk()
    }

    private fun unmockThemeManagerAndAppCompatResources() {
        unmockkObject(ThemeManager.Companion)
        unmockkStatic(AppCompatResources::class)
    }
}
