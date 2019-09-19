## Lineage Settings Provider Tests
The tests package contains coverage for the Lineage Settings provider as well as
its public interfaces.

To run the tests (on a live device), build and install LineageSettingsProviderTests.apk
and then run:

```adb shell am instrument org.lineageos.lineagesettings.tests/androidx.test.runner.AndroidJUnitRunner```

Note: we don't use -w to wait for the results because some of the tests involve creating
and removing a guest account which causes adb connections to get reset.

View the results with:

```adb logcat | grep TestRunner```

End of the output should read something like:

```09-20 16:40:52.879  4146  4165 I TestRunner: run finished: 30 tests, 0 failed, 0 ignored```
