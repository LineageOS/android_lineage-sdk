/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.platform.internal.accessories

import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.android.server.ServiceThread
import lineageos.accessories.IAccessoriesCallback
import lineageos.accessories.IAccessoriesInterface
import lineageos.app.LineageContextConstants
import org.lineageos.platform.internal.LineageSystemService

class AccessoriesInterfaceService(
    private val context: Context,
) : LineageSystemService(context) {
    // Handler
    private val handlerThread = ServiceThread(
        LOG_TAG, Process.THREAD_PRIORITY_DEFAULT, false
    ).apply {
        start()
    }
    private val handler = Handler(handlerThread.looper)

    // Features
    private val accessoriesController by lazy { AccessoriesController(context, handler) }

    private val features by lazy {
        setOf(
            accessoriesController,
        ).filter { it.isSupported() }
    }

    private val service: IBinder = object : IAccessoriesInterface.Stub() {
        override fun getAccessories() = accessoriesController.getAccessories()

        override fun registerCallback(callback: IAccessoriesCallback) =
            accessoriesController.registerCallback(callback)

        override fun unregisterCallback(callback: IAccessoriesCallback) =
            accessoriesController.unregisterCallback(callback)

        /*
        override fun dump(fd: FileDescriptor, pw: PrintWriter, args: Array<String>) {
            context.enforceCallingOrSelfPermission(Manifest.permission.DUMP, LOG_TAG)

            pw.println()
            pw.println("LineageAccessories service status:")

            for (feature in features) {
                feature.dump(pw)
            }
        }
        */
    }

    override fun isCoreService() = false

    override fun onStart() {
        if (!context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.ACCESSORIES)) {
            Log.wtf(LOG_TAG, "Lineage Accessories service started by system server but feature xml "
                    + "not declared. Not publishing binder service!")
            return
        }

        if (features.isNotEmpty()) {
            publishBinderService(LineageContextConstants.LINEAGE_ACCESSORIES_INTERFACE, service)
        }
    }

    override fun onBootPhase(phase: Int) {
        if (phase != PHASE_BOOT_COMPLETED) {
            return
        }

        for (feature in features) {
            feature.start()
        }
    }

    override fun getFeatureDeclaration() = LineageContextConstants.Features.ACCESSORIES

    companion object {
        private const val LOG_TAG = "AccessoriesInterfaceService"
    }
}
