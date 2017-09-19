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

package org.lineageos.tests.hardware.unit;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import lineageos.app.LineageContextConstants;
import lineageos.hardware.LineageHardwareManager;
import lineageos.hardware.ILineageHardwareService;

/**
 * Created by adnan on 9/1/15.
 */
public class LineageHardwareManagerTest extends AndroidTestCase {
    private LineageHardwareManager mLineageHardwareManager;
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Only run this if we support hardware abstraction
        org.junit.Assume.assumeTrue(mContext.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.HARDWARE_ABSTRACTION));
        mLineageHardwareManager = LineageHardwareManager.getInstance(mContext);
    }

    @SmallTest
    public void testManagerExists() {
        assertNotNull(mLineageHardwareManager);
    }

    @SmallTest
    public void testManagerServiceIsAvailable() {
        ILineageHardwareService ilineageStatusBarManager = mLineageHardwareManager.getService();
        assertNotNull(ilineageStatusBarManager);
    }
}
