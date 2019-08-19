/*
 * Copyright (c) 2019 The LineageOS Project
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

package org.lineageos.platform.internal.health;

import android.annotation.NonNull;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Environment;
import android.os.UserHandle;

import java.io.File;

/**
 * A context wrapper that mocks packageName and
 * data directory.
 */
class AltContext extends ContextWrapper {
    private final String mCustomName;

    AltContext(@NonNull Context base, @NonNull String customName) {
        super(base);
        mCustomName = customName;
    }

    @Override
    public String getPackageName() {
        return mCustomName;
    }

    @Override
    public String getBasePackageName() {
        return super.getBasePackageName();
    }

    @Override
    public File getDataDir() {
        return Environment.getDataSystemCeDirectory(0);
    }
}
