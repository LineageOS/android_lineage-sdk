package lineageos.app;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Slog;

import java.util.List;

import lineageos.app.LineageContextConstants;

/**
 * The ContainerManager allows you to ...
 * TODO
 *
 * <p>
 * To get the instance of this class, utilize
 * ContainerManager#getInstance(Context context)
 */
public class ContainerManager {

    /**
     * Subscription ID used to set the default Phone and SMS to "ask every time".
     */
    public static final int ASK_FOR_SUBSCRIPTION_ID = 0;

    private static final String TAG = "ContainerManager";
    private static boolean localLOGD = Log.isLoggable(TAG, Log.DEBUG);

    private static IContainerManager sService;
    private static ContainerManager sContainerManagerInstance;
    private Context mContext;

    private ContainerManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }
        sService = getService();

        if (context.getPackageManager().hasSystemFeature(LineageContextConstants.Features.CONTAINERS)
                && sService == null) {
            Log.wtf(TAG, "Unable to get ContainerManagerService. " +
                    "The service either crashed, was not started, or the interface has been " +
                    "called too early in SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link cyanogenmod.app.ContainerManager}
     *
     * @return {@link cyanogenmod.app.ContainerManager}
     */
    public static ContainerManager getInstance(Context context) {
        if (sContainerManagerInstance == null) {
            sContainerManagerInstance = new ContainerManager(context);
        }
        return sContainerManagerInstance;
    }

    /** @hide */
    public IContainerManager getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(LineageContextConstants.LINEAGE_CONTAINER_MANAGER_SERVICE);
        if (b != null) {
            sService = IContainerManager.Stub.asInterface(b);
            return sService;
        }
        return null;
    }

    public void sayHelloTo(String msg) {
        if (sService == null) {
            Log.w(TAG, "not connected to ContainerManager");
            return;
        }

        if (localLOGD) {
            String pkg = mContext.getPackageName();
            Log.v(TAG, pkg + " ContainerManager: sayHelloTo " + msg + " called!");
        }
        try {
            sService.sayHelloTo(msg);
        } catch (RemoteException e) {
            Slog.w(TAG, "warning: no container manager service");
        }
    }
}
