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

package org.lineageos.tests.providers;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import lineageos.providers.LineageSettings;

public class LineageSettingsTest extends AndroidTestCase{
    private ContentResolver mContentResolver;
    private LineageSettingsTestObserver mTestObserver;

    private static boolean sIsOnChangedCalled = false;
    private static Uri sExpectedUriChange = null;

    @Override
    public void setUp() {
        mContentResolver = getContext().getContentResolver();
        mTestObserver = new LineageSettingsTestObserver(null);
    }

    @Override
    public void tearDown() {
        mContentResolver.unregisterContentObserver(mTestObserver);
    }

    @MediumTest
    public void testPutAndGetSystemString() {
        final String key = LineageSettings.System.__MAGICAL_TEST_PASSING_ENABLER;

        // put
        final String expectedValue = "1";
        boolean isPutSuccessful = LineageSettings.System.putString(mContentResolver, key, expectedValue);
        assertTrue(isPutSuccessful);

        // get
        String actualValue = LineageSettings.System.getString(mContentResolver, key);
        assertEquals(expectedValue, actualValue);

        // setup observer
        sIsOnChangedCalled = false;
        sExpectedUriChange = LineageSettings.System.getUriFor(key);
        mContentResolver.registerContentObserver(sExpectedUriChange, false, mTestObserver,
                UserHandle.USER_ALL);

        // replace
        final String expectedReplaceValue = "0";
        isPutSuccessful = LineageSettings.System.putString(mContentResolver, key, expectedReplaceValue);
        assertTrue(isPutSuccessful);

        // get
        actualValue = LineageSettings.System.getString(mContentResolver, key);
        assertEquals(expectedReplaceValue, actualValue);

        // delete to clean up
        int rowsAffected = mContentResolver.delete(LineageSettings.System.CONTENT_URI,
                Settings.NameValueTable.NAME + " = ?", new String[]{ key });
        assertEquals(1, rowsAffected);

        if (!sIsOnChangedCalled) {
            fail("On change was never called or was called with the wrong uri");
        }
    }

    @MediumTest
    public void testPutAndGetSecureString() {
        /* TODO: FIXME
        final String key = LineageSettings.Secure.__MAGICAL_TEST_PASSING_ENABLER;

        // put
        final String expectedValue = "0";
        boolean isPutSuccessful = LineageSettings.Secure.putString(mContentResolver, key, expectedValue);
        assertTrue(isPutSuccessful);

        // get
        String actualValue = LineageSettings.Secure.getString(mContentResolver, key);
        assertEquals(expectedValue, actualValue);

        // setup observer
        sIsOnChangedCalled = false;
        sExpectedUriChange = LineageSettings.Secure.getUriFor(key);
        mContentResolver.registerContentObserver(sExpectedUriChange, false, mTestObserver,
                UserHandle.USER_ALL);

        // replace
        final String expectedReplaceValue = "1";
        isPutSuccessful = LineageSettings.Secure.putString(mContentResolver, key, expectedReplaceValue);
        assertTrue(isPutSuccessful);

        // get
        actualValue = LineageSettings.Secure.getString(mContentResolver, key);
        assertEquals(expectedReplaceValue, actualValue);

        // delete to clean up
        int rowsAffected = mContentResolver.delete(LineageSettings.Secure.CONTENT_URI,
                Settings.NameValueTable.NAME + " = ?", new String[]{ key });
        assertEquals(1, rowsAffected);

        if (!sIsOnChangedCalled) {
            fail("On change was never called or was called with the wrong uri");
        } */
    }

    @MediumTest
    public void testPutAndGetGlobalString() {
        final String key = "key";

        // put
        final String expectedValue = "globalTestValue1";
        boolean isPutSuccessful = LineageSettings.Global.putString(mContentResolver, key, expectedValue);
        assertTrue(isPutSuccessful);

        // get
        String actualValue = LineageSettings.Global.getString(mContentResolver, key);
        assertEquals(expectedValue, actualValue);

        // setup observer
        sIsOnChangedCalled = false;
        sExpectedUriChange = LineageSettings.Global.getUriFor(key);
        mContentResolver.registerContentObserver(sExpectedUriChange, false, mTestObserver,
                UserHandle.USER_OWNER);

        // replace
        final String expectedReplaceValue = "globalTestValue2";
        isPutSuccessful = LineageSettings.Global.putString(mContentResolver, key, expectedReplaceValue);
        assertTrue(isPutSuccessful);

        // get
        actualValue = LineageSettings.Global.getString(mContentResolver, key);
        assertEquals(expectedReplaceValue, actualValue);

        // delete to clean up
        int rowsAffected = mContentResolver.delete(LineageSettings.Global.CONTENT_URI,
                Settings.NameValueTable.NAME + " = ?", new String[]{ key });
        assertEquals(1, rowsAffected);

        if (!sIsOnChangedCalled) {
            fail("On change was never called or was called with the wrong uri");
        }
    }

    private class LineageSettingsTestObserver extends ContentObserver {

        public LineageSettingsTestObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (sExpectedUriChange.equals(uri)) {
                sIsOnChangedCalled = true;
            }
        }
    }

    // TODO Add tests for other users
}
