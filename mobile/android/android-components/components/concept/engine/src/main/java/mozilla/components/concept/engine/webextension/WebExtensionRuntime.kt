/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.concept.engine.webextension

import mozilla.components.concept.engine.CancellableOperation
import java.lang.UnsupportedOperationException

/**
 * Entry point for interacting with the web extensions.
 */
interface WebExtensionRuntime {

    /**
     * Installs the provided built-in extension in this engine.
     *
     * @param id the unique ID of the extension.
     * @param url the url pointing to either a resources path for locating the extension
     * within the APK file (e.g. resource://android/assets/extensions/my_web_ext) or to a
     * local (e.g. resource://android/assets/extensions/my_web_ext.xpi) XPI file. An error
     * is thrown if a non-resource URL is passed.
     * @param onSuccess (optional) callback invoked if the extension was installed successfully,
     * providing access to the [WebExtension] object for bi-directional messaging.
     * @param onError (optional) callback invoked if there was an error installing the extension.
     * This callback is invoked with an [UnsupportedOperationException] in case the engine doesn't
     * have web extension support.
     */
    fun installBuiltInWebExtension(
        id: String,
        url: String,
        onSuccess: ((WebExtension) -> Unit) = { },
        onError: ((Throwable) -> Unit) = { _ -> },
    ): CancellableOperation {
        onError(UnsupportedOperationException("Web extension support is not available in this engine"))
        return CancellableOperation.Noop()
    }

    /**
     * Installs a [WebExtension] from the provided [url] in this engine.
     *
     * @param url the url pointing to an XPI file. An error is thrown when a resource URL is passed.
     * @param onSuccess (optional) callback invoked if the extension was installed successfully,
     * providing access to the [WebExtension] object for bi-directional messaging.
     * @param installationMethod (optional) the method used to install a [WebExtension].
     * @param onError (optional) callback invoked if there was an error installing the extension.
     * This callback is invoked with an [UnsupportedOperationException] in case the engine doesn't
     * have web extension support.
     */
    fun installWebExtension(
        url: String,
        installationMethod: InstallationMethod? = null,
        onSuccess: ((WebExtension) -> Unit) = { },
        onError: ((Throwable) -> Unit) = { _ -> },
    ): CancellableOperation {
        onError(UnsupportedOperationException("Web extension support is not available in this engine"))
        return CancellableOperation.Noop()
    }

    /**
     * Updates the provided [extension] if a new version is available.
     *
     * @param extension the extension to be updated.
     * @param onSuccess (optional) callback invoked if the extension was updated successfully,
     * providing access to the [WebExtension] object for bi-directional messaging, if null is provided
     * that means that the [WebExtension] hasn't been change since the last update.
     * @param onError (optional) callback invoked if there was an error updating the extension.
     * This callback is invoked with an [UnsupportedOperationException] in case the engine doesn't
     * have web extension support.
     */
    fun updateWebExtension(
        extension: WebExtension,
        onSuccess: ((WebExtension?) -> Unit) = { },
        onError: ((String, Throwable) -> Unit) = { _, _ -> },
    ): Unit = onError(
        extension.id,
        UnsupportedOperationException("Web extension support is not available in this engine"),
    )

    /**
     * Uninstalls the provided extension from this engine.
     *
     * @param ext the [WebExtension] to uninstall.
     * @param onSuccess (optional) callback invoked if the extension was uninstalled successfully.
     * @param onError (optional) callback invoked if there was an error uninstalling the extension.
     * This callback is invoked with an [UnsupportedOperationException] in case the engine doesn't
     * have web extension support.
     */
    fun uninstallWebExtension(
        ext: WebExtension,
        onSuccess: (() -> Unit) = { },
        onError: ((String, Throwable) -> Unit) = { _, _ -> },
    ): Unit = onError(ext.id, UnsupportedOperationException("Web extension support is not available in this engine"))

    /**
     * Lists the currently installed web extensions in this engine.
     *
     * @param onSuccess callback invoked with the list of of installed [WebExtension]s.
     * @param onError (optional) callback invoked if there was an error querying
     * the installed extensions. This callback is invoked with an [UnsupportedOperationException]
     * in case the engine doesn't have web extension support.
     */
    fun listInstalledWebExtensions(
        onSuccess: ((List<WebExtension>) -> Unit),
        onError: ((Throwable) -> Unit) = { },
    ): Unit = onError(UnsupportedOperationException("Web extension support is not available in this engine"))

    /**
     * Enables the provided [WebExtension]. If the extension is already enabled the [onSuccess]
     * callback will be invoked, but this method has no effect on the extension.
     *
     * @param extension the extension to enable.
     * @param source [EnableSource] to indicate why the extension is enabled.
     * @param onSuccess (optional) callback invoked with the enabled [WebExtension]
     * @param onError (optional) callback invoked if there was an error enabling
     * the extensions. This callback is invoked with an [UnsupportedOperationException]
     * in case the engine doesn't have web extension support.
     */
    fun enableWebExtension(
        extension: WebExtension,
        source: EnableSource = EnableSource.USER,
        onSuccess: ((WebExtension) -> Unit) = { },
        onError: ((Throwable) -> Unit) = { },
    ): Unit = onError(UnsupportedOperationException("Web extension support is not available in this engine"))

    /**
     * Add the provided [permissions] and [origins] to the [WebExtension].
     *
     * @param extensionId the id of the [WebExtension].
     * @param permissions [List] the list of permissions to be added to the [WebExtension].
     * @param origins [List] the list of origins to be added to the [WebExtension].
     * @param dataCollectionPermissions [List] the list of data collection permissions to be added to the
     * [WebExtension].
     * @param onSuccess (optional) callback invoked when permissions are added to the [WebExtension].
     * @param onError (optional) callback invoked if there was an error adding permissions to
     * the [WebExtension]. This callback is invoked with an [UnsupportedOperationException]
     * in case the engine doesn't have web extension support.
     */
    fun addOptionalPermissions(
        extensionId: String,
        permissions: List<String> = emptyList(),
        origins: List<String> = emptyList(),
        dataCollectionPermissions: List<String> = emptyList(),
        onSuccess: ((WebExtension) -> Unit) = { },
        onError: ((Throwable) -> Unit) = { },
    ): Unit = onError(UnsupportedOperationException("Web extension support is not available in this engine"))

    /**
     * Remove the provided [permissions] and [origins] from the [WebExtension].
     *
     * @param extensionId the id of the [WebExtension].
     * @param permissions [List] the list of permissions to be removed from the [WebExtension].
     * @param origins [List] the list of origins to be removed from the [WebExtension].
     * @param dataCollectionPermissions [List] the list of data collection permissions to be removed from the
     * [WebExtension].
     * @param onSuccess (optional) callback invoked when permissions are removed from the [WebExtension].
     * @param onError (optional) callback invoked if there was an error removing permissions from
     * the [WebExtension]. This callback is invoked with an [UnsupportedOperationException]
     * in case the engine doesn't have web extension support.
     */
    fun removeOptionalPermissions(
        extensionId: String,
        permissions: List<String> = emptyList(),
        origins: List<String> = emptyList(),
        dataCollectionPermissions: List<String> = emptyList(),
        onSuccess: ((WebExtension) -> Unit) = { },
        onError: ((Throwable) -> Unit) = { },
    ): Unit = onError(UnsupportedOperationException("Web extension support is not available in this engine"))

    /**
     * Disables the provided [WebExtension]. If the extension is already disabled the [onSuccess]
     * callback will be invoked, but this method has no effect on the extension.
     *
     * @param extension the extension to disable.
     * @param source [EnableSource] to indicate why the extension is disabled.
     * @param onSuccess (optional) callback invoked with the enabled [WebExtension]
     * @param onError (optional) callback invoked if there was an error disabling
     * the installed extensions. This callback is invoked with an [UnsupportedOperationException]
     * in case the engine doesn't have web extension support.
     */
    fun disableWebExtension(
        extension: WebExtension,
        source: EnableSource = EnableSource.USER,
        onSuccess: ((WebExtension) -> Unit),
        onError: ((Throwable) -> Unit) = { },
    ): Unit = onError(UnsupportedOperationException("Web extension support is not available in this engine"))

    /**
     * Registers a [WebExtensionDelegate] to be notified of engine events
     * related to web extensions
     *
     * @param webExtensionDelegate callback to be invoked for web extension events.
     */
    fun registerWebExtensionDelegate(
        webExtensionDelegate: WebExtensionDelegate,
    ): Unit = throw UnsupportedOperationException("Web extension support is not available in this engine")

    /**
     * Sets whether the provided [WebExtension] should be allowed to run in private browsing or not.
     *
     * @param extension the [WebExtension] instance to modify.
     * @param allowed true if this extension should be allowed to run in private browsing pages, false otherwise.
     * @param onSuccess (optional) callback invoked with modified [WebExtension] instance.
     * @param onError (optional) callback invoked if there was an error setting private browsing preference
     * the installed extensions. This callback is invoked with an [UnsupportedOperationException]
     * in case the engine doesn't have web extension support.
     */
    fun setAllowedInPrivateBrowsing(
        extension: WebExtension,
        allowed: Boolean,
        onSuccess: ((WebExtension) -> Unit) = { },
        onError: ((Throwable) -> Unit) = { },
    ): Unit = throw UnsupportedOperationException("Web extension support is not available in this engine")

    /**
     * Enable the extensions process spawning.
     */
    fun enableExtensionProcessSpawning(): Unit =
        throw UnsupportedOperationException("Enabling extension process spawning is not available in this engine")

    /**
     * Disable the extensions process spawning.
     */
    fun disableExtensionProcessSpawning(): Unit =
        throw UnsupportedOperationException("Disabling extension process spawning is not available in this engine")
}
