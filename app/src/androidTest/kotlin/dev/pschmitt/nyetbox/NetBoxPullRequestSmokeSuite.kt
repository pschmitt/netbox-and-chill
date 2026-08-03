package dev.pschmitt.nyetbox

import dev.pschmitt.nyetbox.ui.generic.GenericCreateFieldInputTest
import dev.pschmitt.nyetbox.ui.generic.GenericDetailExtractedComponentsTest
import dev.pschmitt.nyetbox.ui.settings.SettingsCategoryContentTest
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
