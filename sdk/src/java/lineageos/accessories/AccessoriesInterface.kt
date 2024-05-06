/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package lineageos.accessories

import android.content.Context
import android.os.ServiceManager
import android.util.Log
import lineageos.app.LineageContextConstants

class AccessoriesInterface private constructor(
    context: Context,
) {
    private val context = context.applicationContext ?: context

    init {
        service = getService()

        if (context.getPackageManager().hasSystemFeature(
                LineageContextConstants.Features.ACCESSORIES) && service == null) {
            throw RuntimeException("Unable to get AccessoriesInterfaceService. The service" +
                    " either crashed, was not started, or the interface has been called too early" +
                    " in SystemServer init")
        }
    }

    fun checkService(): Boolean {
        if (service == null) {
            Log.w(LOG_TAG, "not connected to AccessoriesInterfaceService")
            return false
        }

        return true
    }

    companion object {
        private const val LOG_TAG = "AccessoriesInterface"

        private var service: IAccessoriesInterface? = null
        private var instance: AccessoriesInterface? = null

        @Synchronized fun getInstance(context: Context) =
            instance ?: AccessoriesInterface(context).also {
                instance = it
            }

        fun getService(): IAccessoriesInterface? {
            service?.let {
                return it
            }

            val b = ServiceManager.getService(LineageContextConstants.LINEAGE_ACCESSORIES_INTERFACE)
            service = IAccessoriesInterface.Stub.asInterface(b)

            if (service == null) {
                Log.e(LOG_TAG, "null accessories service, SAD!")
                return null
            }

            return service
        }
    }
}
