/**
 * Copyright (C) 2015, The CyanogenMod Project
 *               2017-2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package lineageos.app;

import android.annotation.SdkConstant;

/**
 * @hide
 * TODO: We need to somehow make these managers accessible via getSystemService
 */
public final class LineageContextConstants {

    /**
     * @hide
     */
    private LineageContextConstants() {
        // Empty constructor
    }

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.app.ProfileManager} for informing the user of
     * background events.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.app.ProfileManager
     *
     * @hide
     */
    public static final String LINEAGE_PROFILE_SERVICE = "profile";

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.hardware.LineageHardwareManager} to manage the extended
     * hardware features of the device.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.hardware.LineageHardwareManager
     *
     * @hide
     */
    public static final String LINEAGE_HARDWARE_SERVICE = "lineagehardware";

    /**
     * Control device power profile and characteristics.
     *
     * @hide
     */
    public static final String LINEAGE_PERFORMANCE_SERVICE = "lineageperformance";

    /**
     * Manages display color adjustments
     *
     * @hide
     */
    public static final String LINEAGE_LIVEDISPLAY_SERVICE = "lineagelivedisplay";

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.trust.TrustInterface} to access the Trust interface.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.trust.TrustInterface
     *
     * @hide
     */
    public static final String LINEAGE_TRUST_INTERFACE = "lineagetrust";

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.health.HealthInterface} to access the Health interface.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.health.HealthInterface
     *
     * @hide
     */
    public static final String LINEAGE_HEALTH_INTERFACE = "lineagehealth";

    /**
     * Update power menu (GlobalActions)
     *
     * @hide
     */
    public static final String LINEAGE_GLOBAL_ACTIONS_SERVICE = "lineageglobalactions";

    /**
     * Features supported by the Lineage SDK.
     */
    public static class Features {
        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the hardware abstraction
         * framework service utilized by the lineage sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String HARDWARE_ABSTRACTION = "org.lineageos.hardware";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the lineage profiles service
         * utilized by the lineage sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String PROFILES = "org.lineageos.profiles";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the lineage performance service
         * utilized by the lineage sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String PERFORMANCE = "org.lineageos.performance";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the LiveDisplay service
         * utilized by the lineage sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String LIVEDISPLAY = "org.lineageos.livedisplay";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the Lineage audio extensions
         * utilized by the lineage sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String AUDIO = "org.lineageos.audio";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the lineage trust service
         * utilized by the lineage sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String TRUST = "org.lineageos.trust";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the lineage settings service
         * utilized by the lineage sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String SETTINGS = "org.lineageos.settings";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the lineage globalactions
         * service utilized by the lineage sdk and LineageParts.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String GLOBAL_ACTIONS = "org.lineageos.globalactions";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the lineage health
         * service utilized by the lineage sdk and LineageParts.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String HEALTH = "org.lineageos.health";
    }
}
