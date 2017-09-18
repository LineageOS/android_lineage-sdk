## Lineage Platform SDK Tests
The tests package contains both functional manual tests as well as unit
tests which can be ran utilizing the InstrumentationTestRunner from android.

To run the tests (on a live device):
  
  ```adb shell am instrument -w org.lineageos.tests/android.support.test.runner.AndroidJUnitRunner```
