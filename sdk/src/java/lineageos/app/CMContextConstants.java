/**
 * Copyright (c) 2015, The CyanogenMod Project
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
public final class CMContextConstants {

    /**
     * @hide
     */
    private CMContextConstants() {
        // Empty constructor
    }

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.app.CMStatusBarManager} for informing the user of
     * background events.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.app.CMStatusBarManager
     */
    public static final String CM_STATUS_BAR_SERVICE = "cmstatusbar";

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
    public static final String CM_PROFILE_SERVICE = "profile";

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.app.PartnerInterface} interact with system settings.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.app.PartnerInterface
     *
     * @hide
     */
    public static final String CM_PARTNER_INTERFACE = "cmpartnerinterface";

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.app.CMTelephonyManager} to manage the phone and
     * data connection.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.app.CMTelephonyManager
     *
     * @hide
     */
    public static final String CM_TELEPHONY_MANAGER_SERVICE = "cmtelephonymanager";

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.hardware.CMHardwareManager} to manage the extended
     * hardware features of the device.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.hardware.CMHardwareManager
     *
     * @hide
     */
    public static final String CM_HARDWARE_SERVICE = "cmhardware";

    /**
     * @hide
     */
    public static final String CM_APP_SUGGEST_SERVICE = "cmappsuggest";

    /**
     * Control device power profile and characteristics.
     *
     * @hide
     */
    public static final String CM_PERFORMANCE_SERVICE = "cmperformance";

    /**
     * Controls changing and applying themes
     *
     * @hide
     */
    public static final String CM_THEME_SERVICE = "cmthemes";

    /**
     * Manages composed icons
     *
     * @hide
     */
    public static final String CM_ICON_CACHE_SERVICE = "cmiconcache";

    /**
     * @hide
     */
    public static final String CM_LIVE_LOCK_SCREEN_SERVICE = "cmlivelockscreen";

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link lineageos.weather.CMWeatherManager} to manage the weather service
     * settings and request weather updates
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.weather.CMWeatherManager
     *
     * @hide
     */
    public static final String CM_WEATHER_SERVICE = "cmweather";

    /**
     * Manages display color adjustments
     *
     * @hide
     */
    public static final String CM_LIVEDISPLAY_SERVICE = "cmlivedisplay";


    /**
     * Manages enhanced audio functionality
     *
     * @hide
     */
    public static final String CM_AUDIO_SERVICE = "cmaudio";

    /**
     * Features supported by the LineageSDK.
     */
    public static class Features {
        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the hardware abstraction
         * framework service utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String HARDWARE_ABSTRACTION = "org.lineageos.hardware";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the cm status bar service
         * utilzed by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String STATUSBAR = "org.lineageos.statusbar";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the cm profiles service
         * utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String PROFILES = "org.lineageos.profiles";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the cm app suggest service
         * utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String APP_SUGGEST = "org.lineageos.appsuggest";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the cm telephony service
         * utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String TELEPHONY = "org.lineageos.telephony";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the cm theme service
         * utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String THEMES = "org.lineageos.theme";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the cm performance service
         * utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String PERFORMANCE = "org.lineageos.performance";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the cm partner service
         * utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String PARTNER = "org.lineageos.partner";

        /*
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the Live lock screen
         * feature.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String LIVE_LOCK_SCREEN = "org.lineageos.livelockscreen";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the cm weather weather
         * service utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String WEATHER_SERVICES = "org.lineageos.weather";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the LiveDisplay service
         * utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String LIVEDISPLAY = "org.lineageos.livedisplay";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the CM audio extensions
         * utilized by the lineagesdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String AUDIO = "org.lineageos.audio";
    }
}
