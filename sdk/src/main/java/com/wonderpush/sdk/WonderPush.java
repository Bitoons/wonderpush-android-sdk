package com.wonderpush.sdk;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.OpenUDID.OpenUDID_manager;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Main class of the WonderPush SDK.
 *
 * <p>You would normally only interact with this class, which has all static members.</p>
 *
 * <p>
 *   Make sure you properly installed the WonderPush SDK, as described in
 *   <a href="../../../packages.html">the guide</a>.
 * </p>
 *
 * <p>You must call {@link #initialize(Context)} before using any other function.</p>
 *
 * <p>
 *   Troubleshooting tip:
 *   As the SDK should not interfere with your application other than when a notification is to be shown,
 *   make sure to monitor your logs for the <tt>WonderPush</tt> tag during development,
 *   if things did not went as smoothly as they should have.
 * </p>
 */
public class WonderPush {

    static final String TAG = WonderPush.class.getSimpleName();
    protected static boolean SHOW_DEBUG = false;

    private static Context sApplicationContext;
    protected static Application sApplication;

    private static Looper sLooper;
    private static Handler sDeferHandler;
    protected static ScheduledExecutorService sScheduledExecutor;
    static {
        sDeferHandler = new Handler(Looper.getMainLooper()); // temporary value until our thread is started
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                sLooper = Looper.myLooper();
                sDeferHandler = new Handler(sLooper);
                Looper.loop();
            }
        }, "WonderPush").start();
        sScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    private static String sClientId;
    private static String sClientSecret;
    private static String sBaseURL;
    private static boolean sIsInitialized = false;
    private static boolean sIsReady = false;
    private static boolean sIsReachable = false;

    private static boolean sBeforeInitializationUserIdSet = false;
    private static String sBeforeInitializationUserId;

    /**
     * The timeout for WebView requests
     */
    protected static final int WEBVIEW_REQUEST_TOTAL_TIMEOUT = 10000;
    protected static final int API_INT = 1; // reset SDK_VERSION when bumping this
    protected static final String API_VERSION = "v" + API_INT;
    protected static final String SDK_SHORT_VERSION = "2.1.1-SNAPSHOT"; // reset to .1.0.0 when bumping API_INT
    protected static final String SDK_VERSION = "Android-" + API_INT + "." + SDK_SHORT_VERSION;
    protected static final int ERROR_INVALID_SID = 12017;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private static long startupDateToServerDateOffset = 0;
    private static long startupDateToServerDateUncertainty = Long.MAX_VALUE;
    private static long deviceDateToServerDateOffset = 0;
    private static long deviceDateToServerDateUncertainty = Long.MAX_VALUE;
    private static long startupDateToDeviceDateOffset = Long.MAX_VALUE;

    /**
     * How long in ms should two interactions should be separated in time,
     * to be considered as belonging to two different sessions.
     */
    private static final long DIFFERENT_SESSION_REGULAR_MIN_TIME_GAP = 30 * 60 * 1000;

    /**
     * How long in ms should have elapsed from last interaction,
     * to consider the opening of a notification as starting a new session.
     * This should be a lower threshold than {@link #DIFFERENT_SESSION_REGULAR_MIN_TIME_GAP},
     * as a notification creates a more urgent need to reopen the application.
     */
    private static final long DIFFERENT_SESSION_NOTIFICATION_MIN_TIME_GAP = 15 * 60 * 1000;

    /**
     * The metadata key name corresponding to the name of the WonderPushInitializer implementation.
     */
    private static final String METADATA_INITIALIZER_CLASS = "wonderpushInitializerClass";

    /**
     * The preference.subscriptionStatus value when notifications are enabled.
     */
    private static final String INSTALLATION_PREFERENCES_SUBSCRIPTION_STATUS_OPTIN = "optIn";

    /**
     * The preference.subscriptionStatus value when notifications are disabled.
     */
    private static final String INSTALLATION_PREFERENCES_SUBSCRIPTION_STATUS_OPTOUT = "optOut";

    /**
     * Local intent broadcasted when the WonderPush SDK has been initialized and network is reachable.
     */
    public static final String INTENT_INTIALIZED = "wonderpushInitialized";

    /**
     * Local intent broadcasted when a push notification created by the WonderPush SDK has been opened.
     */
    public static final String INTENT_NOTIFICATION_OPENED = "wonderpushNotificationOpened";

    /**
     * The extra key for the original received push notification intent in a {@link #INTENT_NOTIFICATION_OPENED} intent.
     */
    public static final String INTENT_NOTIFICATION_OPENED_EXTRA_RECEIVED_PUSH_NOTIFICATION = "wonderpushReceivedPushNotification";

    /**
     * The extra key for whether the user clicked the notification or it was automatically opened by the SDK
     * in a {@link #INTENT_NOTIFICATION_OPENED} intent.
     */
    public static final String INTENT_NOTIFICATION_OPENED_EXTRA_FROM_USER_INTERACTION = "wonderpushFromUserInteraction";

    /**
     * Local intent broadcasted when a push notification created by the WonderPush SDK is to be opened,
     * but no activity is to be started.
     * This let's you handle {@code data} notifications or any deep linking yourself.
     */
    public static final String INTENT_NOTIFICATION_WILL_OPEN = "wonderpushNotificationWillOpen";

    /**
     * The scheme for the WonderPushService intents.
     */
    protected static final String INTENT_NOTIFICATION_WILL_OPEN_SCHEME = "wonderpush";

    /**
     * The authority for handling notification opens with deep links calling the WonderPushService.
     */
    protected static final String INTENT_NOTIFICATION_WILL_OPEN_AUTHORITY = "notificationOpen";

    /**
     * The first path segment for opening the notification in the default way.
     */
    protected static final String INTENT_NOTIFICATION_WILL_OPEN_PATH_DEFAULT = "default";

    /**
     * The first path segment for broadcasting the "notification will open" event for a programmatic resolution.
     */
    protected static final String INTENT_NOTIFICATION_WILL_OPEN_PATH_BROADCAST = "broadcast";

    /**
     * The extra key for the original received push notification intent in a {@link #INTENT_NOTIFICATION_WILL_OPEN} intent.
     */
    public static final String INTENT_NOTIFICATION_WILL_OPEN_EXTRA_RECEIVED_PUSH_NOTIFICATION =
            INTENT_NOTIFICATION_OPENED_EXTRA_RECEIVED_PUSH_NOTIFICATION;

    /**
     * The extra key for whether the user clicked the notification or it was automatically opened by the SDK
     * in a {@link #INTENT_NOTIFICATION_WILL_OPEN} intent.
     */
    public static final String INTENT_NOTIFICATION_WILL_OPEN_EXTRA_FROM_USER_INTERACTION =
            INTENT_NOTIFICATION_OPENED_EXTRA_FROM_USER_INTERACTION;

    /**
     * The extra key denoting whether to automatically display a rich notification message in a {@link #INTENT_NOTIFICATION_WILL_OPEN} intent.
     * You can set this property to {@code false} in your BroadcastReceiver.
     */
    public static final String INTENT_NOTIFICATION_WILL_OPEN_EXTRA_AUTOMATIC_OPEN = "wonderpushAutomaticOpen";

    /**
     * The extra key denoting the received push notification type, for a {@link #INTENT_NOTIFICATION_WILL_OPEN} intent.
     * You can test this property against {@link #INTENT_NOTIFICATION_WILL_OPEN_EXTRA_NOTIFICATION_TYPE_DATA} in your BroadcastReceiver.
     */
    public static final String INTENT_NOTIFICATION_WILL_OPEN_EXTRA_NOTIFICATION_TYPE = "wonderpushNotificationType";

    /**
     * The value associated to data push notifications (aka silent notifications), corresponding to the extra key
     * {@link #INTENT_NOTIFICATION_WILL_OPEN_EXTRA_NOTIFICATION_TYPE}.
     */
    @SuppressWarnings("unused")
    public static final String INTENT_NOTIFICATION_WILL_OPEN_EXTRA_NOTIFICATION_TYPE_DATA = "data";

    /**
     * Local intent broadcasted when a resource has been successfully preloaded.
     */
    protected static final String INTENT_RESOURCE_PRELOADED = "wonderpushResourcePreloaded";

    /**
     * The extra key for the path of a preloaded resource in a {@link #INTENT_RESOURCE_PRELOADED} intent.
     */
    protected static final String INTENT_RESOURCE_PRELOADED_EXTRA_PATH = "wonderpushResourcePreloadedPath";

    /**
     * Intent scheme for GCM notification data when the user clicks the notification.
     */
    protected static final String INTENT_NOTIFICATION_SCHEME = "wonderpush";

    /**
     * Intent authority for GCM notification data when the user clicks the notification.
     */
    protected static final String INTENT_NOTIFICATION_AUTHORITY = "notification";

    /**
     * Intent data type for GCM notification data when the user clicks the notification.
     */
    protected static final String INTENT_NOTIFICATION_TYPE = "application/vnd.wonderpush.notification";

    /**
     * Intent query parameter key for GCM notification data when the user clicks the notification.
     */
    protected static final String INTENT_NOTIFICATION_QUERY_PARAMETER = "body";

    /**
     * Intent action for notification button action `method`.
     */
    public static final String INTENT_NOTIFICATION_BUTTON_ACTION_METHOD_ACTION = "com.wonderpush.action.method";

    /**
     * Intent scheme for notification button action `method`.
     */
    public static final String INTENT_NOTIFICATION_BUTTON_ACTION_METHOD_SCHEME = "wonderpush";

    /**
     * Intent authority for notification button action `method`.
     */
    public static final String INTENT_NOTIFICATION_BUTTON_ACTION_METHOD_AUTHORITY = "action.method";

    /**
     * Intent query parameter key for the notification button action `method` method name.
     */
    public static final String INTENT_NOTIFICATION_BUTTON_ACTION_METHOD_EXTRA_METHOD = "com.wonderpush.action.method.extra_method";

    /**
     * Intent query parameter key for the notification button action `method` argument.
     */
    public static final String INTENT_NOTIFICATION_BUTTON_ACTION_METHOD_EXTRA_ARG = "com.wonderpush.action.method.extra_arg";

    private static final String PRODUCTION_API_URL = "https://api.wonderpush.com/" + API_VERSION;
    protected static final int ERROR_INVALID_CREDENTIALS = 11000;
    protected static final int ERROR_INVALID_ACCESS_TOKEN = 11003;
    protected static final String DEFAULT_LANGUAGE_CODE = "en";
    protected static final String[] VALID_LANGUAGE_CODES = {
            "af", "ar", "be", "bg", "bn", "ca", "cs", "da", "de", "el", "en", "en_GB", "en_US", "es", "es_ES", "es_MX",
            "et", "fa", "fi", "fr", "fr_FR", "fr_CA", "he", "hi", "hr", "hu", "id", "is", "it", "ja", "ko", "lt", "lv",
            "mk", "ms", "nb", "nl", "pa", "pl", "pt", "pt_PT", "pt_BR", "ro", "ru", "sk", "sl", "sq", "sr", "sv", "sw",
            "ta", "th", "tl", "tr", "uk", "vi", "zh", "zh_CN", "zh_TW", "zh_HK",
    };

    protected WonderPush() {
        throw new IllegalAccessError("You should not instantiate this class!");
    }

    private static boolean checkPlayService(Context context) {
        try {
            Class.forName("com.google.android.gms.common.GooglePlayServicesUtil");
            GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
            if (resultCode != ConnectionResult.SUCCESS) {
                if (googleApiAvailability.isUserResolvableError(resultCode)) {
                    if (context instanceof Activity) {
                        googleApiAvailability.getErrorDialog((Activity) context, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
                    } else {
                        googleApiAvailability.showErrorNotification(context, resultCode);
                    }
                } else {
                    Log.w(TAG, "This device does not support Google Play Services, push notification are not supported");
                }
                return false;
            }
            return true;
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "The Google Play Services have not been added to the application", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while checking the Google Play Services", e);
        }
        return false;
    }

    /**
     * Helper method that will register a device for google cloud messages
     * notification and register the device token to WonderPush. This method is
     * called within {@link #initialize(Context, String, String)}.
     *
     * @param context
     *            The current {@link Activity} (preferred) or {@link Application} context.
     */
    protected static void registerForPushNotification(Context context) {
        if (checkPlayService(context)) {
            WonderPushGcmClient.registerForPushNotification(context);
        } else {
            Log.w(TAG, "Google Play Services not present. Check your setup. If on an emulator, use a Google APIs system image.");
        }
    }

    /**
     * Whether to enable debug logging.
     *
     * You should not do this in production builds.
     *
     * @param enable {@code true} to enable debug logs.
     */
    @SuppressWarnings("unused")
    public static void setLogging(boolean enable) {
        WonderPush.SHOW_DEBUG = enable;
    }

    protected static void logDebug(String debug) {
        if (WonderPush.SHOW_DEBUG) {
            Log.d(TAG, debug);
        }
    }

    protected static void logDebug(String debug, Throwable tr) {
        if (WonderPush.SHOW_DEBUG) {
            Log.d(TAG, debug, tr);
        }
    }

    protected static void logError(String msg) {
        if (WonderPush.SHOW_DEBUG) {
            Log.e(TAG, msg);
        }
    }

    protected static void logError(String msg, Throwable tr) {
        if (WonderPush.SHOW_DEBUG) {
            Log.e(TAG, msg, tr);
        }
    }

    /**
     * Method to call on your {@code onNewIntent()} and {@code onCreate()} methods to handle the WonderPush notification.
     *
     * <p>Starting from API 14, there is no need to call this method, but it won't hurt if you do.</p>
     *
     * <p>
     *   This method is automatically called from within {@link WonderPush#initialize(Context)},
     *   so calling this method after calling {@link WonderPush#initialize(Context)} is useless.
     * </p>
     *
     * <p>Example:</p>
     * <pre>
     * <code>
     * &#64;Override
     * protected void onCreate(Bundle savedInstance) {
     *     // In case you call WonderPush.initialize() from your custom Application class,
     *     // and you target API < 14, you can either call WonderPush.initialize() once again here
     *     // or call this method instead.
     *     WonderPush.showPotentialNotification(this, getIntent());
     * }
     *
     * &#64;Override
     * protected void onNewIntent(Intent intent) {
     *     WonderPush.showPotentialNotification(this, intent);
     * }
     * </code>
     * </pre>
     *
     * @param activity
     *            The current {@link Activity}.
     *            Just give {@code this}.
     * @param intent
     *            The intent the activity received.
     *            Just give the {@code intent} you received in parameter, or give {@code getIntent()}.
     *
     * @return <code>true</code> if handled, <code>false</code> otherwise.
     */
    public static boolean showPotentialNotification(final Activity activity, Intent intent) {
        try {
            NotificationManager.showPotentialNotification(activity, intent);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while showing potential notification", e);
        }
        return false;
    }

    /**
     * Gets the clientId that was specified during the {@link #initialize(Context, String, String)} call.
     */
    protected static String getClientId() {
        return sClientId;
    }

    /**
     * Gets the clientSecret that was specified during the {@link #initialize(Context, String, String)} call.
     */
    protected static String getClientSecret() {
        return sClientSecret;
    }

    protected static boolean isUDIDReady() {
        return OpenUDID_manager.isInitialized();
    }

    /**
     * Returns the UDID determined by OpenUDID.
     *
     * @return The UDID determined by OpenUDID or null if OpenUDID is not initialized.
     */
    protected static String getUDID() {
        if (!isUDIDReady()) {
            Log.w(TAG, "Reading UDID before it is ready!");
            return null;
        }
        return OpenUDID_manager.getOpenUDID();
    }

    protected static void setNetworkAvailable(boolean state) {
        sIsReachable = state;
    }

    protected static boolean isNetworkAvailable() {
        return sIsReachable;
    }

    /**
     * Returns the base URL for the WonderPush API.
     * This is the URL used to prefix every API resource path.
     *
     * @return The base URL.
     */
    protected static String getBaseURL() {
        return sBaseURL;
    }

    /**
     * Returns the base URL for the WonderPush API with a <code>http:</code> scheme.
     *
     * @see #getBaseURL()
     *
     * @return The base URL.
     */
    protected static String getNonSecureBaseURL() {
        return sBaseURL.replaceFirst("https:", "http:");
    }

    /**
     * A GET request.
     *
     * @param resource
     *            The resource path, starting with /.
     * @param params
     *            AsyncHttpClient request parameters.
     * @param responseHandler
     *            An AsyncHttpClient response handler.
     */
    protected static void get(String resource, RequestParams params,
            ResponseHandler responseHandler) {
        WonderPushRestClient.get(resource, params, responseHandler);
    }

    /**
     * A POST request.
     *
     * @param resource
     *            The resource path, starting with /.
     * @param params
     *            AsyncHttpClient request parameters.
     * @param responseHandler
     *            An AsyncHttpClient response handler.
     */
    protected static void post(String resource, RequestParams params,
            ResponseHandler responseHandler) {
        WonderPushRestClient.post(resource, params, responseHandler);
    }

    /**
     * A POST request that is guaranteed to be executed when a network
     * connection is present, surviving application reboot. The responseHandler
     * will be called only if the network is present when the request is first run.
     *
     * @param resource
     *            The resource path, starting with /.
     * @param params
     *            The request parameters. Only serializable parameters are
     *            guaranteed to survive a network error or device reboot.
     */
    protected static void postEventually(String resource,
            RequestParams params) {
        WonderPushRestClient.postEventually(resource, params);
    }

    /**
     * A PUT request.
     *
     * @param resource
     *            The resource path, starting with /.
     * @param params
     *            AsyncHttpClient request parameters.
     * @param responseHandler
     *            An AsyncHttpClient response handler.
     */
    protected static void put(String resource, RequestParams params,
            ResponseHandler responseHandler) {
        WonderPushRestClient.put(resource, params, responseHandler);
    }

    /**
     * A DELETE request.
     *
     * @param resource
     *            The resource path, starting with /.
     * @param responseHandler
     *            An AsyncHttpClient response handler.
     */
    protected static void delete(String resource,
            ResponseHandler responseHandler) {
        WonderPushRestClient.delete(resource, responseHandler);
    }

    /**
     * Returns the last known location of the {@link LocationManager}
     * or null if permission was not given.
     */
    protected static Location getLocation() {
        Context applicationContext = getApplicationContext();

        if (applicationContext == null)
            return null;

        LocationManager locationManager = (LocationManager) applicationContext.getSystemService(Context.LOCATION_SERVICE);
        try {
            Location best = null;
            for (String provider : locationManager.getAllProviders()) {
                Location location;
                try {
                    location = locationManager.getLastKnownLocation(provider);
                } catch (SecurityException ex) {
                    continue;
                }
                // If this location is null, discard
                if (null == location) {
                    continue;
                }

                // If no, broken or poor accuracy, discard
                if (location.getAccuracy() <= 0 || location.getAccuracy() >= 10000) {
                    continue;
                }

                // Skip locations older than 5 minutes
                if (location.getTime() < System.currentTimeMillis() - 5 * 60 * 1000) {
                    continue;
                }

                // If we have no best yet, use this first location
                if (null == best) {
                    best = location;
                    continue;
                }

                // If this location is more than 2 minutes older than the current best, discard
                if (location.getTime() < best.getTime() - 2 * 60 * 1000) {
                    continue;
                }

                // If this location is less precise (ie. has a *larger* accuracy radius), discard
                if (location.getAccuracy() > best.getAccuracy()) {
                    continue;
                }

                best = location;
            }

            return best;
        } catch (java.lang.SecurityException e) {
            // Missing permission;
            return null;
        }
    }

    /**
     * Gets the current language, guessed from the system.
     *
     * @return The locale in use.
     */
    protected static String getLang() {
        Locale locale = Locale.getDefault();

        if (null == locale)
            return DEFAULT_LANGUAGE_CODE;

        String language = locale.getLanguage();
        String country = locale.getCountry();
        String localeString = String.format("%s_%s",
                language != null ? language.toLowerCase(Locale.ENGLISH) : "",
                country != null ? country.toUpperCase(Locale.ENGLISH) : "");

        // 1. if no language is specified, return the default language
        if (null == language)
            return DEFAULT_LANGUAGE_CODE;

        // 2. try to match the language or the entire locale string among the
        // list of available language codes
        String matchedLanguageCode = null;
        for (String languageCode : VALID_LANGUAGE_CODES) {
            if (languageCode.equals(localeString)) {
                // return here as this is the most precise match we can get
                return localeString;
            }

            if (languageCode.equals(language)) {
                // set the matched language code, and continue iterating as we
                // may match the localeString in a later iteration.
                matchedLanguageCode = language;
            }
        }

        if (null != matchedLanguageCode)
            return matchedLanguageCode;

        return DEFAULT_LANGUAGE_CODE;
    }

    /**
     * Returns the latest known custom properties attached to the current installation object stored by WonderPush.
     */
    @SuppressWarnings("unused")
    public static JSONObject getInstallationCustomProperties() {
        return InstallationManager.getInstallationCustomProperties();
    }

    /**
     * Update the custom properties attached to the current installation object stored by WonderPush.
     *
     * <p>
     *   In order to remove a value, don't forget to use the
     *   {@link <a href="http://d.android.com/reference/org/json/JSONObject.html#NULL">JSONObject.NULL</a>}
     *   object as value.
     * </p>
     *
     * @param customProperties
     *            The partial object containing only the properties to update.
     */
    @SuppressWarnings("unused")
    public static synchronized void putInstallationCustomProperties(JSONObject customProperties) {
        try {
            InstallationManager.putInstallationCustomProperties(customProperties);
            onInteraction();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while putting installation custom properties", e);
        }
    }

    /**
     * Get the current timestamp in milliseconds, UTC.
     * @return A timestamp in milliseconds
     */
    protected static long getTime() {
        // Initialization
        if (deviceDateToServerDateUncertainty == Long.MAX_VALUE) {
            deviceDateToServerDateUncertainty = WonderPushConfiguration.getDeviceDateSyncUncertainty();
            deviceDateToServerDateOffset = WonderPushConfiguration.getDeviceDateSyncOffset();
        }
        long currentTimeMillis = System.currentTimeMillis();
        long elapsedRealtime = SystemClock.elapsedRealtime();
        long startupToDeviceOffset = currentTimeMillis - elapsedRealtime;
        if (startupDateToDeviceDateOffset == Long.MAX_VALUE) {
            startupDateToDeviceDateOffset = startupToDeviceOffset;
        }

        // Check device date consistency with startup date
        if (Math.abs(startupToDeviceOffset - startupDateToDeviceDateOffset) > 1000) {
            // System time has jumped (by at least 1 second), or has drifted with regards to elapsedRealtime.
            // Apply the offset difference to resynchronize the "device" sync offset onto the new system date.
            deviceDateToServerDateOffset -= startupToDeviceOffset - startupDateToDeviceDateOffset;
            WonderPushConfiguration.setDeviceDateSyncOffset(deviceDateToServerDateOffset);
            startupDateToDeviceDateOffset = startupToDeviceOffset;
        }

        if (startupDateToServerDateUncertainty <= deviceDateToServerDateUncertainty
                // Don't use the startup date if it has not been synced, use and trust last device date sync
                && startupDateToServerDateUncertainty != Long.MAX_VALUE) {
            return elapsedRealtime + startupDateToServerDateOffset;
        } else {
            return currentTimeMillis + deviceDateToServerDateOffset;
        }
    }

    /**
     * Synchronize time with the WonderPush servers.
     * @param elapsedRealtimeSend
     *            The time at which the request was sent.
     * @param elapsedRealtimeReceive
     *            The time at which the response was received.
     * @param serverDate
     *            The time at which the server received the request, as read in the response.
     * @param serverTook
     *            The time the server took to process the request, as read in the response.
     */
    protected static void syncTimeWithServer(long elapsedRealtimeSend, long elapsedRealtimeReceive, long serverDate, long serverTook) {
        if (serverDate == 0) {
            return;
        }

        // We have two synchronization sources:
        // - The "startup" sync, bound to the process lifecycle, using SystemClock.elapsedRealtime()
        //   This time source cannot be messed up with.
        //   It is only valid until the device reboots, at which time a new time origin is set.
        // - The "device" sync, bound to the system clock, using System.currentTimeMillis()
        //   This time source is affected each time the user changes the date and time,
        //   but it is not affected by timezone or daylight saving changes.
        // The "startup" sync must be saved into a "device" sync in order to persist between runs of the process.
        // The "startup" sync should only be stored in memory, and no attempt to count reboot should be taken.

        // Initialization
        if (deviceDateToServerDateUncertainty == Long.MAX_VALUE) {
            deviceDateToServerDateUncertainty = WonderPushConfiguration.getDeviceDateSyncUncertainty();
            deviceDateToServerDateOffset = WonderPushConfiguration.getDeviceDateSyncOffset();
        }
        long startupToDeviceOffset = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        if (startupDateToDeviceDateOffset == Long.MAX_VALUE) {
            startupDateToDeviceDateOffset = startupToDeviceOffset;
        }

        long uncertainty = (elapsedRealtimeReceive - elapsedRealtimeSend - serverTook) / 2;
        long offset = serverDate + serverTook / 2 - (elapsedRealtimeSend + elapsedRealtimeReceive) / 2;

        // We must improve the quality of the "startup" sync. We can trust elaspedRealtime() based measures.
        if (
                // Case 1. Lower uncertainty
                uncertainty < startupDateToServerDateUncertainty
                // Case 2. Additional check for exceptional server-side time gaps
                //         Calculate whether the two offsets agree within the total uncertainty limit
                || Math.abs(offset - startupDateToServerDateOffset)
                        > uncertainty+startupDateToServerDateUncertainty
                        // note the RHS overflows with the Long.MAX_VALUE initialization, but case 1 handles that
        ) {
            // Case 1. Take the new, more accurate synchronization
            // Case 2. Forget the old synchronization, time have changed too much
            startupDateToServerDateOffset = offset;
            startupDateToServerDateUncertainty = uncertainty;
        }

        // We must detect whether the "device" sync is still valid, otherwise we must update it.
        if (
                // Case 1. Lower uncertainty
                startupDateToServerDateUncertainty < deviceDateToServerDateUncertainty
                // Case 2. Local clock was updated, or the two time sources have drifted from each other
                || Math.abs(startupToDeviceOffset - startupDateToDeviceDateOffset) > startupDateToServerDateUncertainty
                // Case 3. Time gap between the "startup" and "device" sync
                || Math.abs(deviceDateToServerDateOffset - (startupDateToServerDateOffset - startupDateToDeviceDateOffset))
                        > deviceDateToServerDateUncertainty + startupDateToServerDateUncertainty
                        // note the RHS overflows with the Long.MAX_VALUE initialization, but case 1 handles that
        ) {
            deviceDateToServerDateOffset = startupDateToServerDateOffset - startupDateToDeviceDateOffset;
            deviceDateToServerDateUncertainty = startupDateToServerDateUncertainty;
            WonderPushConfiguration.setDeviceDateSyncOffset(deviceDateToServerDateOffset);
            WonderPushConfiguration.setDeviceDateSyncUncertainty(deviceDateToServerDateUncertainty);
        }
    }

    /**
     * Send an event to be tracked to WonderPush.
     * @param type
     *            The event type, or name.
     *            Event types starting with an {@code @} character are reserved.
     */
    @SuppressWarnings("unused")
    public static void trackEvent(String type) {
        try {
            trackEvent(type, null);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while tracking user event of type \"" + type + "\"", e);
        }
    }

    /**
     * Send an event to be tracked to WonderPush.
     * @param type
     *            The event type, or name.
     *            Event types starting with an {@code @} character are reserved.
     * @param customData
     *            A JSON object containing custom properties to be attached to the event.
     *            Prefer using a few custom properties over a plethora of event type variants.
     */
    public static void trackEvent(String type, JSONObject customData) {
        try {
            if (type == null || type.length() == 0 || type.charAt(0) == '@') {
                throw new IllegalArgumentException("Bad event type");
            }
            sendEvent(type, null, customData);
            onInteraction();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while tracking user event of type \"" + type + "\"", e);
        }
    }

    protected static void trackInternalEvent(String type, JSONObject eventData) {
        trackInternalEvent(type, eventData, null);
    }

    protected static void trackInternalEvent(String type, JSONObject eventData, JSONObject customData) {
        if (type.charAt(0) != '@') {
            throw new IllegalArgumentException("This method must only be called for internal events, starting with an '@'");
        }
        sendEvent(type, eventData, customData);
    }

    private static void sendEvent(String type, JSONObject eventData, JSONObject customData) {
        String eventEndpoint = "/events/";

        JSONObject event = new JSONObject();
        if (eventData != null && eventData.length() > 0) {
            @SuppressWarnings("unchecked")
            Iterator<String> keys = eventData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = eventData.opt(key);
                try {
                    event.putOpt(key, value);
                } catch (JSONException ex) {
                    WonderPush.logError("Error building event object body", ex);
                }
            }
        }
        try {
            event.put("type", type);
            if (customData != null && customData.length() > 0) {
                event.put("custom", customData);
            }
            // Fill some pieces of information at the time of tracking,
            // instead of using the automatically injected location at request time,
            // which can be wrong in case of network problems
            Location location = getLocation();
            if (location != null) {
                event.put("location", "" + location.getLatitude() + "," + location.getLongitude());
            }
            if (!event.has("actionDate")) {
                event.put("actionDate", getTime());
            }
        } catch (JSONException ex) {
            WonderPush.logError("Error building event object body", ex);
        }

        RequestParams parameters = new RequestParams();
        parameters.put("body", event.toString());
        postEventually(eventEndpoint, parameters);
    }

    protected static void onInteraction() {
        long lastInteractionDate = WonderPushConfiguration.getLastInteractionDate();
        long lastAppOpenDate = WonderPushConfiguration.getLastAppOpenDate();
        long lastAppCloseDate = WonderPushConfiguration.getLastAppCloseDate();
        JSONObject lastReceivedNotificationInfo = WonderPushConfiguration.getLastReceivedNotificationInfoJson();
        if (lastReceivedNotificationInfo == null) lastReceivedNotificationInfo = new JSONObject();
        long lastReceivedNotificationDate = lastReceivedNotificationInfo.optLong("actionDate", Long.MAX_VALUE);
        JSONObject lastOpenedNotificationInfo = WonderPushConfiguration.getLastOpenedNotificationInfoJson();
        if (lastOpenedNotificationInfo == null) lastOpenedNotificationInfo = new JSONObject();
        long lastOpenedNotificationDate = lastOpenedNotificationInfo.optLong("actionDate", Long.MAX_VALUE);
        long now = getTime();

        if (
                now - lastInteractionDate >= DIFFERENT_SESSION_REGULAR_MIN_TIME_GAP
                || (
                        lastReceivedNotificationDate > lastInteractionDate
                        && now - lastInteractionDate >= DIFFERENT_SESSION_NOTIFICATION_MIN_TIME_GAP
                )
        ) {
            // We will track a new app open event

            // We must first close the possibly still-open previous session
            if (lastAppCloseDate < lastAppOpenDate) {
                JSONObject closeInfo = WonderPushConfiguration.getLastAppOpenInfoJson();
                if (closeInfo == null) {
                    closeInfo = new JSONObject();
                }
                long appCloseDate = lastInteractionDate;
                try {
                    closeInfo.put("actionDate", appCloseDate);
                    closeInfo.put("openedTime", appCloseDate - lastAppOpenDate);
                } catch (JSONException e) {
                    logDebug("Failed to fill @APP_CLOSE information", e);
                }
                // trackInternalEvent("@APP_CLOSE", closeInfo);
                WonderPushConfiguration.setLastAppCloseDate(appCloseDate);
            }

            // Track the new app open event
            JSONObject openInfo = new JSONObject();
            // Add the elapsed time between the last received notification
            if (lastReceivedNotificationDate <= now) {
                try {
                    openInfo.put("lastReceivedNotificationTime", now - lastReceivedNotificationDate);
                } catch (JSONException e) {
                    logDebug("Failed to fill @APP_OPEN previous notification information", e);
                }
            }
            // Add the information of the clicked notification
            if (now - lastOpenedNotificationDate < 10 * 1000) { // allow a few seconds between click on the notification and the call to this method
                try {
                    openInfo.putOpt("notificationId", lastOpenedNotificationInfo.opt("notificationId"));
                    openInfo.putOpt("campaignId", lastOpenedNotificationInfo.opt("campaignId"));
                } catch (JSONException e) {
                    logDebug("Failed to fill @APP_OPEN opened notification information", e);
                }
            }
            trackInternalEvent("@APP_OPEN", openInfo);
            WonderPushConfiguration.setLastAppOpenDate(now);
            WonderPushConfiguration.setLastAppOpenInfoJson(openInfo);
        }

        WonderPushConfiguration.setLastInteractionDate(now);
    }

    /**
     * Whether {@link #initialize(Context, String, String)} has been called.
     * Different from having fetched an access token,
     * and hence from {@link #INTENT_INTIALIZED} being dispatched.
     * @return {@code true} if the SDK is initialized, {@code false} otherwise.
     */
    static boolean isInitialized() {
        return sIsInitialized;
    }

    /**
     * Whether the SDK is ready to operate and
     * the {@link #INTENT_INTIALIZED} intent has been dispatched.
     *
     * The SDK is ready when it is initialized and has fetched an access token.
     * @return {@code true} if the SDK is ready, {@code false} otherwise.
     */
    @SuppressWarnings("unused")
    public static boolean isReady() {
        return sIsReady;
    }

    /**
     * Initialize WonderPush.<br />
     * <b>Call this method before using WonderPush.</b>
     *
     * <p>
     *   A good place to initialize WonderPush is in your main activity's
     *   <a href="http://developer.android.com/reference/android/app/Activity.html#onCreate(android.os.Bundle)">
     *   {@code onCreate(Bundle)}</a> method as follows:
     * </p>
     * <pre><code>protected void onCreate(Bundle savedInstance) {
     *    WonderPush.initialize(this);
     *}</code></pre>
     *
     * <p>
     *   This function will instantiate the {@link WonderPushInitializer} implementation you provided in your
     *   {@code AndroidManifest.xml}.<br />
     *   <i>Please look at that interface documentation for detailed instruction.</i>
     * </p>
     *
     * @param context
     *            The main {@link Activity} of your application, or failing that, the {@link Application} context.
     *            It must be the same activity that you declared in the {@code <meta-data>} tag
     *            under the WonderPush {@code <receiver>} tag in your {@code AndroidManifest.xml}.
     */
    @SuppressWarnings("unused")
    public static void initialize(final Context context) {
        try {
            ensureInitialized(context);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while initializing the SDK", e);
        }
    }

    /**
     * Initialize WonderPush from your {@link WonderPushInitializer} implementation.
     *
     * <p>
     *   Prefer calling the simpler {@link WonderPush#initialize(Context)} function directly, as it will
     *   instantiate your {@link WonderPushInitializer} implementation which will in turn call this function.
     *   This way you concentrate the retrieval of your credentials from secure storage in a single location.
     * </p>
     *
     * @param context
     *            The main {@link Activity} of your application, or failing that, the {@link Application} context.
     * @param clientId
     *            The clientId of your application.
     * @param clientSecret
     *            The clientSecret of your application.
     */
    public static void initialize(final Context context, final String clientId, String clientSecret) {
        try {
            if (!sIsInitialized || (
                    clientId != null && clientSecret != null && (!clientId.equals(sClientId) || !clientSecret.equals(sClientSecret))
            )) {

                sIsInitialized = false;
                setNetworkAvailable(false);

                sApplicationContext = context.getApplicationContext();
                sClientId = clientId;
                sClientSecret = clientSecret;
                sBaseURL = PRODUCTION_API_URL;

                WonderPushConfiguration.initialize(getApplicationContext());
                WonderPushRequestVault.initialize();

                initForNewUser(sBeforeInitializationUserIdSet
                        ? sBeforeInitializationUserId
                        : WonderPushConfiguration.getUserId());

                // Initialize OpenUDID
                OpenUDID_manager.sync(getApplicationContext());

                sIsInitialized = true;

                // Permission checks
                if (context.getPackageManager().checkPermission(android.Manifest.permission.INTERNET, context.getPackageName()) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Missing INTERNET permission. Add <uses-permission android:name=\"android.permission.INTERNET\" /> under <manifest> in your AndroidManifest.xml");
                }
                if (context.getPackageManager().checkPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, context.getPackageName()) != PackageManager.PERMISSION_GRANTED
                        && context.getPackageManager().checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, context.getPackageName()) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Missing ACCESS_COARSE_LOCATION and ACCESS_FINE_LOCATION permission. Add <uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\" /> under <manifest> in your AndroidManifest.xml (you can add either or both)");
                } else if (context.getPackageManager().checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, context.getPackageName()) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Only ACCESS_COARSE_LOCATION permission is granted. For more precision, you should strongly consider adding <uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\" /> under <manifest> in your AndroidManifest.xml");
                }
            }

            initializeForApplication(context);
            initializeForActivity(context);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while initializing the SDK", e);
        }
    }

    private static void initForNewUser(final String userId) {
        sIsReady = false;
        if (WonderPushConfiguration.getCachedInstallationCustomPropertiesFirstDelayedWrite() != 0) {
            // Flush any delayed write for old user
            InstallationManager.putInstallationCustomProperties_inner();
        }
        WonderPushConfiguration.changeUserId(userId);
        // Wait for UDID to be ready and fetch anonymous token if needed.
        WonderPush.safeDefer(new Runnable() {
            @Override
            public void run() {
                if (isUDIDReady()) {
                    final Runnable init = new Runnable() {
                        @Override
                        public void run() {
                            InstallationManager.updateInstallationCoreProperties(getApplicationContext());
                            registerForPushNotification(getApplicationContext());
                            sIsReady = true;
                            Intent broadcast = new Intent(INTENT_INTIALIZED);
                            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(broadcast);
                        }
                    };
                    boolean isFetchingToken = WonderPushRestClient.fetchAnonymousAccessTokenIfNeeded(userId, new ResponseHandler() {
                        @Override
                        public void onFailure(Throwable e, Response errorResponse) {
                        }
                        @Override
                        public void onSuccess(Response response) {
                            init.run();
                        }
                    });
                    if (!isFetchingToken) {
                        init.run();
                    }
                } else {
                    WonderPush.safeDefer(this, 100);
                }
            }
        }, 0);
    }

    protected static void initializeForApplication(Context context) {
        if (sApplication != null || !(context instanceof Application)) {
            return;
        }
        sApplication = (Application) context;
        ActivityLifecycleMonitor.monitorActivitiesLifecycle();
    }

    protected static void initializeForActivity(Context context) {
        if (!(context instanceof Activity)) {
            return;
        }
        Activity activity = (Activity) context;

        ActivityLifecycleMonitor.addTrackedActivity(activity);

        showPotentialNotification(activity, activity.getIntent());
        onInteraction(); // keep after onCreateMainActivity() as a possible received notification's information is needed
    }

    /**
     * Instantiate the {@link WonderPushInitializer} interface configured in the {@code AndroidManifest.xml},
     * and calls it if the SDK is not initialized yet.
     * @param context
     *            The main {@link Activity} of your application, or failing that, the {@link Application} context.
     * @return {@code true} if no error happened, {@code false} otherwise
     */
    protected static boolean ensureInitialized(Context context) {
        if (!isInitialized()) {

            String initializerClassName = null;
            try {

                ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                Bundle bundle = ai.metaData;
                initializerClassName = bundle.getString(METADATA_INITIALIZER_CLASS);
                if (initializerClassName == null) {
                    Log.e(TAG, "Failed to load initializer class. Did you add: <meta-data android:name=\"" + METADATA_INITIALIZER_CLASS + "\" android:value=\"com.package.YourWonderPushInitializerImpl\"/> under <application> in your AndroidManifest.xml");
                }

                Class<? extends WonderPushInitializer> initializerClass = Class.forName(initializerClassName).asSubclass(WonderPushInitializer.class);
                WonderPushInitializer initializer = initializerClass.newInstance();

                initializer.initialize(context);

            } catch (NameNotFoundException e) {
                Log.e(TAG, "Failed to load initializer class", e);
            } catch (NullPointerException e) {
                Log.e(TAG, "Failed to load initializer class", e);
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Failed to load initializer class. Check your <meta-data android:name=\"" + METADATA_INITIALIZER_CLASS + "\" android:value=\"com.package.YourWonderPushInitializerImpl\"/> entry under <application> in your AndroidManifest.xml", e);
            } catch (InstantiationException e) {
                Log.e(TAG, "Failed to intantiate the initializer class " + initializerClassName + ". Make sure it has a public default constructor with no argument.", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Failed to intantiate the initializer class " + initializerClassName + ". Make sure it has a public default constructor with no argument.", e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error while ensuring SDK initialization", e);
            }

        } else {

            // No need to get clientId/clientSecret once again
            // we only need to re-run the Activity-related initialization
            initialize(context, null, null);

        }

        return isInitialized();
    }

    /**
     * Sets the user id, used to identify a single identity across multiple devices,
     * and to correctly identify multiple users on a single device.
     *
     * <p>If not called, the last used user id it assumed. Defaulting to {@code null} if none is known.</p>
     *
     * <p>Prefer calling this method just before calling {@link #initialize(Context)}, rather than just after.</p>
     *
     * <p>
     *   Upon changing userId, the access token is wiped, so avoid unnecessary calls, like calling with {@code null}
     *   just before calling with a user id.
     * </p>
     *
     * @param userId
     *            The user id, unique to your application.
     *            Use {@code null} for anonymous users.<br />
     *            You are strongly encouraged to use your own unique internal identifier.
     */
    @SuppressWarnings("unused")
    public static void setUserId(String userId) {
        try {
            if ("".equals(userId)) userId = null;
            logDebug("setUserId(" + userId + ")");

            // Do nothing if not initialized
            if (!isInitialized()) {
                logDebug("setting user id for next initialization");
                sBeforeInitializationUserIdSet = true;
                sBeforeInitializationUserId = userId;
                return;
            }
            sBeforeInitializationUserIdSet = false;
            sBeforeInitializationUserId = null;

            String oldUserId = WonderPushConfiguration.getUserId();
            if (userId == null && oldUserId == null
                    || userId != null && userId.equals(oldUserId)) {
                // User id is the same as before, nothing needs to be done
            } else {
                // The user id changed, we must reset the access token
                initForNewUser(userId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while setting userId to \"" + userId + "\"", e);
        }
    }

    /**
     * Gets the user id, used to identify a single identity across multiple devices,
     * and to correctly identify multiple users on a single device.
     *
     * <p>You should not call this method before initializing the SDK.</p>
     *
     * @return The user id, which may be {@code null} for anonymous users.
     * @see #setUserId(String)
     * @see #initialize(Context)
     */
    @SuppressWarnings("unused")
    public static String getUserId() {
        String userId = null;
        try {
            userId = WonderPushConfiguration.getUserId();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while getting userId", e);
        }
        return userId;
    }

    /**
     * Gets the device id, used to identify a single device across applications,
     * and to correctly identify multiple users on a single device.
     *
     * <p>You should not call this method before initializing the SDK.</p>
     *
     * <p>
     *   Because of the way our device id is build, it is populated asynchronously,
     *   so you may get a {@code null} response even a few moments after calling
     *   {@link #initialize(Context)}.
     *   You can either wait a bit (1 second should be enough on modern devices), or
     *   wait for {@link #isReady()} to return {@code true}, which may take some more
     *   time on the first launch, especially is the network connection is bad.
     * </p>
     *
     * @return The device id, or {@code null} if the SDK is not initialized.
     * @see #isReady()
     * @see #initialize(Context)
     */
    @SuppressWarnings("unused")
    public static String getDeviceId() {
        String deviceId = null;
        try {
            deviceId = getUDID();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while getting userId", e);
        }
        return deviceId;
    }

    /**
     * Gets the device id, used to identify a single device across applications,
     * and to correctly identify multiple users on a single device.
     *
     * <p>You should not call this method before the SDK is ready.</p>
     *
     * @return The device id, or {@code null} if the SDK is not initialized.
     * @see #isReady()
     */
    @SuppressWarnings("unused")
    public static String getInstallationId() {
        String installationId = null;
        try {
            installationId = WonderPushConfiguration.getInstallationId();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while getting installationId", e);
        }
        return installationId;
    }

    /**
     * Gets the push token, used to send notification to this installation.
     *
     * <p>You should not call this method before initializing the SDK.</p>
     *
     * @return The push token, or {@code null} if the installation is not yet
     *     registered to push notifications, or has not finished refreshing
     *     the push token after a forced update.
     */
    @SuppressWarnings("unused")
    public static String getPushToken() {
        String pushToken = null;
        try {
            pushToken = WonderPushConfiguration.getGCMRegistrationId();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while getting pushToken", e);
        }
        return pushToken;
    }

    /**
     * Gets the access token, used to grant access to the current installation
     * to the WonderPush REST API.
     *
     * <p>You should not call this method before the SDK is ready.</p>
     *
     * <p>
     *     This together with your client secret gives entire control to the current installation
     *     and the associated user, you should not disclose it unnecessarily.
     * </p>
     *
     * @return The access token, or {@code null} if the SDK is not initialized.
     * @see #isReady()
     */
    @SuppressWarnings("unused")
    public static String getAccessToken() {
        String accessToken = null;
        try {
            accessToken = WonderPushConfiguration.getAccessToken();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while getting accessToken", e);
        }
        return accessToken;
    }

    /**
     * Method to be called in your own Google Cloud Messaging
     * <a href="http://developer.android.com/reference/android/content/BroadcastReceiver.html"><tt>BroadcastReceiver</tt></a>
     * to handle WonderPush push notifications.
     *
     * <p>
     *   <b>Note:</b> This is only needed if you use your own {@link BroadcastReceiver}, as previously
     *   advertised in <a href="../../../packages.html#installing-sdk--configuring-sdk">the guide</a>.
     * </p>
     *
     * <p>
     *   Implement your <a href="http://developer.android.com/reference/android/content/BroadcastReceiver.html#onReceive(android.content.Context, android.content.Intent)"><tt>BroadcastReceiver.onReceive(Context, Intent)</tt></a>
     *   method as follows:
     * </p>
     * <pre><code>public void onReceive(Context context, Intent intent) {
     *    if (WonderPush.onBroadcastReceived(context, intent, R.drawable.icon, YourMainActivity.class)) {
     *        return;
     *    }
     *    // Do your own handling here
     *}</code></pre>
     *
     * <p>
     *   For more information about Google Cloud Messaging visit:
     *   <a href="https://developers.google.com/cloud-messaging/android/client">https://developers.google.com/cloud-messaging/android/client</a>.
     * </p>
     *
     * @param context
     *            The current context.
     * @param intent
     *            The received intent.
     * @param iconResource
     *            The icon you want to show in the notification.
     * @param activityClass
     *            The activity class you want to start when the user touches the notification
     * @return {@code true} if handled, {@code false} otherwise.
     */
    public static boolean onBroadcastReceived(Context context, Intent intent, int iconResource, Class<? extends Activity> activityClass) {
        try {
            return WonderPushGcmClient.onBroadcastReceived(context, intent, iconResource, activityClass);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while giving broadcast to the receiver", e);
        }
        return false;
    }

    /**
     * Returns whether push notification are enabled.
     * @return {@code true} by default as no explicit user permission is required.
     */
    @SuppressWarnings("unused")
    public static boolean getNotificationEnabled() {
        return WonderPushConfiguration.getNotificationEnabled();
    }

    /**
     * Sets whether to enable push notifications for the current device.
     * @param status {@code false} to opt out of push notifications.
     */
    @SuppressWarnings("unused")
    public static void setNotificationEnabled(boolean status) {
        try {
            String value = status
                    ? INSTALLATION_PREFERENCES_SUBSCRIPTION_STATUS_OPTIN
                    : INSTALLATION_PREFERENCES_SUBSCRIPTION_STATUS_OPTOUT;
            JSONObject properties = new JSONObject();
            JSONObject preferences = new JSONObject();
            properties.put("preferences", preferences);
            preferences.put("subscriptionStatus", value);
            InstallationManager.updateInstallation(properties, false);
            WonderPushConfiguration.setNotificationEnabled(status);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error while setting notification enabled to " + status, e);
        }
    }

    /**
     * Gets the application context that was captured during the
     * {@link WonderPush#initialize(Context, String, String)} call.
     */
    protected static Context getApplicationContext() {
        if (null == sApplicationContext)
            Log.e(TAG, "Application context is null, did you call WonderPush.initialize()?");
        return sApplicationContext;
    }

    protected static boolean safeDefer(final Runnable runnable, long defer) {
        return sDeferHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Exception ex) {
                    Log.e(TAG, "Unexpected error on deferred task", ex);
                }
            }
        }, defer);
    }

}