/*****************************************************************************
 * VLCApplication.java
 *****************************************************************************
 * Copyright © 2010-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.util.SimpleArrayMap;
import android.util.Log;

import org.videolan.libvlc.Dialog;
import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.gui.DialogActivity;
import org.videolan.vlc.gui.dialogs.VlcProgressDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.BitmapCache;
import org.videolan.vlc.gui.helpers.NotificationHelper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.VLCInstance;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VLCApplication extends Application {
    public final static String TAG = "VLC/VLCApplication";

    public final static String ACTION_MEDIALIBRARY_READY = "VLC/VLCApplication";
    private static volatile VLCApplication instance;

    public final static String SLEEP_INTENT = Strings.buildPkgString("SleepIntent");

    public static Calendar sPlayerSleepTime = null;
    private static boolean sTV;
    private static SharedPreferences mSettings;

    private static SimpleArrayMap<String, WeakReference<Object>> sDataMap = new SimpleArrayMap<>();

    /* Up to 2 threads maximum, inactive threads are killed after 2 seconds */
    private final int maxThreads = Math.max(AndroidUtil.isJellyBeanMR1OrLater ? Runtime.getRuntime().availableProcessors() : 2, 1);
    public static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setPriority(Process.THREAD_PRIORITY_DEFAULT+Process.THREAD_PRIORITY_LESS_FAVORABLE);
            return thread;
        }
    };
    private final ThreadPoolExecutor mThreadPool = new ThreadPoolExecutor(Math.min(2, maxThreads), maxThreads, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), THREAD_FACTORY);
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private static int sDialogCounter = 0;

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        setLocale();

        runBackground(new Runnable() {
            @Override
            public void run() {
                if (AndroidUtil.isOOrLater)
                    NotificationHelper.createNotificationChannels();
                // Prepare cache folder constants
                AudioUtil.prepareCacheFolder(instance);

                sTV = AndroidDevices.isAndroidTv || !AndroidDevices.hasTsp;

                if (!VLCInstance.testCompatibleCPU(instance))
                    return;
                Dialog.setCallbacks(VLCInstance.get(), mDialogCallbacks);

                // Disable remote control receiver on Fire TV.
                if (!AndroidDevices.hasTsp)
                    AndroidDevices.setRemoteControlReceiverEnabled(false);
            }
        });
        if (sActivityCbListener != null)
            registerActivityLifecycleCallbacks(sActivityCbListener);
        else
            ExternalMonitor.register(instance);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setLocale();
    }

    /**
     * Called when the overall system is running low on memory
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "System is running low on memory");

        BitmapCache.getInstance().clear();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.w(TAG, "onTrimMemory, level: "+level);

        BitmapCache.getInstance().clear();
    }

    /**
     * @return the main context of the Application
     */
    public static Context getAppContext() {
        return instance;
    }

    /**
     * @return the main resources from the Application
     */
    public static Resources getAppResources()
    {
        return instance.getResources();
    }

    public static boolean showTvUi() {
        return sTV || (mSettings != null && mSettings.getBoolean("tv_ui", false));
    }

    public static void runBackground(Runnable runnable) {
        if (Looper.myLooper() != Looper.getMainLooper())
            runnable.run();
        else
            instance.mThreadPool.execute(runnable);
    }

    public static void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper())
            runnable.run();
        else
            instance.mHandler.post(runnable);
    }

    public static boolean removeTask(Runnable runnable) {
        return instance.mThreadPool.remove(runnable);
    }

    public static void storeData(String key, Object data) {
        sDataMap.put(key, new WeakReference<>(data));
    }

    public static Object getData(String key) {
        final WeakReference wr = sDataMap.remove(key);
        return wr != null ? wr.get() : null;
    }

    public static boolean hasData(String key) {
        return sDataMap.containsKey(key);
    }

    public static void clearData() {
        sDataMap.clear();
    }

    Dialog.Callbacks mDialogCallbacks = new Dialog.Callbacks() {
        @Override
        public void onDisplay(Dialog.ErrorMessage dialog) {
            Log.w(TAG, "ErrorMessage "+dialog.getText());
        }

        @Override
        public void onDisplay(Dialog.LoginDialog dialog) {
            String key = DialogActivity.KEY_LOGIN + sDialogCounter++;
            fireDialog(dialog, key);
        }

        @Override
        public void onDisplay(Dialog.QuestionDialog dialog) {
            String key = DialogActivity.KEY_QUESTION + sDialogCounter++;
            fireDialog(dialog, key);
        }

        @Override
        public void onDisplay(Dialog.ProgressDialog dialog) {
            String key = DialogActivity.KEY_PROGRESS + sDialogCounter++;
            fireDialog(dialog, key);
        }

        @Override
        public void onCanceled(Dialog dialog) {
            if (dialog != null && dialog.getContext() != null)
                ((DialogFragment)dialog.getContext()).dismiss();
        }

        @Override
        public void onProgressUpdate(Dialog.ProgressDialog dialog) {
            VlcProgressDialog vlcProgressDialog = (VlcProgressDialog) dialog.getContext();
            if (vlcProgressDialog != null && vlcProgressDialog.isVisible())
                vlcProgressDialog.updateProgress();
        }
    };

    private void fireDialog(Dialog dialog, String key) {
        storeData(key, dialog);
        startActivity(new Intent(instance, DialogActivity.class).setAction(key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static Medialibrary getMLInstance() {
        return Medialibrary.getInstance();
    }

    public static void setLocale(){
        // Are we using advanced debugging - locale?
        String p = mSettings.getString("set_locale", "");
        if (!p.equals("")) {
            Locale locale;
            // workaround due to region code
            if(p.equals("zh-TW")) {
                locale = Locale.TRADITIONAL_CHINESE;
            } else if(p.startsWith("zh")) {
                locale = Locale.CHINA;
            } else if(p.equals("pt-BR")) {
                locale = new Locale("pt", "BR");
            } else if(p.equals("bn-IN") || p.startsWith("bn")) {
                locale = new Locale("bn", "IN");
            } else {
                /**
                 * Avoid a crash of
                 * java.lang.AssertionError: couldn't initialize LocaleData for locale
                 * if the user enters nonsensical region codes.
                 */
                if(p.contains("-"))
                    p = p.substring(0, p.indexOf('-'));
                locale = new Locale(p);
            }
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            getAppResources().updateConfiguration(config,
                    getAppResources().getDisplayMetrics());
        }
    }

    /**
     * Check if application is currently displayed
     * @return true if an activity is displayed, false if app is in background.
     */
    public static boolean isForeground() {
        return sActivitiesCount > 0;
    }

    private static int sActivitiesCount = 0;
    private static ActivityLifecycleCallbacks sActivityCbListener = AndroidUtil.isICSOrLater ? new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

        @Override
        public void onActivityStarted(Activity activity) {
            if (++sActivitiesCount == 1)
                ExternalMonitor.register(instance);
        }

        @Override
        public void onActivityResumed(Activity activity) {}

        @Override
        public void onActivityPaused(Activity activity) {}

        @Override
        public void onActivityStopped(Activity activity) {
            if (--sActivitiesCount == 0)
                ExternalMonitor.unregister(instance);
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

        @Override
        public void onActivityDestroyed(Activity activity) {}
    } : null;
}
