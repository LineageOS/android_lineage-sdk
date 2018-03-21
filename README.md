# LineageOS Platform SDK

The Platform SDK provides a set of APIs that give you easy access to a variety of different features within LineageOS. The SDK exposes APIs and system level framework access in the Android framework that aren't available in any other distribution.

## Setup

You can either [download](https://github.com/LineageOS/android_prebuilts_lineage-sdk/tree/master/current) from prebuilts hosted on github or pull directly via Gradle.


### Building against release artifacts

Our stable releases are mirrored in [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22platform.sdk%22), and you can fetch the current release by setting your `build.gradle` dependencies to

```gradle
dependencies {
    compile 'org.lineageos:platform.sdk:5.+'
}
```

### Building against development snapshots

Within `build.gradle` make sure your `repositories` list sonatype OSS repos for snapshots

```gradle
repositories {
    maven {
        url "https://oss.sonatype.org/content/repositories/snapshots/"
    }
}
```

You can target the `future` or `development` branch by setting your `dependencies` for `6.0-SNAPSHOT`

```gradle
dependencies {
    compile 'org.lineageos:platform.sdk:6.0-SNAPSHOT'
}
```

### Wiki

For further inquiries regarding this project, please reference the [wiki](https://wiki.lineageos.org/sdk).
