package org.lineageos.tests.telephony;

import android.telephony.SubscriptionManager;
import android.widget.Toast;
import org.lineageos.tests.TestActivity;

import lineageos.app.LineageTelephonyManager;

/**
 * Created by adnan on 8/6/15.
 */
public class LineageTelephonyTest extends TestActivity {
    @Override
    protected String tag() {
        return null;
    }

    @Override
    protected Test[] tests() {
        return mTests;
    }

    private Test[] mTests = new Test[] {
            new Test("test retreive list of subscription information") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    Toast.makeText(LineageTelephonyTest.this, "Test retrieve info "
                                    + lineageTelephonyManager.getSubInformation().toString(),
                            Toast.LENGTH_SHORT).show();
                }
            },
            new Test("test is default subscription active") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    Toast.makeText(LineageTelephonyTest.this, "Test default sub active "
                                    + lineageTelephonyManager.isSubActive(
                                    SubscriptionManager.getDefaultSubscriptionId()),
                            Toast.LENGTH_SHORT).show();
                }
            },
            new Test("test setSubState on default subscription") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    lineageTelephonyManager.setSubState(SubscriptionManager.getDefaultSubscriptionId(), true);
                }
            },
            new Test("test is data enabled on default sub") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    Toast.makeText(LineageTelephonyTest.this, "Test default sub data "
                                    + lineageTelephonyManager.isDataConnectionSelectedOnSub(
                                    SubscriptionManager.getDefaultSubscriptionId()),
                            Toast.LENGTH_SHORT).show();
                }
            },
            new Test("test is data enabled") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    Toast.makeText(LineageTelephonyTest.this, "Test data enabled "
                                    + lineageTelephonyManager.isDataConnectionEnabled(),
                            Toast.LENGTH_SHORT).show();
                }
            },
            new Test("test setDataConnectionState") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    lineageTelephonyManager.setDataConnectionState(true);
                }
            },
            new Test("test setDataConnectionSelectedOnSub") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    lineageTelephonyManager.setDataConnectionSelectedOnSub(
                            SubscriptionManager.getDefaultSubscriptionId());
                }
            },
            new Test("test setDefaultPhoneSub") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    lineageTelephonyManager.setDefaultPhoneSub(
                            SubscriptionManager.getDefaultSubscriptionId());
                }
            },
            new Test("test setDefaultSmsSub") {
                public void run() {
                    LineageTelephonyManager lineageTelephonyManager =
                            LineageTelephonyManager.getInstance(LineageTelephonyTest.this);
                    lineageTelephonyManager.setDefaultSmsSub(
                            SubscriptionManager.getDefaultSubscriptionId());
                }
            },
    };
}
