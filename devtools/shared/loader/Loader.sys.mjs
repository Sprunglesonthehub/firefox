/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/**
 * Manages the base loader (base-loader.sys.mjs) instance used to load the developer tools.
 */

import {
  Loader,
  Require,
  resolveURI,
  unload,
} from "resource://devtools/shared/loader/base-loader.sys.mjs";
import { requireRawId } from "resource://devtools/shared/loader/loader-plugin-raw.sys.mjs";

export const DEFAULT_SANDBOX_NAME = "DevTools (Module loader)";

var gNextLoaderID = 0;

/**
 * The main devtools API. The standard instance of this loader is exported as
 * |loader| below, but if a fresh copy of the loader is needed, then a new
 * one can also be created.
 *
 * The two following boolean flags are used to control the sandboxes into
 * which the modules are loaded.
 * @param freshCompartment boolean
 *        If true, the modules will be forced to be loaded in a distinct
 *        compartment. It is typically used to load the modules in a distinct
 *        system compartment, different from the main one, which is shared by
 *        all ESMs, XPCOMs and modules loaded with this flag set to true.
 *        We use this in order to debug modules loaded in this shared system
 *        compartment. The debugger actor has to be running in a distinct
 *        compartment than the context it is debugging.
 * @param useDevToolsLoaderGlobal boolean
 *        If true, the loader will reuse the current global to load other
 *        modules instead of creating a sandbox with custom options. Cannot be
 *        used with freshCompartment.
 */
export function DevToolsLoader({
  freshCompartment = false,
  useDevToolsLoaderGlobal = false,
} = {}) {
  if (useDevToolsLoaderGlobal && freshCompartment) {
    throw new Error(
      "Loader cannot use freshCompartment if useDevToolsLoaderGlobal is true"
    );
  }

  const paths = {
    // This resource:// URI is only registered when running DAMP tests.
    // This is done by: testing/talos/talos/tests/devtools/addon/api.js
    "damp-test": "resource://damp-test/content",
    // ⚠ DISCUSSION ON DEV-DEVELOPER-TOOLS REQUIRED BEFORE MODIFYING ⚠
    devtools: "resource://devtools",
    // ⚠ DISCUSSION ON DEV-DEVELOPER-TOOLS REQUIRED BEFORE MODIFYING ⚠
    // Allow access to xpcshell test items from the loader.
    "xpcshell-test": "resource://test",

    // ⚠ DISCUSSION ON DEV-DEVELOPER-TOOLS REQUIRED BEFORE MODIFYING ⚠
    // Allow access to locale data using paths closer to what is
    // used in the source tree.
    "devtools/client/locales": "chrome://devtools/locale",
    "devtools/shared/locales": "chrome://devtools-shared/locale",
    "devtools/startup/locales": "chrome://devtools-startup/locale",
    "toolkit/locales": "chrome://global/locale",
  };

  // In case the Loader ESM is loaded in DevTools global,
  // also reuse this global for all CommonJS modules.
  const sharedGlobal =
    useDevToolsLoaderGlobal ||
    // eslint-disable-next-line mozilla/reject-globalThis-modification
    Cu.getRealmLocation(globalThis) == "DevTools global"
      ? Cu.getGlobalForObject({})
      : undefined;
  this.loader = new Loader({
    paths,
    sharedGlobal,
    freshCompartment,
    sandboxName: useDevToolsLoaderGlobal
      ? "DevTools (Server Module Loader)"
      : DEFAULT_SANDBOX_NAME,
    // Make sure `define` function exists. JSON Viewer needs modules in AMD
    // format, as it currently uses RequireJS from a content document and
    // can't access our usual loaders. So, any modules shared with the JSON
    // Viewer should include a define wrapper:
    //
    //   // Make this available to both AMD and CJS environments
    //   define(function(require, exports, module) {
    //     ... code ...
    //   });
    //
    // Bug 1248830 will work out a better plan here for our content module
    // loading needs, especially as we head towards devtools.html.
    supportAMDModules: true,
    requireHook: (id, require) => {
      if (id.startsWith("raw!") || id.startsWith("theme-loader!")) {
        return requireRawId(id, require);
      }
      return require(id);
    },
  });

  this.require = Require(this.loader, { id: "devtools" });

  // Various globals are available from ESM, but not from sandboxes,
  // inject them into the globals list.
  // Changes here should be mirrored to devtools/.eslintrc.
  const injectedGlobals = {
    BrowsingContext,
    CanonicalBrowsingContext,
    ChromeWorker,
    console,
    DebuggerNotificationObserver,
    DOMPoint,
    DOMQuad,
    DOMRect,
    fetch,
    Glean,
    HeapSnapshot,
    IOUtils,
    L10nRegistry,
    Localization,
    NamedNodeMap,
    NodeFilter,
    PathUtils,
    Services,
    StructuredCloneHolder,
    WebExtensionPolicy,
    WebSocket,
    WindowGlobalChild,
    WindowGlobalParent,
  };
  for (const name in injectedGlobals) {
    this.loader.globals[name] = injectedGlobals[name];
  }

  // Fetch custom pseudo modules and globals
  const { modules, globals } = this.require(
    "resource://devtools/shared/loader/builtin-modules.js"
  );

  // Register custom pseudo modules to the current loader instance
  for (const id in modules) {
    const uri = resolveURI(id, this.loader.mapping);
    this.loader.modules[uri] = {
      get exports() {
        return modules[id];
      },
    };
  }

  // Register custom globals to the current loader instance
  Object.defineProperties(
    this.loader.sharedGlobal,
    Object.getOwnPropertyDescriptors(globals)
  );

  // Define the loader id for these two usecases:
  // * access via the ESM (this.id)
  // let { loader } = ChromeUtils.importESModule("resource://devtools/shared/loader/Loader.sys.mjs");
  // loader.id
  this.id = gNextLoaderID++;
  // * access via module's `loader` global
  // loader.id
  globals.loader.id = this.id;

  // Expose lazy helpers on `loader`
  // ie. when you use it like that from a ESM:
  // let { loader } = ChromeUtils.importESModule("resource://devtools/shared/loader/Loader.sys.mjs");
  // loader.lazyGetter(...);
  this.lazyGetter = globals.loader.lazyGetter;
  this.lazyServiceGetter = globals.loader.lazyServiceGetter;
  this.lazyRequireGetter = globals.loader.lazyRequireGetter;
}

DevToolsLoader.prototype = {
  destroy(reason = "shutdown") {
    unload(this.loader, reason);
    delete this.loader;
  },

  /**
   * Return true if |id| refers to something requiring help from a
   * loader plugin.
   */
  isLoaderPluginId(id) {
    return id.startsWith("raw!");
  },
};

// Export the standard instance of DevToolsLoader used by the tools.
export var loader = new DevToolsLoader();

export var require = loader.require;
