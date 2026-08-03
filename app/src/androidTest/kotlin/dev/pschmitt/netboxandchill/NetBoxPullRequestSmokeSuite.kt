package dev.pschmitt.netboxandchill

import dev.pschmitt.netboxandchill.ui.generic.GenericCreateFieldInputTest
import dev.pschmitt.netboxandchill.ui.generic.GenericDetailExtractedComponentsTest
import dev.pschmitt.netboxandchill.ui.settings.SettingsCategoryContentTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Keeps the PR gate short while exercising both extracted UI components and app routing. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    NetBoxE2eSmokeTest::class,
    GenericCreateFieldInputTest::class,
    GenericDetailExtractedComponentsTest::class,
    SettingsCategoryContentTest::class,
)
class NetBoxPullRequestSmokeSuite
