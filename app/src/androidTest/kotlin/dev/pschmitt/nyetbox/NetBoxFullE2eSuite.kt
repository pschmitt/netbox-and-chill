package dev.pschmitt.nyetbox

import dev.pschmitt.nyetbox.ui.generic.GenericCreateFieldInputTest
import dev.pschmitt.nyetbox.ui.generic.GenericDetailExtractedComponentsTest
import dev.pschmitt.nyetbox.ui.settings.SettingsCategoryContentTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/** Manual release-candidate suite: the full journey plus extracted-component regressions. */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    NetBoxE2eTest::class,
    GenericCreateFieldInputTest::class,
    GenericDetailExtractedComponentsTest::class,
    SettingsCategoryContentTest::class,
)
class NetBoxFullE2eSuite
