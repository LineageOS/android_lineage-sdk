package org.lineageos.platform.internal;

import com.android.server.SystemService;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;

import java.util.List;

import lineageos.app.LineageContextConstants;
import lineageos.platform.Manifest;

import lineageos.app.ContainerManager;
import lineageos.app.IContainerManager;

/**
 * Internal service which manages interactions with the phone and data connection
 *
 * @hide
 */
public class ContainerManagerService extends LineageSystemService {
    private static final String TAG = "ContainerManagerSrv";
    private static boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private Context mContext;
    private final IBinder mService = new IContainerManager.Stub() {

        @Override
        public void sayHelloTo(String msg) {
            enforceContainerSayHelloPermission();
            ContainerManagerService.this.sayHelloTo(msg);
        }
    };

    public ContainerManagerService(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public String getFeatureDeclaration() {
        return LineageContextConstants.Features.CONTAINERS;
    }

    @Override
    public void onStart() {
        if (localLOGD) {
            Log.d(TAG, "Container Manager Service start: " + this);
        }
        publishBinderService(LineageContextConstants.LINEAGE_CONTAINER_MANAGER_SERVICE, mService);
    }

    private void sayHelloTo(String msg) {
        Log.e(TAG, "ContainerManagerService: Say hello to " + msg + "!");
    }

    private void enforceContainerSayHelloPermission() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.CONTAINER_SAY_HELLO,
                "ContainerManagerService");
    }
}
