/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.sessioncontrol

import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mozilla.components.feature.tab.collections.TabCollection
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.service.nimbus.messaging.Message
import mozilla.components.service.pocket.PocketStory
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.components.appstate.AppAction
import org.mozilla.fenix.components.appstate.AppState
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.ext.shouldShowRecentSyncedTabs
import org.mozilla.fenix.ext.shouldShowRecentTabs
import org.mozilla.fenix.home.bookmarks.Bookmark
import org.mozilla.fenix.home.ext.showWallpaperOnboardingDialog
import org.mozilla.fenix.home.recentvisits.RecentlyVisitedItem
import org.mozilla.fenix.messaging.FenixMessageSurfaceId
import org.mozilla.fenix.onboarding.HomeCFRPresenter
import org.mozilla.fenix.search.SearchDialogFragment
import org.mozilla.fenix.utils.Settings

// This method got a little complex with the addition of the tab tray feature flag
// When we remove the tabs from the home screen this will get much simpler again.
@Suppress("ComplexMethod", "LongParameterList")
@VisibleForTesting
internal fun normalModeAdapterItems(
    settings: Settings,
    topSites: List<TopSite>,
    collections: List<TabCollection>,
    expandedCollections: Set<Long>,
    bookmarks: List<Bookmark>,
    showCollectionsPlaceholder: Boolean,
    nimbusMessageCard: Message? = null,
    showRecentTab: Boolean,
    showRecentSyncedTab: Boolean,
    recentVisits: List<RecentlyVisitedItem>,
    pocketStories: List<PocketStory>,
    firstFrameDrawn: Boolean = false,
): List<AdapterItem> {
    val items = mutableListOf<AdapterItem>()

    // Add a synchronous, unconditional and invisible placeholder so home is anchored to the top when created.
    items.add(AdapterItem.TopPlaceholderItem)

    nimbusMessageCard?.let {
        items.add(AdapterItem.NimbusMessageCard(it))
    }

    if (settings.showTopSitesFeature && topSites.isNotEmpty()) {
        items.add(AdapterItem.TopSitePager(topSites))
    }

    if (settings.showSetupChecklist) {
        items.add(AdapterItem.SetupChecklist)
    }

    if (showRecentTab) {
        items.add(AdapterItem.RecentTabsHeader)
        items.add(AdapterItem.RecentTabItem)
        if (showRecentSyncedTab) {
            items.add(AdapterItem.RecentSyncedTabItem)
        }
    }

    if (settings.showBookmarksHomeFeature && bookmarks.isNotEmpty()) {
        items.add(AdapterItem.BookmarksHeader)
        items.add(AdapterItem.Bookmarks)
    }

    if (settings.historyMetadataUIFeature && recentVisits.isNotEmpty()) {
        items.add(AdapterItem.RecentVisitsHeader)
        items.add(AdapterItem.RecentVisitsItems)
    }

    if (collections.isEmpty()) {
        if (showCollectionsPlaceholder) {
            items.add(AdapterItem.NoCollectionsMessage)
        }
    } else {
        showCollections(collections, expandedCollections, items)
    }

    // When Pocket is enabled and the initial layout of the app is done, then we can add these items
    // to render to the home screen.
    // This is only useful while we have a RecyclerView + Compose implementation. We can remove this
    // when we switch to a Compose-only home screen.
    if (firstFrameDrawn && settings.showPocketRecommendationsFeature && pocketStories.isNotEmpty()) {
        items.add(AdapterItem.PocketStoriesItem)
    }

    items.add(AdapterItem.BottomSpacer)

    return items
}

private fun showCollections(
    collections: List<TabCollection>,
    expandedCollections: Set<Long>,
    items: MutableList<AdapterItem>,
) {
    // If the collection is expanded, we want to add all of its tabs beneath it in the adapter
    items.add(AdapterItem.CollectionHeader)
    collections.map {
        AdapterItem.CollectionItem(it, expandedCollections.contains(it.id))
    }.forEach {
        items.add(it)
        if (it.expanded) {
            items.addAll(collectionTabItems(it.collection))
        }
    }
}

private fun privateModeAdapterItems() = listOf(AdapterItem.PrivateBrowsingDescription)

private fun AppState.toAdapterList(settings: Settings): List<AdapterItem> = when (mode) {
    BrowsingMode.Normal -> normalModeAdapterItems(
        settings,
        topSites,
        collections,
        expandedCollections,
        bookmarks,
        showCollectionPlaceholder,
        messaging.messageToShow[FenixMessageSurfaceId.HOMESCREEN],
        shouldShowRecentTabs(settings),
        shouldShowRecentSyncedTabs(),
        recentHistory,
        recommendationState.pocketStories,
        firstFrameDrawn,
    )
    BrowsingMode.Private -> privateModeAdapterItems()
}

private fun collectionTabItems(collection: TabCollection) =
    collection.tabs.mapIndexed { index, tab ->
        AdapterItem.TabInCollectionItem(collection, tab, index == collection.tabs.lastIndex)
    }

/**
 * Shows a list of Home screen views.
 *
 * @param containerView The [View] that is used to initialize the Home recycler view.
 * @param viewLifecycleOwner [LifecycleOwner] for the view.
 * @param fragmentManager The [FragmentManager] of the parent [Fragment].
 * @param interactor [SessionControlInteractor] which will have delegated to all user interactions.
 */
class SessionControlView(
    containerView: View,
    viewLifecycleOwner: LifecycleOwner,
    fragmentManager: FragmentManager,
    private val interactor: SessionControlInteractor,
) {

    val view: RecyclerView = containerView as RecyclerView

    // We want to limit feature recommendations to one per HomePage visit.
    var featureRecommended = false

    private val sessionControlAdapter = SessionControlAdapter(
        interactor,
        viewLifecycleOwner,
        containerView.context.components,
    )

    init {
        @Suppress("NestedBlockDepth")
        view.apply {
            adapter = sessionControlAdapter
            layoutManager = object : LinearLayoutManager(containerView.context) {
                override fun onLayoutCompleted(state: RecyclerView.State?) {
                    super.onLayoutCompleted(state)

                    val searchDialogFragment: SearchDialogFragment? =
                        fragmentManager.fragments.find { it is SearchDialogFragment } as SearchDialogFragment?

                    with(settings()) {
                        if (!featureRecommended) {
                            if (searchDialogFragment == null && showSyncCFR) {
                                featureRecommended =
                                    HomeCFRPresenter(context = context, recyclerView = view).show()
                            }

                            if (showWallpaperOnboardingDialog(featureRecommended)) {
                                featureRecommended = interactor.showWallpapersOnboardingDialog(
                                    context.components.appStore.state.wallpaperState,
                                )
                            }
                        }
                    }

                    // We want some parts of the home screen UI to be rendered first if they are
                    // the most prominent parts of the visible part of the screen.
                    // For this reason, we wait for the home screen recycler view to finish it's
                    // layout and post an update for when it's best for non-visible parts of the
                    // home screen to render itself.
                    containerView.context.components.appStore.dispatch(
                        AppAction.UpdateFirstFrameDrawn(true),
                    )
                }
            }
        }
    }

    fun update(state: AppState, shouldReportMetrics: Boolean = false) {
        if (shouldReportMetrics) interactor.reportSessionMetrics(state)

        sessionControlAdapter.submitList(state.toAdapterList(view.context.settings()))
    }
}
