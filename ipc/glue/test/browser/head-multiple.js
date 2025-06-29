/* Any copyright is dedicated to the Public Domain.
 * http://creativecommons.org/publicdomain/zero/1.0/ */

"use strict";

/* import-globals-from head.js */

async function runTest({
  expectUtility = false,
  expectRDD = false,
  expectContent = false,
  expectError = false,
}) {
  info(`Running tests with decoding from somewhere`);
  info(`  expectUtility: ${expectUtility}`);
  info(`  expectRDD: ${expectRDD}`);
  info(`  expectContent: ${expectContent}`);

  const platform = Services.appinfo.OS;

  for (let { src, expectations } of audioTestData()) {
    if (!(platform in expectations)) {
      info(`Skipping ${src} for ${platform}`);
      continue;
    }

    const expectation = expectations[platform];

    info(`Add media tabs: ${src}`);
    let tabs = [await addMediaTab(src), await addMediaTab(src)];
    let playback = [];

    info("Play tabs");
    for (let tab of tabs) {
      playback.push(
        play(
          tab,
          expectUtility && !expectContent && !expectError
            ? expectation.process
            : "RDD",
          expectation.decoder,
          expectContent,
          false, // expectJava
          expectError
        )
      );
    }

    info("Wait all playback");
    await Promise.all(playback);

    let allstop = [];
    info("Stop tabs");
    for (let tab of tabs) {
      allstop.push(stop(tab));
    }

    info("Wait all stop");
    await Promise.all(allstop);

    let remove = [];
    info("Remove tabs");
    for (let tab of tabs) {
      remove.push(BrowserTestUtils.removeTab(tab));
    }

    info("Wait all tabs to be removed");
    await Promise.all(remove);
  }
}
