/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko;

import android.Manifest;
import android.app.DownloadManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import org.json.JSONArray;
import org.mozilla.gecko.adjust.AdjustHelperInterface;
import org.mozilla.gecko.annotation.RobocopTarget;
import org.mozilla.gecko.AppConstants.Versions;
import org.mozilla.gecko.DynamicToolbar.VisibilityTransition;
import org.mozilla.gecko.GeckoProfileDirectories.NoMozillaDirectoryException;
import org.mozilla.gecko.Tabs.TabEvents;
import org.mozilla.gecko.animation.PropertyAnimator;
import org.mozilla.gecko.animation.ViewHelper;
import org.mozilla.gecko.db.BrowserContract;
import org.mozilla.gecko.db.BrowserDB;
import org.mozilla.gecko.db.SuggestedSites;
import org.mozilla.gecko.distribution.Distribution;
import org.mozilla.gecko.dlc.DownloadContentService;
import org.mozilla.gecko.dlc.catalog.DownloadContent;
import org.mozilla.gecko.favicons.Favicons;
import org.mozilla.gecko.favicons.OnFaviconLoadedListener;
import org.mozilla.gecko.favicons.decoders.IconDirectoryEntry;
import org.mozilla.gecko.feeds.FeedService;
import org.mozilla.gecko.feeds.action.CheckForUpdatesAction;
import org.mozilla.gecko.firstrun.FirstrunAnimationContainer;
import org.mozilla.gecko.gfx.DynamicToolbarAnimator;
import org.mozilla.gecko.gfx.DynamicToolbarAnimator.PinReason;
import org.mozilla.gecko.gfx.ImmutableViewportMetrics;
import org.mozilla.gecko.gfx.LayerView;
import org.mozilla.gecko.home.BrowserSearch;
import org.mozilla.gecko.home.HomeBanner;
import org.mozilla.gecko.home.HomeConfig;
import org.mozilla.gecko.home.HomeConfig.PanelType;
import org.mozilla.gecko.home.HomeConfigPrefsBackend;
import org.mozilla.gecko.home.HomePager;
import org.mozilla.gecko.home.HomePager.OnUrlOpenInBackgroundListener;
import org.mozilla.gecko.home.HomePager.OnUrlOpenListener;
import org.mozilla.gecko.home.HomePanelsManager;
import org.mozilla.gecko.home.SearchEngine;
import org.mozilla.gecko.javaaddons.JavaAddonManager;
import org.mozilla.gecko.menu.GeckoMenu;
import org.mozilla.gecko.menu.GeckoMenuItem;
import org.mozilla.gecko.mozglue.ContextUtils;
import org.mozilla.gecko.mozglue.ContextUtils.SafeIntent;
import org.mozilla.gecko.overlays.ui.ShareDialog;
import org.mozilla.gecko.permissions.Permissions;
import org.mozilla.gecko.preferences.ClearOnShutdownPref;
import org.mozilla.gecko.preferences.GeckoPreferences;
import org.mozilla.gecko.prompts.Prompt;
import org.mozilla.gecko.prompts.PromptListItem;
import org.mozilla.gecko.reader.ReaderModeUtils;
import org.mozilla.gecko.reader.ReadingListHelper;
import org.mozilla.gecko.restrictions.Restrictable;
import org.mozilla.gecko.restrictions.RestrictedProfileConfiguration;
import org.mozilla.gecko.restrictions.Restrictions;
import org.mozilla.gecko.search.SearchEngineManager;
import org.mozilla.gecko.sync.repositories.android.FennecTabsRepository;
import org.mozilla.gecko.tabqueue.TabQueueHelper;
import org.mozilla.gecko.tabqueue.TabQueuePrompt;
import org.mozilla.gecko.tabs.TabHistoryController;
import org.mozilla.gecko.tabs.TabHistoryController.OnShowTabHistory;
import org.mozilla.gecko.tabs.TabHistoryFragment;
import org.mozilla.gecko.tabs.TabHistoryPage;
import org.mozilla.gecko.tabs.TabsPanel;
import org.mozilla.gecko.telemetry.TelemetryConstants;
import org.mozilla.gecko.telemetry.TelemetryUploadService;
import org.mozilla.gecko.toolbar.AutocompleteHandler;
import org.mozilla.gecko.toolbar.BrowserToolbar;
import org.mozilla.gecko.toolbar.BrowserToolbar.TabEditingState;
import org.mozilla.gecko.toolbar.ToolbarProgressView;
import org.mozilla.gecko.trackingprotection.TrackingProtectionPrompt;
import org.mozilla.gecko.updater.UpdateServiceHelper;
import org.mozilla.gecko.util.ActivityUtils;
import org.mozilla.gecko.util.Clipboard;
import org.mozilla.gecko.util.EventCallback;
import org.mozilla.gecko.util.Experiments;
import org.mozilla.gecko.util.FloatUtils;
import org.mozilla.gecko.util.GamepadUtils;
import org.mozilla.gecko.util.GeckoEventListener;
import org.mozilla.gecko.util.HardwareUtils;
import org.mozilla.gecko.util.MenuUtils;
import org.mozilla.gecko.util.NativeEventListener;
import org.mozilla.gecko.util.NativeJSObject;
import org.mozilla.gecko.util.PrefUtils;
import org.mozilla.gecko.util.StringUtils;
import org.mozilla.gecko.util.ThreadUtils;
import org.mozilla.gecko.widget.AnchoredPopup;

import org.mozilla.gecko.widget.GeckoActionProvider;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.MenuItemCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ViewFlipper;
import com.keepsafe.switchboard.AsyncConfigLoader;
import com.keepsafe.switchboard.SwitchBoard;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;
import java.util.regex.Pattern;

public class BrowserApp extends GeckoApp
                        implements TabsPanel.TabsLayoutChangeListener,
                                   PropertyAnimator.PropertyAnimationListener,
                                   View.OnKeyListener,
                                   LayerView.DynamicToolbarListener,
                                   BrowserSearch.OnSearchListener,
                                   BrowserSearch.OnEditSuggestionListener,
                                   OnUrlOpenListener,
                                   OnUrlOpenInBackgroundListener,
                                   ReadingListHelper.OnReadingListEventListener,
                                   AnchoredPopup.OnVisibilityChangeListener,
                                   ActionModeCompat.Presenter,
                                   LayoutInflater.Factory {
    private static final String LOGTAG = "GeckoBrowserApp";

    private static final int TABS_ANIMATION_DURATION = 450;

    public static final String GUEST_BROWSING_ARG = "--guest";

    // Intent String extras used to specify custom Switchboard configurations.
    private static final String INTENT_KEY_SWITCHBOARD_UUID = "switchboard-uuid";
    private static final String INTENT_KEY_SWITCHBOARD_HOST = "switchboard-host";

    private static final String DEFAULT_SWITCHBOARD_HOST = "switchboard.services.mozilla.com";

    private static final String STATE_ABOUT_HOME_TOP_PADDING = "abouthome_top_padding";

    private static final String BROWSER_SEARCH_TAG = "browser_search";

    // Request ID for startActivityForResult.
    private static final int ACTIVITY_REQUEST_PREFERENCES = 1001;
    private static final int ACTIVITY_REQUEST_TAB_QUEUE = 2001;

    public static final String ACTION_VIEW_MULTIPLE = AppConstants.ANDROID_PACKAGE_NAME + ".action.VIEW_MULTIPLE";

    @RobocopTarget
    public static final String EXTRA_SKIP_STARTPANE = "skipstartpane";
    private static final String EOL_NOTIFIED = "eol_notified";

    private BrowserSearch mBrowserSearch;
    private View mBrowserSearchContainer;

    public ViewGroup mBrowserChrome;
    public ViewFlipper mActionBarFlipper;
    public ActionModeCompatView mActionBar;
    private BrowserToolbar mBrowserToolbar;
    private View mDoorhangerOverlay;
    // We can't name the TabStrip class because it's not included on API 9.
    private TabStripInterface mTabStrip;
    private ToolbarProgressView mProgressView;
    private FirstrunAnimationContainer mFirstrunAnimationContainer;
    private HomePager mHomePager;
    private TabsPanel mTabsPanel;
    private ViewGroup mHomePagerContainer;
    private ActionModeCompat mActionMode;
    private TabHistoryController tabHistoryController;
    private ZoomedView mZoomedView;

    private static final int GECKO_TOOLS_MENU = -1;
    private static final int ADDON_MENU_OFFSET = 1000;
    public static final String TAB_HISTORY_FRAGMENT_TAG = "tabHistoryFragment";

    private static class MenuItemInfo {
        public int id;
        public String label;
        public boolean checkable;
        public boolean checked;
        public boolean enabled = true;
        public boolean visible = true;
        public int parent;
        public boolean added;   // So we can re-add after a locale change.
    }

    // The types of guest mode dialogs we show.
    public static enum GuestModeDialog {
        ENTERING,
        LEAVING
    }

    private Vector<MenuItemInfo> mAddonMenuItemsCache;
    private PropertyAnimator mMainLayoutAnimator;

    private static final Interpolator sTabsInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private FindInPageBar mFindInPageBar;
    private MediaCastingBar mMediaCastingBar;

    // We'll ask for feedback after the user launches the app this many times.
    private static final int FEEDBACK_LAUNCH_COUNT = 15;

    // Stored value of the toolbar height, so we know when it's changed.
    private int mToolbarHeight;

    private SharedPreferencesHelper mSharedPreferencesHelper;

    private OrderedBroadcastHelper mOrderedBroadcastHelper;

    private ReadingListHelper mReadingListHelper;

    private AccountsHelper mAccountsHelper;

    // The tab to be selected on editing mode exit.
    private Integer mTargetTabForEditingMode;

    private final TabEditingState mLastTabEditingState = new TabEditingState();

    // The animator used to toggle HomePager visibility has a race where if the HomePager is shown
    // (starting the animation), the HomePager is hidden, and the HomePager animation completes,
    // both the web content and the HomePager will be hidden. This flag is used to prevent the
    // race by determining if the web content should be hidden at the animation's end.
    private boolean mHideWebContentOnAnimationEnd;

    private final DynamicToolbar mDynamicToolbar = new DynamicToolbar();
    private final ScreenshotObserver mScreenshotObserver = new ScreenshotObserver();

    @NonNull
    private SearchEngineManager searchEngineManager; // Contains reference to Context - DO NOT LEAK!

    @Override
    public View onCreateView(final String name, final Context context, final AttributeSet attrs) {
        final View view;
        if (BrowserToolbar.class.getName().equals(name)) {
            view = BrowserToolbar.create(context, attrs);
        } else if (TabsPanel.TabsLayout.class.getName().equals(name)) {
            view = TabsPanel.createTabsLayout(context, attrs);
        } else {
            view = super.onCreateView(name, context, attrs);
        }
        return view;
    }

    @Override
    public void onTabChanged(Tab tab, Tabs.TabEvents msg, Object data) {
        if (tab == null) {
            // Only RESTORED is allowed a null tab: it's the only event that
            // isn't tied to a specific tab.
            if (msg != Tabs.TabEvents.RESTORED) {
                throw new IllegalArgumentException("onTabChanged:" + msg + " must specify a tab.");
            }
            return;
        }

        Log.d(LOGTAG, "BrowserApp.onTabChanged: " + tab.getId() + ": " + msg);
        switch(msg) {
            case SELECTED:
                if (Tabs.getInstance().isSelectedTab(tab) && mDynamicToolbar.isEnabled()) {
                    mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);
                }
                // fall through
            case LOCATION_CHANGE:
                if (mZoomedView != null) {
                    mZoomedView.stopZoomDisplay(false);
                }
                if (Tabs.getInstance().isSelectedTab(tab)) {
                    updateHomePagerForTab(tab);
                }

                mDynamicToolbar.persistTemporaryVisibility();
                break;
            case START:
                if (Tabs.getInstance().isSelectedTab(tab)) {
                    invalidateOptionsMenu();

                    if (mDynamicToolbar.isEnabled()) {
                        mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);
                    }
                }
                break;
            case LOAD_ERROR:
            case STOP:
            case MENU_UPDATED:
                if (Tabs.getInstance().isSelectedTab(tab)) {
                    invalidateOptionsMenu();
                }
                break;
            case PAGE_SHOW:
                tab.loadFavicon();
                break;
            case BOOKMARK_ADDED:
                showBookmarkAddedSnackbar();
                break;
            case BOOKMARK_REMOVED:
                showBookmarkRemovedSnackbar();
                break;
            case READING_LIST_ADDED:
                onAddedToReadingList(tab.getURL());
                break;
            case READING_LIST_REMOVED:
                onRemovedFromReadingList(tab.getURL());
                break;

            case UNSELECTED:
                // We receive UNSELECTED immediately after the SELECTED listeners run
                // so we are ensured that the unselectedTabEditingText has not changed.
                if (tab.isEditing()) {
                    // Copy to avoid constructing new objects.
                    tab.getEditingState().copyFrom(mLastTabEditingState);
                }
                break;
        }

        if (HardwareUtils.isTablet() && msg == TabEvents.SELECTED) {
            updateEditingModeForTab(tab);
        }

        super.onTabChanged(tab, msg, data);
    }

    private void updateEditingModeForTab(final Tab selectedTab) {
        // (bug 1086983 comment 11) Because the tab may be selected from the gecko thread and we're
        // running this code on the UI thread, the selected tab argument may not still refer to the
        // selected tab. However, that means this code should be run again and the initial state
        // changes will be overridden. As an optimization, we can skip this update, but it may have
        // unknown side-effects so we don't.
        if (!Tabs.getInstance().isSelectedTab(selectedTab)) {
            Log.w(LOGTAG, "updateEditingModeForTab: Given tab is expected to be selected tab");
        }

        saveTabEditingState(mLastTabEditingState);

        if (selectedTab.isEditing()) {
            enterEditingMode();
            restoreTabEditingState(selectedTab.getEditingState());
        } else {
            mBrowserToolbar.cancelEdit();
        }
    }

    private void saveTabEditingState(final TabEditingState editingState) {
        mBrowserToolbar.saveTabEditingState(editingState);
        editingState.setIsBrowserSearchShown(mBrowserSearch.getUserVisibleHint());
    }

    private void restoreTabEditingState(final TabEditingState editingState) {
        mBrowserToolbar.restoreTabEditingState(editingState);

        // Since changing the editing text will show/hide browser search, this
        // must be called after we restore the editing state in the edit text View.
        if (editingState.isBrowserSearchShown()) {
            showBrowserSearch();
        } else {
            hideBrowserSearch();
        }
    }

    private void showBookmarkAddedSnackbar() {
        // This flow is from the option menu which has check to see if a bookmark was already added.
        // So, it is safe here to show the snackbar that bookmark_added without any checks.

        final SnackbarHelper.SnackbarCallback callback = new SnackbarHelper.SnackbarCallback() {
            @Override
            public void onClick(View v) {
                Telemetry.sendUIEvent(TelemetryContract.Event.SHOW, TelemetryContract.Method.TOAST, "bookmark_options");
                showBookmarkDialog();
            }
        };

        SnackbarHelper.showSnackbarWithAction(this,
                getResources().getString(R.string.bookmark_added),
                Snackbar.LENGTH_LONG,
                getResources().getString(R.string.bookmark_options),
                callback);
    }

    private void showBookmarkRemovedSnackbar() {
        SnackbarHelper.showSnackbar(this, getResources().getString(R.string.bookmark_removed), Snackbar.LENGTH_LONG);
    }

    private void showSwitchToReadingListSnackbar(String message) {
        final SnackbarHelper.SnackbarCallback callback = new SnackbarHelper.SnackbarCallback() {
            @Override
            public void onClick(View v) {
                Telemetry.sendUIEvent(TelemetryContract.Event.SHOW, TelemetryContract.Method.TOAST, "reading_list");

                final String aboutPageUrl = AboutPages.getURLForBuiltinPanelType(PanelType.READING_LIST);
                Tabs.getInstance().loadUrlInTab(aboutPageUrl);
            }
        };

        SnackbarHelper.showSnackbarWithAction(this,
                message,
                Snackbar.LENGTH_LONG,
                getResources().getString(R.string.switch_button_message),
                callback);
    }

    public void onAddedToReadingList(String url) {
        showSwitchToReadingListSnackbar(getResources().getString(R.string.reading_list_added));
    }

    public void onAlreadyInReadingList(String url) {
        showSwitchToReadingListSnackbar(getResources().getString(R.string.reading_list_duplicate));
    }

    public void onRemovedFromReadingList(String url) {
        SnackbarHelper.showSnackbar(this,
                getResources().getString(R.string.reading_list_removed),
                Snackbar.LENGTH_LONG);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (AndroidGamepadManager.handleKeyEvent(event)) {
            return true;
        }

        // Global onKey handler. This is called if the focused UI doesn't
        // handle the key event, and before Gecko swallows the events.
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BUTTON_Y:
                    // Toggle/focus the address bar on gamepad-y button.
                    if (mBrowserChrome.getVisibility() == View.VISIBLE) {
                        if (mDynamicToolbar.isEnabled() && !isHomePagerVisible()) {
                            mDynamicToolbar.setVisible(false, VisibilityTransition.ANIMATE);
                            if (mLayerView != null) {
                                mLayerView.requestFocus();
                            }
                        } else {
                            // Just focus the address bar when about:home is visible
                            // or when the dynamic toolbar isn't enabled.
                            mBrowserToolbar.requestFocusFromTouch();
                        }
                    } else {
                        mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);
                        mBrowserToolbar.requestFocusFromTouch();
                    }
                    return true;
                case KeyEvent.KEYCODE_BUTTON_L1:
                    // Go back on L1
                    Tabs.getInstance().getSelectedTab().doBack();
                    return true;
                case KeyEvent.KEYCODE_BUTTON_R1:
                    // Go forward on R1
                    Tabs.getInstance().getSelectedTab().doForward();
                    return true;
            }
        }

        // Check if this was a shortcut. Meta keys exists only on 11+.
        final Tab tab = Tabs.getInstance().getSelectedTab();
        if (Versions.feature11Plus && tab != null && event.isCtrlPressed()) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_LEFT_BRACKET:
                    tab.doBack();
                    return true;

                case KeyEvent.KEYCODE_RIGHT_BRACKET:
                    tab.doForward();
                    return true;

                case KeyEvent.KEYCODE_R:
                    tab.doReload(false);
                    return true;

                case KeyEvent.KEYCODE_PERIOD:
                    tab.doStop();
                    return true;

                case KeyEvent.KEYCODE_T:
                    addTab();
                    return true;

                case KeyEvent.KEYCODE_W:
                    Tabs.getInstance().closeTab(tab);
                    return true;

                case KeyEvent.KEYCODE_F:
                    mFindInPageBar.show();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!mBrowserToolbar.isEditing() && onKey(null, keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (AndroidGamepadManager.handleKeyEvent(event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (!HardwareUtils.isSupportedSystem()) {
            // This build does not support the Android version of the device; Exit early.
            super.onCreate(savedInstanceState);
            return;
        }

        final Intent intent = getIntent();

        // Note that we're calling GeckoProfile.get *before GeckoApp.onCreate*.
        // This means we're reliant on the logic in GeckoProfile to correctly
        // look up our launch intent (via BrowserApp's Activity-ness) and pull
        // out the arguments. Be careful if you change that!
        final GeckoProfile p = GeckoProfile.get(this);

        if (p != null && !p.inGuestMode()) {
            // This is *only* valid because we never want to use the guest mode
            // profile concurrently with a normal profile -- no syncing to it,
            // no dual-profile usage, nothing. BrowserApp startup with a conventional
            // GeckoProfile will cause the guest profile to be deleted.
            GeckoProfile.maybeCleanupGuestProfile(this);
        }

        // This has to be prepared prior to calling GeckoApp.onCreate, because
        // widget code and BrowserToolbar need it, and they're created by the
        // layout, which GeckoApp takes care of.
        ((GeckoApplication) getApplication()).prepareLightweightTheme();
        super.onCreate(savedInstanceState);

        final Context appContext = getApplicationContext();

        initSwitchboard(intent);

        mBrowserChrome = (ViewGroup) findViewById(R.id.browser_chrome);
        mActionBarFlipper = (ViewFlipper) findViewById(R.id.browser_actionbar);
        mActionBar = (ActionModeCompatView) findViewById(R.id.actionbar);

        mBrowserToolbar = (BrowserToolbar) findViewById(R.id.browser_toolbar);
        mProgressView = (ToolbarProgressView) findViewById(R.id.progress);
        mBrowserToolbar.setProgressBar(mProgressView);

        // Initialize Tab History Controller.
        tabHistoryController = new TabHistoryController(new OnShowTabHistory() {
            @Override
            public void onShowHistory(final List<TabHistoryPage> historyPageList, final int toIndex) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final TabHistoryFragment fragment = TabHistoryFragment.newInstance(historyPageList, toIndex);
                        final FragmentManager fragmentManager = getSupportFragmentManager();
                        GeckoAppShell.vibrateOnHapticFeedbackEnabled(getResources().getIntArray(R.array.long_press_vibrate_msec));
                        fragment.show(R.id.tab_history_panel, fragmentManager.beginTransaction(), TAB_HISTORY_FRAGMENT_TAG);
                    }
                });
            }
        });
        mBrowserToolbar.setTabHistoryController(tabHistoryController);

        final String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            // Show the target URL immediately in the toolbar.
            mBrowserToolbar.setTitle(intent.getDataString());

            showTabQueuePromptIfApplicable(intent);
        } else if (GuestSession.NOTIFICATION_INTENT.equals(action)) {
            GuestSession.handleIntent(this, intent);
        } else if (TabQueueHelper.LOAD_URLS_ACTION.equals(action)) {
            Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.NOTIFICATION, "tabqueue");
        }

        if (HardwareUtils.isTablet()) {
            mTabStrip = (TabStripInterface) (((ViewStub) findViewById(R.id.tablet_tab_strip)).inflate());
        }

        ((GeckoApp.MainLayout) mMainLayout).setTouchEventInterceptor(new HideOnTouchListener());
        ((GeckoApp.MainLayout) mMainLayout).setMotionEventInterceptor(new MotionEventInterceptor() {
            @Override
            public boolean onInterceptMotionEvent(View view, MotionEvent event) {
                // If we get a gamepad panning MotionEvent while the focus is not on the layerview,
                // put the focus on the layerview and carry on
                if (mLayerView != null && !mLayerView.hasFocus() && GamepadUtils.isPanningControl(event)) {
                    if (mHomePager == null) {
                        return false;
                    }

                    if (isHomePagerVisible()) {
                        mLayerView.requestFocus();
                    } else {
                        mHomePager.requestFocus();
                    }
                }
                return false;
            }
        });

        mHomePagerContainer = (ViewGroup) findViewById(R.id.home_pager_container);

        mBrowserSearchContainer = findViewById(R.id.search_container);
        mBrowserSearch = (BrowserSearch) getSupportFragmentManager().findFragmentByTag(BROWSER_SEARCH_TAG);
        if (mBrowserSearch == null) {
            mBrowserSearch = BrowserSearch.newInstance();
            mBrowserSearch.setUserVisibleHint(false);
        }

        setBrowserToolbarListeners();

        mFindInPageBar = (FindInPageBar) findViewById(R.id.find_in_page);
        mMediaCastingBar = (MediaCastingBar) findViewById(R.id.media_casting);

        EventDispatcher.getInstance().registerGeckoThreadListener((GeckoEventListener)this,
            "Gecko:DelayedStartup",
            "Menu:Open",
            "Menu:Update",
            "LightweightTheme:Update",
            "Search:Keyword",
            "Prompt:ShowTop");

        EventDispatcher.getInstance().registerGeckoThreadListener((NativeEventListener)this,
            "CharEncoding:Data",
            "CharEncoding:State",
            "Download:AndroidDownloadManager",
            "Experiments:GetActive",
            "Favicon:CacheLoad",
            "Feedback:MaybeLater",
            "Menu:Add",
            "Menu:Remove",
            "Sanitize:ClearHistory",
            "Sanitize:ClearSyncedTabs",
            "Settings:Show",
            "Telemetry:Gather",
            "Updater:Launch");

        // We want to upload the telemetry core ping as soon after startup as possible. It relies on the
        // Distribution being initialized. If you move this initialization, ensure it plays well with telemetry.
        Distribution distribution = Distribution.init(this);
        searchEngineManager = new SearchEngineManager(this, distribution);

        // Init suggested sites engine in BrowserDB.
        final SuggestedSites suggestedSites = new SuggestedSites(appContext, distribution);
        final BrowserDB db = getProfile().getDB();
        db.setSuggestedSites(suggestedSites);

        JavaAddonManager.getInstance().init(appContext);
        mSharedPreferencesHelper = new SharedPreferencesHelper(appContext);
        mOrderedBroadcastHelper = new OrderedBroadcastHelper(appContext);
        mReadingListHelper = new ReadingListHelper(appContext, getProfile(), this);
        mAccountsHelper = new AccountsHelper(appContext, getProfile());

        final AdjustHelperInterface adjustHelper = AdjustConstants.getAdjustHelper();
        adjustHelper.onCreate(this, AdjustConstants.MOZ_INSTALL_TRACKING_ADJUST_SDK_APP_TOKEN);

        // Adjust stores enabled state so this is only necessary because users may have set
        // their data preferences before this feature was implemented and we need to respect
        // those before upload can occur in Adjust.onResume.
        final SharedPreferences prefs = GeckoSharedPrefs.forApp(this);
        adjustHelper.setEnabled(prefs.getBoolean(GeckoPreferences.PREFS_HEALTHREPORT_UPLOAD_ENABLED, true));

        if (AppConstants.MOZ_ANDROID_BEAM) {
            NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
            if (nfc != null) {
                nfc.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
                    @Override
                    public NdefMessage createNdefMessage(NfcEvent event) {
                        Tab tab = Tabs.getInstance().getSelectedTab();
                        if (tab == null || tab.isPrivate()) {
                            return null;
                        }
                        return new NdefMessage(new NdefRecord[] { NdefRecord.createUri(tab.getURL()) });
                    }
                }, this);
            }
        }

        if (savedInstanceState != null) {
            mDynamicToolbar.onRestoreInstanceState(savedInstanceState);
            mHomePagerContainer.setPadding(0, savedInstanceState.getInt(STATE_ABOUT_HOME_TOP_PADDING), 0, 0);
        }

        mDynamicToolbar.setEnabledChangedListener(new DynamicToolbar.OnEnabledChangedListener() {
            @Override
            public void onEnabledChanged(boolean enabled) {
                setDynamicToolbarEnabled(enabled);
            }
        });

        // Watch for screenshots while browser is in foreground.
        mScreenshotObserver.setListener(getContext(), new ScreenshotObserver.OnScreenshotListener() {
            @Override
            public void onScreenshotTaken(final String screenshotPath, final String title) {
                // Treat screenshots as a sharing method.
                Telemetry.sendUIEvent(TelemetryContract.Event.SHARE, TelemetryContract.Method.BUTTON, "screenshot");

                if (!AppConstants.SCREENSHOTS_IN_BOOKMARKS_ENABLED) {
                    return;
                }

                final Tab selectedTab = Tabs.getInstance().getSelectedTab();
                if (selectedTab == null) {
                    Log.w(LOGTAG, "Selected tab is null: could not page info to store screenshot.");
                    return;
                }

                getProfile().getDB().getUrlAnnotations().insertScreenshot(
                        getContentResolver(), selectedTab.getURL(), screenshotPath);
                SnackbarHelper.showSnackbar(BrowserApp.this,
                        getResources().getString(R.string.screenshot_added_to_bookmarks), Snackbar.LENGTH_SHORT);
            }
        });

        // Set the maximum bits-per-pixel the favicon system cares about.
        IconDirectoryEntry.setMaxBPP(GeckoAppShell.getScreenDepth());

        // The update service is enabled for RELEASE_BUILD, which includes the release and beta channels.
        // However, no updates are served.  Therefore, we don't trust the update service directly, and
        // try to avoid prompting unnecessarily. See Bug 1232798.
        if (!AppConstants.RELEASE_BUILD && UpdateServiceHelper.isUpdaterEnabled()) {
            Permissions.from(this)
                       .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                       .doNotPrompt()
                       .andFallback(new Runnable() {
                           @Override
                           public void run() {
                               showUpdaterPermissionSnackbar();
                           }
                       })
                      .run();
        }
    }

    /**
     * Initializes the default Switchboard URLs the first time.
     * @param intent
     */
    private void initSwitchboard(Intent intent) {
        if (Experiments.isDisabled(new SafeIntent(intent)) || !AppConstants.MOZ_SWITCHBOARD) {
            return;
        }

        final String hostExtra = ContextUtils.getStringExtra(intent, INTENT_KEY_SWITCHBOARD_HOST);
        final String host = TextUtils.isEmpty(hostExtra) ? DEFAULT_SWITCHBOARD_HOST : hostExtra;

        final String configServerUpdateUrl;
        final String configServerUrl;
        try {
            configServerUpdateUrl = new URL("https", host, "urls").toString();
            configServerUrl = new URL("https", host, "v1").toString();
        } catch (MalformedURLException e) {
            Log.e(LOGTAG, "Error creating Switchboard server URL", e);
            return;
        }

        SwitchBoard.initDefaultServerUrls(configServerUpdateUrl, configServerUrl, true);

        final String switchboardUUID = ContextUtils.getStringExtra(intent, INTENT_KEY_SWITCHBOARD_UUID);
        SwitchBoard.setUUIDFromExtra(switchboardUUID);

        // Looks at the server if there are changes in the server URL that should be used in the future
        new AsyncConfigLoader(this, AsyncConfigLoader.UPDATE_SERVER, switchboardUUID).execute();

        // Loads the actual config. This can be done on app start or on app onResume() depending
        // how often you want to update the config.
        new AsyncConfigLoader(this, AsyncConfigLoader.CONFIG_SERVER, switchboardUUID).execute();
    }

    private void showUpdaterPermissionSnackbar() {
        SnackbarHelper.SnackbarCallback allowCallback = new SnackbarHelper.SnackbarCallback() {
            @Override
            public void onClick(View v) {
                Permissions.from(BrowserApp.this)
                        .withPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        .run();
            }
        };

        SnackbarHelper.showSnackbarWithAction(this,
                getString(R.string.updater_permission_text),
                Snackbar.LENGTH_INDEFINITE,
                getString(R.string.updater_permission_allow),
                allowCallback);
    }

    private void conditionallyNotifyEOL() {
        final StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskReads();
        try {
            final SharedPreferences prefs = GeckoSharedPrefs.forProfile(this);
            if (!prefs.contains(EOL_NOTIFIED)) {

                // Launch main App to load SUMO url on EOL notification.
                final String link = getString(R.string.eol_notification_url,
                                              AppConstants.MOZ_APP_VERSION,
                                              AppConstants.OS_TARGET,
                                              Locales.getLanguageTag(Locale.getDefault()));

                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setClassName(AppConstants.ANDROID_PACKAGE_NAME, AppConstants.MOZ_ANDROID_BROWSER_INTENT_CLASS);
                intent.setData(Uri.parse(link));
                final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                final Notification notification = new NotificationCompat.Builder(this)
                        .setContentTitle(getString(R.string.eol_notification_title))
                        .setContentText(getString(R.string.eol_notification_summary))
                        .setSmallIcon(R.drawable.ic_status_logo)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .build();

                final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                final int notificationID = EOL_NOTIFIED.hashCode();
                notificationManager.notify(notificationID, notification);

                GeckoSharedPrefs.forProfile(this)
                                .edit()
                                .putBoolean(EOL_NOTIFIED, true)
                                .apply();
            }
        } finally {
            StrictMode.setThreadPolicy(savedPolicy);
        }
    }

    /**
     * Check and show the firstrun pane if the browser has never been launched and
     * is not opening an external link from another application.
     *
     * @param context Context of application; used to show firstrun pane if appropriate
     * @param intent Intent that launched this activity
     */
    private void checkFirstrun(Context context, SafeIntent intent) {
        if (intent.getBooleanExtra(EXTRA_SKIP_STARTPANE, false)) {
            // Note that we don't set the pref, so subsequent launches can result
            // in the firstrun pane being shown.
            return;
        }
        final StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskReads();

        try {
            final SharedPreferences prefs = GeckoSharedPrefs.forProfile(this);

            if (prefs.getBoolean(FirstrunAnimationContainer.PREF_FIRSTRUN_ENABLED, false)) {
                if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
                    showFirstrunPager();

                    if (HardwareUtils.isTablet()) {
                        mTabStrip.setOnTabChangedListener(new TabStripInterface.OnTabAddedOrRemovedListener() {
                            @Override
                            public void onTabChanged() {
                                hideFirstrunPager(TelemetryContract.Method.BUTTON);
                                mTabStrip.setOnTabChangedListener(null);
                            }
                        });
                    }
                }
                // Don't bother trying again to show the v1 minimal first run.
                prefs.edit().putBoolean(FirstrunAnimationContainer.PREF_FIRSTRUN_ENABLED, false).apply();
            }
        } finally {
            StrictMode.setThreadPolicy(savedPolicy);
        }
    }

    private Class<?> getMediaPlayerManager() {
        if (AppConstants.MOZ_MEDIA_PLAYER) {
            try {
                return Class.forName("org.mozilla.gecko.MediaPlayerManager");
            } catch(Exception ex) {
                // Ignore failures
                Log.e(LOGTAG, "No native casting support", ex);
            }
        }

        return null;
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
            return;
        }

        if (mBrowserToolbar.onBackPressed()) {
            return;
        }

        if (mActionMode != null) {
            endActionModeCompat();
            return;
        }

        if (hideFirstrunPager(TelemetryContract.Method.BACK)) {
            return;
        }

        super.onBackPressed();
    }

    @Override
    public void onAttachedToWindow() {
        mDoorhangerOverlay = findViewById(R.id.doorhanger_overlay);
        mDoorhangerOverlay.setVisibility(View.VISIBLE);

        // We can't show the first run experience until Gecko has finished initialization (bug 1077583).
        checkFirstrun(this, new SafeIntent(getIntent()));
    }

    @Override
    protected void processTabQueue() {
        if (TabQueueHelper.TAB_QUEUE_ENABLED && mInitialized) {
            ThreadUtils.postToBackgroundThread(new Runnable() {
                @Override
                public void run() {
                    if (TabQueueHelper.shouldOpenTabQueueUrls(BrowserApp.this)) {
                        openQueuedTabs();
                    }
                }
            });
        }
    }

    @Override
    protected void openQueuedTabs() {
        ThreadUtils.assertNotOnUiThread();

        int queuedTabCount = TabQueueHelper.getTabQueueLength(BrowserApp.this);

        Telemetry.addToHistogram("FENNEC_TABQUEUE_QUEUESIZE", queuedTabCount);
        Telemetry.sendUIEvent(TelemetryContract.Event.LOAD_URL, TelemetryContract.Method.INTENT, "tabqueue-delayed");

        TabQueueHelper.openQueuedUrls(BrowserApp.this, mProfile, TabQueueHelper.FILE_NAME, false);

        // If there's more than one tab then also show the tabs panel.
        if (queuedTabCount > 1) {
            ThreadUtils.postToUiThread(new Runnable() {
                @Override
                public void run() {
                    showNormalTabs();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Needed for Adjust to get accurate session measurements
        AdjustConstants.getAdjustHelper().onResume();

        final String args = ContextUtils.getStringExtra(getIntent(), "args");
        // If an external intent tries to start Fennec in guest mode, and it's not already
        // in guest mode, this will change modes before opening the url.
        // NOTE: OnResume is called twice sometimes when showing on the lock screen.
        final boolean enableGuestSession = GuestSession.shouldUse(this, args);
        final boolean inGuestSession = GeckoProfile.get(this).inGuestMode();
        if (enableGuestSession != inGuestSession) {
            doRestart(getIntent());
            return;
        }

        EventDispatcher.getInstance().unregisterGeckoThreadListener((GeckoEventListener) this,
            "Prompt:ShowTop");

        processTabQueue();

        mScreenshotObserver.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Needed for Adjust to get accurate session measurements
        AdjustConstants.getAdjustHelper().onPause();

        // Register for Prompt:ShowTop so we can foreground this activity even if it's hidden.
        EventDispatcher.getInstance().registerGeckoThreadListener((GeckoEventListener) this,
            "Prompt:ShowTop");

        mScreenshotObserver.stop();
    }

    @Override
    public void onStart() {
        super.onStart();

        // Queue this work so that the first launch of the activity doesn't
        // trigger profile init too early.
        ThreadUtils.postToBackgroundThread(new Runnable() {
            @Override
            public void run() {
                final GeckoProfile profile = getProfile();
                if (profile.inGuestMode()) {
                    GuestSession.showNotification(BrowserApp.this);
                } else {
                    // If we're restarting, we won't destroy the activity.
                    // Make sure we remove any guest notifications that might
                    // have been shown.
                    GuestSession.hideNotification(BrowserApp.this);
                }
            }
        });

        // We don't upload in onCreate because that's only called when the Activity needs to be instantiated
        // and it's possible the system will never free the Activity from memory.
        //
        // We don't upload in onResume/onPause because that will be called each time the Activity is obscured,
        // including by our own Activities/dialogs, and there is no reason to upload each time we're unobscured.
        //
        // So we're left with onStart/onStop.
        searchEngineManager.getEngine(new UploadTelemetryCallback(BrowserApp.this));
    }

    @Override
    public void onStop() {
        super.onStop();

        // We only show the guest mode notification when our activity is in the foreground.
        GuestSession.hideNotification(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Sending a message to the toolbar when the browser window gains focus
        // This is needed for qr code input
        if (hasFocus) {
            mBrowserToolbar.onParentFocus();
        }
    }

    private void setBrowserToolbarListeners() {
        mBrowserToolbar.setOnActivateListener(new BrowserToolbar.OnActivateListener() {
            @Override
            public void onActivate() {
                enterEditingMode();
            }
        });

        mBrowserToolbar.setOnCommitListener(new BrowserToolbar.OnCommitListener() {
            @Override
            public void onCommit() {
                commitEditingMode();
            }
        });

        mBrowserToolbar.setOnDismissListener(new BrowserToolbar.OnDismissListener() {
            @Override
            public void onDismiss() {
                mBrowserToolbar.cancelEdit();
            }
        });

        mBrowserToolbar.setOnFilterListener(new BrowserToolbar.OnFilterListener() {
            @Override
            public void onFilter(String searchText, AutocompleteHandler handler) {
                filterEditingMode(searchText, handler);
            }
        });

        mBrowserToolbar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (isHomePagerVisible()) {
                    mHomePager.onToolbarFocusChange(hasFocus);
                }
            }
        });

        mBrowserToolbar.setOnStartEditingListener(new BrowserToolbar.OnStartEditingListener() {
            @Override
            public void onStartEditing() {
                final Tab selectedTab = Tabs.getInstance().getSelectedTab();
                if (selectedTab != null) {
                    selectedTab.setIsEditing(true);
                }

                // Temporarily disable doorhanger notifications.
                if (mDoorHangerPopup != null) {
                    mDoorHangerPopup.disable();
                }
            }
        });

        mBrowserToolbar.setOnStopEditingListener(new BrowserToolbar.OnStopEditingListener() {
            @Override
            public void onStopEditing() {
                final Tab selectedTab = Tabs.getInstance().getSelectedTab();
                if (selectedTab != null) {
                    selectedTab.setIsEditing(false);
                }

                selectTargetTabForEditingMode();

                // Since the underlying LayerView is set visible in hideHomePager, we would
                // ordinarily want to call it first. However, hideBrowserSearch changes the
                // visibility of the HomePager and hideHomePager will take no action if the
                // HomePager is hidden, so we want to call hideBrowserSearch to restore the
                // HomePager visibility first.
                hideBrowserSearch();
                hideHomePager();

                // Re-enable doorhanger notifications. They may trigger on the selected tab above.
                if (mDoorHangerPopup != null) {
                    mDoorHangerPopup.enable();
                }
            }
        });

        // Intercept key events for gamepad shortcuts
        mBrowserToolbar.setOnKeyListener(this);
    }

    private void showBookmarkDialog() {
        final Resources res = getResources();
        final Tab tab = Tabs.getInstance().getSelectedTab();

        final Prompt ps = new Prompt(this, new Prompt.PromptCallback() {
            @Override
            public void onPromptFinished(String result) {
                int itemId = -1;
                try {
                  itemId = new JSONObject(result).getInt("button");
                } catch(JSONException ex) {
                    Log.e(LOGTAG, "Exception reading bookmark prompt result", ex);
                }

                if (tab == null) {
                    return;
                }

                if (itemId == 0) {
                    final String extrasId = res.getResourceEntryName(R.string.contextmenu_edit_bookmark);
                    Telemetry.sendUIEvent(TelemetryContract.Event.ACTION,
                        TelemetryContract.Method.DIALOG, extrasId);

                    new EditBookmarkDialog(BrowserApp.this).show(tab.getURL());
                } else if (itemId == 1) {
                    final String extrasId = res.getResourceEntryName(R.string.contextmenu_add_to_launcher);
                    Telemetry.sendUIEvent(TelemetryContract.Event.ACTION,
                        TelemetryContract.Method.DIALOG, extrasId);

                    final String url = tab.getURL();
                    final String title = tab.getDisplayTitle();

                    if (url != null && title != null) {
                        ThreadUtils.postToBackgroundThread(new Runnable() {
                            @Override
                            public void run() {
                                GeckoAppShell.createShortcut(title, url);
                            }
                        });
                    }
                }
            }
        });

        final PromptListItem[] items = new PromptListItem[2];
        items[0] = new PromptListItem(res.getString(R.string.contextmenu_edit_bookmark));
        items[1] = new PromptListItem(res.getString(R.string.contextmenu_add_to_launcher));

        ps.show("", "", items, ListView.CHOICE_MODE_NONE);
    }

    private void setDynamicToolbarEnabled(boolean enabled) {
        ThreadUtils.assertOnUiThread();

        if (enabled) {
            if (mLayerView != null) {
                mLayerView.getDynamicToolbarAnimator().addTranslationListener(this);
            }
            setToolbarMargin(0);
            mHomePagerContainer.setPadding(0, mBrowserChrome.getHeight(), 0, 0);
        } else {
            // Immediately show the toolbar when disabling the dynamic
            // toolbar.
            if (mLayerView != null) {
                mLayerView.getDynamicToolbarAnimator().removeTranslationListener(this);
            }
            mHomePagerContainer.setPadding(0, 0, 0, 0);
            if (mBrowserChrome != null) {
                ViewHelper.setTranslationY(mBrowserChrome, 0);
            }
            if (mLayerView != null) {
                mLayerView.setSurfaceTranslation(0);
            }
        }

        refreshToolbarHeight();
    }

    private static boolean isAboutHome(final Tab tab) {
        return AboutPages.isAboutHome(tab.getURL());
    }

    @Override
    public boolean onSearchRequested() {
        enterEditingMode();
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.pasteandgo) {
            hideFirstrunPager(TelemetryContract.Method.CONTEXT_MENU);

            String text = Clipboard.getText();
            if (!TextUtils.isEmpty(text)) {
                loadUrlOrKeywordSearch(text);
                Telemetry.sendUIEvent(TelemetryContract.Event.LOAD_URL, TelemetryContract.Method.CONTEXT_MENU);
                Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.CONTEXT_MENU, "pasteandgo");
            }
            return true;
        }

        if (itemId == R.id.paste) {
            String text = Clipboard.getText();
            if (!TextUtils.isEmpty(text)) {
                enterEditingMode(text);
                showBrowserSearch();
                mBrowserSearch.filter(text, null);
                Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.CONTEXT_MENU, "paste");
            }
            return true;
        }

        if (itemId == R.id.subscribe) {
            // This can be selected from either the browser menu or the contextmenu, depending on the size and version (v11+) of the phone.
            Tab tab = Tabs.getInstance().getSelectedTab();
            if (tab != null && tab.hasFeeds()) {
                JSONObject args = new JSONObject();
                try {
                    args.put("tabId", tab.getId());
                } catch (JSONException e) {
                    Log.e(LOGTAG, "error building json arguments", e);
                }
                GeckoAppShell.notifyObservers("Feeds:Subscribe", args.toString());
            }
            return true;
        }

        if (itemId == R.id.add_search_engine) {
            // This can be selected from either the browser menu or the contextmenu, depending on the size and version (v11+) of the phone.
            Tab tab = Tabs.getInstance().getSelectedTab();
            if (tab != null && tab.hasOpenSearch()) {
                JSONObject args = new JSONObject();
                try {
                    args.put("tabId", tab.getId());
                } catch (JSONException e) {
                    Log.e(LOGTAG, "error building json arguments", e);
                    return true;
                }
                GeckoAppShell.notifyObservers("SearchEngines:Add", args.toString());
            }
            return true;
        }

        if (itemId == R.id.copyurl) {
            Tab tab = Tabs.getInstance().getSelectedTab();
            if (tab != null) {
                String url = ReaderModeUtils.stripAboutReaderUrl(tab.getURL());
                if (url != null) {
                    Clipboard.setText(url);
                    Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.CONTEXT_MENU, "copyurl");
                }
            }
            return true;
        }

        if (itemId == R.id.add_to_launcher) {
            final Tab tab = Tabs.getInstance().getSelectedTab();
            if (tab == null) {
                return true;
            }

            final String url = tab.getURL();
            final String title = tab.getDisplayTitle();
            if (url == null || title == null) {
                return true;
            }

            ThreadUtils.postToBackgroundThread(new Runnable() {
                @Override
                public void run() {
                    GeckoAppShell.createShortcut(title, url);

                }
            });

            Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.CONTEXT_MENU,
                getResources().getResourceEntryName(itemId));
            return true;
        }

        return false;
    }

    @Override
    public void setAccessibilityEnabled(boolean enabled) {
        super.setAccessibilityEnabled(enabled);
        mDynamicToolbar.setAccessibilityEnabled(enabled);
    }

    @Override
    public void onDestroy() {
        if (!HardwareUtils.isSupportedSystem()) {
            // This build does not support the Android version of the device; Exit early.
            super.onDestroy();
            return;
        }

        mDynamicToolbar.destroy();

        if (mBrowserToolbar != null)
            mBrowserToolbar.onDestroy();

        if (mFindInPageBar != null) {
            mFindInPageBar.onDestroy();
            mFindInPageBar = null;
        }

        if (mMediaCastingBar != null) {
            mMediaCastingBar.onDestroy();
            mMediaCastingBar = null;
        }

        if (mSharedPreferencesHelper != null) {
            mSharedPreferencesHelper.uninit();
            mSharedPreferencesHelper = null;
        }

        if (mOrderedBroadcastHelper != null) {
            mOrderedBroadcastHelper.uninit();
            mOrderedBroadcastHelper = null;
        }

        if (mReadingListHelper != null) {
            mReadingListHelper.uninit();
            mReadingListHelper = null;
        }

        if (mAccountsHelper != null) {
            mAccountsHelper.uninit();
            mAccountsHelper = null;
        }

        if (mZoomedView != null) {
            mZoomedView.destroy();
        }

        searchEngineManager.unregisterListeners();

        EventDispatcher.getInstance().unregisterGeckoThreadListener((GeckoEventListener) this,
            "Gecko:DelayedStartup",
            "Menu:Open",
            "Menu:Update",
            "LightweightTheme:Update",
            "Search:Keyword",
            "Prompt:ShowTop");

        EventDispatcher.getInstance().unregisterGeckoThreadListener((NativeEventListener) this,
            "CharEncoding:Data",
            "CharEncoding:State",
            "Download:AndroidDownloadManager",
            "Experiments:GetActive",
            "Favicon:CacheLoad",
            "Feedback:MaybeLater",
            "Menu:Add",
            "Menu:Remove",
            "Sanitize:ClearHistory",
            "Sanitize:ClearSyncedTabs",
            "Settings:Show",
            "Telemetry:Gather",
            "Updater:Launch");

        if (AppConstants.MOZ_ANDROID_BEAM) {
            NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
            if (nfc != null) {
                // null this out even though the docs say it's not needed,
                // because the source code looks like it will only do this
                // automatically on API 14+
                nfc.setNdefPushMessageCallback(null, this);
            }
        }

        super.onDestroy();
    }

    @Override
    protected void initializeChrome() {
        super.initializeChrome();

        mDoorHangerPopup.setAnchor(mBrowserToolbar.getDoorHangerAnchor());
        mDoorHangerPopup.setOnVisibilityChangeListener(this);

        mDynamicToolbar.setLayerView(mLayerView);
        setDynamicToolbarEnabled(mDynamicToolbar.isEnabled());

        // Intercept key events for gamepad shortcuts
        mLayerView.setOnKeyListener(this);

        // Initialize the actionbar menu items on startup for both large and small tablets
        if (HardwareUtils.isTablet()) {
            onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, null);
            invalidateOptionsMenu();
        }
    }

    @Override
    public void onDoorHangerShow() {
        mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);

        final Animator alphaAnimator = ObjectAnimator.ofFloat(mDoorhangerOverlay, "alpha", 1);
        alphaAnimator.setDuration(250);

        alphaAnimator.start();
    }

    @Override
    public void onDoorHangerHide() {
        final Animator alphaAnimator = ObjectAnimator.ofFloat(mDoorhangerOverlay, "alpha", 0);
        alphaAnimator.setDuration(200);

        alphaAnimator.start();
    }

    private void handleClearHistory(final boolean clearSearchHistory) {
        final BrowserDB db = getProfile().getDB();
        ThreadUtils.postToBackgroundThread(new Runnable() {
            @Override
            public void run() {
                db.clearHistory(getContentResolver(), clearSearchHistory);
            }
        });
    }

    private void handleClearSyncedTabs() {
        ThreadUtils.postToBackgroundThread(new Runnable() {
            @Override
            public void run() {
                FennecTabsRepository.deleteNonLocalClientsAndTabs(getContext());
            }
        });
    }

    private void setToolbarMargin(int margin) {
        ((RelativeLayout.LayoutParams) mGeckoLayout.getLayoutParams()).topMargin = margin;
        mGeckoLayout.requestLayout();
    }

    @Override
    public void onTranslationChanged(float aToolbarTranslation, float aLayerViewTranslation) {
        if (mBrowserChrome == null) {
            return;
        }

        final View browserChrome = mBrowserChrome;
        final ToolbarProgressView progressView = mProgressView;

        ViewHelper.setTranslationY(browserChrome, -aToolbarTranslation);
        mLayerView.setSurfaceTranslation(mToolbarHeight - aLayerViewTranslation);

        // Stop the progressView from moving all the way up so that we can still see a good chunk of it
        // when the chrome is offscreen.
        final float offset = getResources().getDimensionPixelOffset(R.dimen.progress_bar_scroll_offset);
        final float progressTranslationY = Math.min(aToolbarTranslation, mToolbarHeight - offset);
        ViewHelper.setTranslationY(progressView, -progressTranslationY);

        if (mFormAssistPopup != null) {
            mFormAssistPopup.onTranslationChanged();
        }
    }

    @Override
    public void onMetricsChanged(ImmutableViewportMetrics aMetrics) {
        if (isHomePagerVisible() || mBrowserChrome == null) {
            return;
        }

        if (mFormAssistPopup != null) {
            mFormAssistPopup.onMetricsChanged(aMetrics);
        }
    }

    @Override
    public void onPanZoomStopped() {
        if (!mDynamicToolbar.isEnabled() || isHomePagerVisible() ||
            mBrowserChrome.getVisibility() != View.VISIBLE) {
            return;
        }

        // Make sure the toolbar is fully hidden or fully shown when the user
        // lifts their finger, depending on various conditions.
        ImmutableViewportMetrics metrics = mLayerView.getViewportMetrics();
        float toolbarTranslation = mLayerView.getDynamicToolbarAnimator().getToolbarTranslation();

        boolean shortPage = metrics.getPageHeight() < metrics.getHeight();
        boolean atBottomOfLongPage =
            FloatUtils.fuzzyEquals(metrics.pageRectBottom, metrics.viewportRectBottom())
            && (metrics.pageRectBottom > 2 * metrics.getHeight());
        Log.v(LOGTAG, "On pan/zoom stopped, short page: " + shortPage
            + "; atBottomOfLongPage: " + atBottomOfLongPage);
        if (shortPage || atBottomOfLongPage) {
            mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);
        }
    }

    public void refreshToolbarHeight() {
        ThreadUtils.assertOnUiThread();

        int height = 0;
        if (mBrowserChrome != null) {
            height = mBrowserChrome.getHeight();
        }

        if (!mDynamicToolbar.isEnabled() || isHomePagerVisible()) {
            // Use aVisibleHeight here so that when the dynamic toolbar is
            // enabled, the padding will animate with the toolbar becoming
            // visible.
            if (mDynamicToolbar.isEnabled()) {
                // When the dynamic toolbar is enabled, set the padding on the
                // about:home widget directly - this is to avoid resizing the
                // LayerView, which can cause visible artifacts.
                mHomePagerContainer.setPadding(0, height, 0, 0);
            } else {
                setToolbarMargin(height);
                height = 0;
            }
        } else {
            setToolbarMargin(0);
        }

        if (mLayerView != null && height != mToolbarHeight) {
            mToolbarHeight = height;
            mLayerView.setMaxTranslation(height);
            mDynamicToolbar.setVisible(true, VisibilityTransition.IMMEDIATE);
        }
    }

    @Override
    void toggleChrome(final boolean aShow) {
        ThreadUtils.postToUiThread(new Runnable() {
            @Override
            public void run() {
                if (aShow) {
                    mBrowserChrome.setVisibility(View.VISIBLE);
                } else {
                    mBrowserChrome.setVisibility(View.GONE);
                }
            }
        });

        super.toggleChrome(aShow);
    }

    @Override
    void focusChrome() {
        ThreadUtils.postToUiThread(new Runnable() {
            @Override
            public void run() {
                mBrowserChrome.setVisibility(View.VISIBLE);
                mActionBarFlipper.requestFocusFromTouch();
            }
        });
    }

    @Override
    public void refreshChrome() {
        invalidateOptionsMenu();

        if (mTabsPanel != null) {
            mTabsPanel.refresh();
        }

        if (mTabStrip != null) {
            mTabStrip.refresh();
        }

        mBrowserToolbar.refresh();
    }

    @Override
    public void handleMessage(final String event, final NativeJSObject message,
                              final EventCallback callback) {
        if ("CharEncoding:Data".equals(event)) {
            final NativeJSObject[] charsets = message.getObjectArray("charsets");
            final int selected = message.getInt("selected");

            final String[] titleArray = new String[charsets.length];
            final String[] codeArray = new String[charsets.length];
            for (int i = 0; i < charsets.length; i++) {
                final NativeJSObject charset = charsets[i];
                titleArray[i] = charset.getString("title");
                codeArray[i] = charset.getString("code");
            }

            final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setSingleChoiceItems(titleArray, selected,
                    new AlertDialog.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    GeckoAppShell.notifyObservers("CharEncoding:Set", codeArray[which]);
                    dialog.dismiss();
                }
            });
            dialogBuilder.setNegativeButton(R.string.button_cancel,
                    new AlertDialog.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    dialog.dismiss();
                }
            });
            ThreadUtils.postToUiThread(new Runnable() {
                @Override
                public void run() {
                    dialogBuilder.show();
                }
            });

        } else if ("CharEncoding:State".equals(event)) {
            final boolean visible = message.getString("visible").equals("true");
            GeckoPreferences.setCharEncodingState(visible);
            final Menu menu = mMenu;
            ThreadUtils.postToUiThread(new Runnable() {
                @Override
                public void run() {
                    if (menu != null) {
                        menu.findItem(R.id.char_encoding).setVisible(visible);
                    }
                }
            });

        } else if ("Experiments:GetActive".equals(event)) {
            final List<String> experiments = SwitchBoard.getActiveExperiments(this);
            final JSONArray json = new JSONArray(experiments);
            callback.sendSuccess(json.toString());
        } else if ("Favicon:CacheLoad".equals(event)) {
            final String url = message.getString("url");
            getFaviconFromCache(callback, url);
        } else if ("Feedback:MaybeLater".equals(event)) {
            resetFeedbackLaunchCount();
        } else if ("Menu:Add".equals(event)) {
            final MenuItemInfo info = new MenuItemInfo();
            info.label = message.getString("name");
            info.id = message.getInt("id") + ADDON_MENU_OFFSET;
            info.checked = message.optBoolean("checked", false);
            info.enabled = message.optBoolean("enabled", true);
            info.visible = message.optBoolean("visible", true);
            info.checkable = message.optBoolean("checkable", false);
            final int parent = message.optInt("parent", 0);
            info.parent = parent <= 0 ? parent : parent + ADDON_MENU_OFFSET;
            final MenuItemInfo menuItemInfo = info;
            ThreadUtils.postToUiThread(new Runnable() {
                @Override
                public void run() {
                    addAddonMenuItem(menuItemInfo);
                }
            });

        } else if ("Menu:Remove".equals(event)) {
            final int id = message.getInt("id") + ADDON_MENU_OFFSET;
            ThreadUtils.postToUiThread(new Runnable() {
                @Override
                public void run() {
                    removeAddonMenuItem(id);
                }
            });
        } else if ("Sanitize:ClearHistory".equals(event)) {
            handleClearHistory(message.optBoolean("clearSearchHistory", false));
            callback.sendSuccess(true);
        } else if ("Sanitize:ClearSyncedTabs".equals(event)) {
            handleClearSyncedTabs();
            callback.sendSuccess(true);
        } else if ("Settings:Show".equals(event)) {
            final String resource =
                    message.optString(GeckoPreferences.INTENT_EXTRA_RESOURCES, null);
            final Intent settingsIntent = new Intent(this, GeckoPreferences.class);
            GeckoPreferences.setResourceToOpen(settingsIntent, resource);
            startActivityForResult(settingsIntent, ACTIVITY_REQUEST_PREFERENCES);

            // Don't use a transition to settings if we're on a device where that
            // would look bad.
            if (HardwareUtils.IS_KINDLE_DEVICE) {
                overridePendingTransition(0, 0);
            }

        } else if ("Telemetry:Gather".equals(event)) {
            final BrowserDB db = getProfile().getDB();
            final ContentResolver cr = getContentResolver();
            Telemetry.addToHistogram("PLACES_PAGES_COUNT", db.getCount(cr, "history"));
            Telemetry.addToHistogram("FENNEC_BOOKMARKS_COUNT", db.getCount(cr, "bookmarks"));
            Telemetry.addToHistogram("FENNEC_READING_LIST_COUNT", db.getReadingListAccessor().getCount(cr));
            Telemetry.addToHistogram("BROWSER_IS_USER_DEFAULT", (isDefaultBrowser(Intent.ACTION_VIEW) ? 1 : 0));
            Telemetry.addToHistogram("FENNEC_TABQUEUE_ENABLED", (TabQueueHelper.isTabQueueEnabled(BrowserApp.this) ? 1 : 0));
            Telemetry.addToHistogram("FENNEC_CUSTOM_HOMEPAGE", (TextUtils.isEmpty(getHomepage()) ? 0 : 1));
            final SharedPreferences prefs = GeckoSharedPrefs.forProfile(getContext());
            final boolean hasCustomHomepanels = prefs.contains(HomeConfigPrefsBackend.PREFS_CONFIG_KEY) || prefs.contains(HomeConfigPrefsBackend.PREFS_CONFIG_KEY_OLD);
            Telemetry.addToHistogram("FENNEC_HOMEPANELS_CUSTOM", hasCustomHomepanels ? 1 : 0);

            if (Versions.feature16Plus) {
                Telemetry.addToHistogram("BROWSER_IS_ASSIST_DEFAULT", (isDefaultBrowser(Intent.ACTION_ASSIST) ? 1 : 0));
            }

            if (Restrictions.isRestrictedProfile(this)) {
                for (Restrictable rest : RestrictedProfileConfiguration.getVisibleRestrictions()) {
                    int value = Restrictions.isAllowed(this, rest) ? 1 : 0;
                    Telemetry.addToKeyedHistogram("FENNEC_RESTRICTED_PROFILE_RESTRICTIONS", rest.name(), value);
                }
            }
        } else if ("Updater:Launch".equals(event)) {
            handleUpdaterLaunch();
        } else if ("Download:AndroidDownloadManager".equals(event)) {
            // Downloading via Android's download manager

            final String uri = message.getString("uri");
            final String filename = message.getString("filename");
            final String mimeType = message.getString("mimeType");

            final DownloadManager.Request request = new DownloadManager.Request(Uri.parse(uri));
            request.setMimeType(mimeType);

            try {
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);
            } catch (IllegalStateException e) {
                Log.e(LOGTAG, "Cannot create download directory");
                return;
            }

            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.addRequestHeader("User-Agent", HardwareUtils.isTablet() ?
                    AppConstants.USER_AGENT_FENNEC_TABLET :
                    AppConstants.USER_AGENT_FENNEC_MOBILE);

            try {
                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                manager.enqueue(request);

                Log.d(LOGTAG, "Enqueued download (Download Manager)");
            } catch (RuntimeException e) {
                Log.e(LOGTAG, "Download failed: " + e);
            }

        } else {
            super.handleMessage(event, message, callback);
        }
    }

    private void getFaviconFromCache(final EventCallback callback, final String url) {
        final OnFaviconLoadedListener listener = new OnFaviconLoadedListener() {
            @Override
            public void onFaviconLoaded(final String url, final String faviconURL, final Bitmap favicon) {
                ThreadUtils.assertOnUiThread();
                // Convert Bitmap to Base64 data URI in background.
                ThreadUtils.postToBackgroundThread(new Runnable() {
                    @Override
                    public void run() {
                        ByteArrayOutputStream out = null;
                        Base64OutputStream b64 = null;

                        // Failed to load favicon from local.
                        if (favicon == null) {
                            callback.sendError("Failed to get favicon from cache");
                        } else {
                            try {
                                out = new ByteArrayOutputStream();
                                out.write("data:image/png;base64,".getBytes());
                                b64 = new Base64OutputStream(out, Base64.NO_WRAP);
                                favicon.compress(Bitmap.CompressFormat.PNG, 100, b64);
                                callback.sendSuccess(new String(out.toByteArray()));
                            } catch (IOException e) {
                                Log.w(LOGTAG, "Failed to convert to base64 data URI");
                                callback.sendError("Failed to convert favicon to a base64 data URI");
                            } finally {
                                try {
                                    if (out != null) {
                                        out.close();
                                    }
                                    if (b64 != null) {
                                        b64.close();
                                    }
                                } catch (IOException e) {
                                    Log.w(LOGTAG, "Failed to close the streams");
                                }
                            }
                        }
                    }
                });
            }
        };
        Favicons.getSizedFaviconForPageFromLocal(getContext(),
                url,
                listener);
    }

    /**
     * Use a dummy Intent to do a default browser check.
     *
     * @return true if this package is the default browser on this device, false otherwise.
     */
    private boolean isDefaultBrowser(String action) {
        final Intent viewIntent = new Intent(action, Uri.parse("http://www.mozilla.org"));
        final ResolveInfo info = getPackageManager().resolveActivity(viewIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null) {
            // No default is set
            return false;
        }

        final String packageName = info.activityInfo.packageName;
        return (TextUtils.equals(packageName, getPackageName()));
    }

    @Override
    public void handleMessage(String event, JSONObject message) {
        try {
            if (event.equals("Menu:Open")) {
                if (mBrowserToolbar.isEditing()) {
                    mBrowserToolbar.cancelEdit();
                }

                openOptionsMenu();
            } else if (event.equals("Menu:Update")) {
                final int id = message.getInt("id") + ADDON_MENU_OFFSET;
                final JSONObject options = message.getJSONObject("options");
                ThreadUtils.postToUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateAddonMenuItem(id, options);
                    }
                });
            } else if (event.equals("Gecko:DelayedStartup")) {
                ThreadUtils.postToUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Force tabs panel inflation once the initial
                        // pageload is finished.
                        ensureTabsPanelExists();
                        if (AppConstants.NIGHTLY_BUILD && mZoomedView == null) {
                            ViewStub stub = (ViewStub) findViewById(R.id.zoomed_view_stub);
                            mZoomedView = (ZoomedView) stub.inflate();
                        }
                    }
                });

                if (AppConstants.MOZ_MEDIA_PLAYER) {
                    // Check if the fragment is already added. This should never be true here, but this is
                    // a nice safety check.
                    // If casting is disabled, these classes aren't built. We use reflection to initialize them.
                    final Class<?> mediaManagerClass = getMediaPlayerManager();

                    if (mediaManagerClass != null) {
                        try {
                            final String tag = "";
                            mediaManagerClass.getDeclaredField("MEDIA_PLAYER_TAG").get(tag);
                            Log.i(LOGTAG, "Found tag " + tag);
                            final Fragment frag = getSupportFragmentManager().findFragmentByTag(tag);
                            if (frag == null) {
                                final Method getInstance = mediaManagerClass.getMethod("newInstance", (Class[]) null);
                                final Fragment mpm = (Fragment) getInstance.invoke(null);
                                getSupportFragmentManager().beginTransaction().disallowAddToBackStack().add(mpm, tag).commit();
                            }
                        } catch (Exception ex) {
                            Log.e(LOGTAG, "Error initializing media manager", ex);
                        }
                    }
                }

                if (AppConstants.MOZ_STUMBLER_BUILD_TIME_ENABLED && Restrictions.isAllowed(this, Restrictable.DATA_CHOICES)) {
                    // Start (this acts as ping if started already) the stumbler lib; if the stumbler has queued data it will upload it.
                    // Stumbler operates on its own thread, and startup impact is further minimized by delaying work (such as upload) a few seconds.
                    // Avoid any potential startup CPU/thread contention by delaying the pref broadcast.
                    final long oneSecondInMillis = 1000;
                    ThreadUtils.getBackgroundHandler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                             GeckoPreferences.broadcastStumblerPref(BrowserApp.this);
                        }
                    }, oneSecondInMillis);
                }

                if (AppConstants.MOZ_ANDROID_DOWNLOAD_CONTENT_SERVICE) {
                    // TODO: Better scheduling of sync action (Bug 1257492)
                    DownloadContentService.startSync(this);

                    DownloadContentService.startVerification(this);
                }

                FeedService.setup(this);

                super.handleMessage(event, message);
            } else if (event.equals("Gecko:Ready")) {
                // Handle this message in GeckoApp, but also enable the Settings
                // menuitem, which is specific to BrowserApp.
                super.handleMessage(event, message);
                final Menu menu = mMenu;
                ThreadUtils.postToUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (menu != null) {
                            menu.findItem(R.id.settings).setEnabled(true);
                            menu.findItem(R.id.help).setEnabled(true);
                        }
                    }
                });

                // Display notification for Mozilla data reporting, if data should be collected.
                if (AppConstants.MOZ_DATA_REPORTING && Restrictions.isAllowed(this, Restrictable.DATA_CHOICES)) {
                    DataReportingNotification.checkAndNotifyPolicy(GeckoAppShell.getContext());
                }

            } else if (event.equals("Search:Keyword")) {
                storeSearchQuery(message.getString("query"));
            } else if (event.equals("LightweightTheme:Update")) {
                ThreadUtils.postToUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);
                    }
                });
            } else if (event.equals("Prompt:ShowTop")) {
                // Bring this activity to front so the prompt is visible..
                Intent bringToFrontIntent = new Intent();
                bringToFrontIntent.setClassName(AppConstants.ANDROID_PACKAGE_NAME, AppConstants.MOZ_ANDROID_BROWSER_INTENT_CLASS);
                bringToFrontIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(bringToFrontIntent);
            } else {
                super.handleMessage(event, message);
            }
        } catch (Exception e) {
            Log.e(LOGTAG, "Exception handling message \"" + event + "\":", e);
        }
    }

    @Override
    public void addTab() {
        Tabs.getInstance().addTab();
    }

    @Override
    public void addPrivateTab() {
        Tabs.getInstance().addPrivateTab();
    }

    public void showTrackingProtectionPromptIfApplicable() {
        final SharedPreferences prefs = getSharedPreferences();

        final boolean hasTrackingProtectionPromptBeShownBefore = prefs.getBoolean(GeckoPreferences.PREFS_TRACKING_PROTECTION_PROMPT_SHOWN, false);

        if (hasTrackingProtectionPromptBeShownBefore) {
            return;
        }

        prefs.edit().putBoolean(GeckoPreferences.PREFS_TRACKING_PROTECTION_PROMPT_SHOWN, true).apply();

        startActivity(new Intent(BrowserApp.this, TrackingProtectionPrompt.class));
    }

    @Override
    public void showNormalTabs() {
        showTabs(TabsPanel.Panel.NORMAL_TABS);
    }

    @Override
    public void showPrivateTabs() {
        showTabs(TabsPanel.Panel.PRIVATE_TABS);
    }
    /**
    * Ensure the TabsPanel view is properly inflated and returns
    * true when the view has been inflated, false otherwise.
    */
    private boolean ensureTabsPanelExists() {
        if (mTabsPanel != null) {
            return false;
        }

        ViewStub tabsPanelStub = (ViewStub) findViewById(R.id.tabs_panel);
        mTabsPanel = (TabsPanel) tabsPanelStub.inflate();

        mTabsPanel.setTabsLayoutChangeListener(this);

        return true;
    }

    private void showTabs(final TabsPanel.Panel panel) {
        if (Tabs.getInstance().getDisplayCount() == 0)
            return;

        hideFirstrunPager(TelemetryContract.Method.BUTTON);

        if (ensureTabsPanelExists()) {
            // If we've just inflated the tabs panel, only show it once the current
            // layout pass is done to avoid displayed temporary UI states during
            // relayout.
            ViewTreeObserver vto = mTabsPanel.getViewTreeObserver();
            if (vto.isAlive()) {
                vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mTabsPanel.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        showTabs(panel);
                    }
                });
            }
        } else {
            if (mDoorHangerPopup != null) {
                mDoorHangerPopup.disable();
            }
            mTabsPanel.show(panel);

            // Hide potentially visible "find in page" bar (Bug 1177338)
            mFindInPageBar.hide();
        }
    }

    @Override
    public void hideTabs() {
        mTabsPanel.hide();
        if (mDoorHangerPopup != null) {
            mDoorHangerPopup.enable();
        }
    }

    @Override
    public boolean autoHideTabs() {
        if (areTabsShown()) {
            hideTabs();
            return true;
        }
        return false;
    }

    @Override
    public boolean areTabsShown() {
        return (mTabsPanel != null && mTabsPanel.isShown());
    }

    @Override
    public String getHomepage() {
        final SharedPreferences prefs = GeckoSharedPrefs.forProfile(getContext());
        return prefs.getString(GeckoPreferences.PREFS_HOMEPAGE, null);
    }

    @Override
    public void onTabsLayoutChange(int width, int height) {
        int animationLength = TABS_ANIMATION_DURATION;

        if (mMainLayoutAnimator != null) {
            animationLength = Math.max(1, animationLength - (int)mMainLayoutAnimator.getRemainingTime());
            mMainLayoutAnimator.stop(false);
        }

        if (areTabsShown()) {
            mTabsPanel.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            // Hide the web content from accessibility tools even though it's visible
            // so that you can't examine it as long as the tabs are being shown.
            if (Versions.feature16Plus) {
                mLayerView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
        } else {
            if (Versions.feature16Plus) {
                mLayerView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        }

        mMainLayoutAnimator = new PropertyAnimator(animationLength, sTabsInterpolator);
        mMainLayoutAnimator.addPropertyAnimationListener(this);
        mMainLayoutAnimator.attach(mMainLayout,
                                   PropertyAnimator.Property.SCROLL_Y,
                                   -height);

        mTabsPanel.prepareTabsAnimation(mMainLayoutAnimator);
        mBrowserToolbar.triggerTabsPanelTransition(mMainLayoutAnimator, areTabsShown());

        // If the tabs panel is animating onto the screen, pin the dynamic
        // toolbar.
        if (mDynamicToolbar.isEnabled()) {
            if (width > 0 && height > 0) {
                mDynamicToolbar.setPinned(true, PinReason.RELAYOUT);
                mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);
            } else {
                mDynamicToolbar.setPinned(false, PinReason.RELAYOUT);
            }
        }

        mMainLayoutAnimator.start();
    }

    @Override
    public void onPropertyAnimationStart() {
    }

    @Override
    public void onPropertyAnimationEnd() {
        if (!areTabsShown()) {
            mTabsPanel.setVisibility(View.INVISIBLE);
            mTabsPanel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        } else {
            // Cancel editing mode to return to page content when the TabsPanel closes. We cancel
            // it here because there are graphical glitches if it's canceled while it's visible.
            mBrowserToolbar.cancelEdit();
        }

        mTabsPanel.finishTabsAnimation();

        mMainLayoutAnimator = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mDynamicToolbar.onSaveInstanceState(outState);
        outState.putInt(STATE_ABOUT_HOME_TOP_PADDING, mHomePagerContainer.getPaddingTop());
    }

    /**
     * Attempts to switch to an open tab with the given URL.
     * <p>
     * If the tab exists, this method cancels any in-progress editing as well as
     * calling {@link Tabs#selectTab(int)}.
     *
     * @param url of tab to switch to.
     * @param flags to obey: if {@link OnUrlOpenListener.Flags#ALLOW_SWITCH_TO_TAB}
     *        is not present, return false.
     * @return true if we successfully switched to a tab, false otherwise.
     */
    private boolean maybeSwitchToTab(String url, EnumSet<OnUrlOpenListener.Flags> flags) {
        if (!flags.contains(OnUrlOpenListener.Flags.ALLOW_SWITCH_TO_TAB)) {
            return false;
        }

        final Tabs tabs = Tabs.getInstance();
        final Tab tab;

        if (AboutPages.isAboutReader(url)) {
            tab = tabs.getFirstReaderTabForUrl(url, tabs.getSelectedTab().isPrivate());
        } else {
            tab = tabs.getFirstTabForUrl(url, tabs.getSelectedTab().isPrivate());
        }

        if (tab == null) {
            return false;
        }

        return maybeSwitchToTab(tab.getId());
    }

    /**
     * Attempts to switch to an open tab with the given unique tab ID.
     * <p>
     * If the tab exists, this method cancels any in-progress editing as well as
     * calling {@link Tabs#selectTab(int)}.
     *
     * @param id of tab to switch to.
     * @return true if we successfully switched to the tab, false otherwise.
     */
    private boolean maybeSwitchToTab(int id) {
        final Tabs tabs = Tabs.getInstance();
        final Tab tab = tabs.getTab(id);

        if (tab == null) {
            return false;
        }

        final Tab oldTab = tabs.getSelectedTab();
        if (oldTab != null) {
            oldTab.setIsEditing(false);
        }

        // Set the target tab to null so it does not get selected (on editing
        // mode exit) in lieu of the tab we are about to select.
        mTargetTabForEditingMode = null;
        tabs.selectTab(tab.getId());

        mBrowserToolbar.cancelEdit();

        return true;
    }

    private void openUrlAndStopEditing(String url) {
        openUrlAndStopEditing(url, null, false);
    }

    private void openUrlAndStopEditing(String url, boolean newTab) {
        openUrlAndStopEditing(url, null, newTab);
    }

    private void openUrlAndStopEditing(String url, String searchEngine) {
        openUrlAndStopEditing(url, searchEngine, false);
    }

    private void openUrlAndStopEditing(String url, String searchEngine, boolean newTab) {
        int flags = Tabs.LOADURL_NONE;
        if (newTab) {
            flags |= Tabs.LOADURL_NEW_TAB;
            if (Tabs.getInstance().getSelectedTab().isPrivate()) {
                flags |= Tabs.LOADURL_PRIVATE;
            }
        }

        Tabs.getInstance().loadUrl(url, searchEngine, -1, flags);

        mBrowserToolbar.cancelEdit();
    }

    private boolean isHomePagerVisible() {
        return (mHomePager != null && mHomePager.isVisible()
            && mHomePagerContainer != null && mHomePagerContainer.getVisibility() == View.VISIBLE);
    }

    private boolean isFirstrunVisible() {
        return (mFirstrunAnimationContainer != null && mFirstrunAnimationContainer.isVisible()
            && mHomePagerContainer != null && mHomePagerContainer.getVisibility() == View.VISIBLE);
    }

    /**
     * Enters editing mode with the current tab's URL. There might be no
     * tabs loaded by the time the user enters editing mode e.g. just after
     * the app starts. In this case, we simply fallback to an empty URL.
     */
    private void enterEditingMode() {
        String url = "";
        String telemetryMsg = "urlbar-empty";

        final Tab tab = Tabs.getInstance().getSelectedTab();
        if (tab != null) {
            final String userSearchTerm = tab.getUserRequested();
            final String tabURL = tab.getURL();

            // Check to see if there's a user-entered search term,
            // which we save whenever the user performs a search.
            if (!TextUtils.isEmpty(userSearchTerm)) {
                url = userSearchTerm;
                telemetryMsg = "urlbar-userentered";
            } else if (!TextUtils.isEmpty(tabURL)) {
                url = tabURL;
                telemetryMsg = "urlbar-url";
            }
        }

        enterEditingMode(url);
        Telemetry.sendUIEvent(TelemetryContract.Event.SHOW, TelemetryContract.Method.ACTIONBAR, telemetryMsg);
    }

    /**
     * Enters editing mode with the specified URL. If a null
     * url is given, the empty String will be used instead.
     */
    private void enterEditingMode(@NonNull String url) {
        hideFirstrunPager(TelemetryContract.Method.ACTIONBAR);

        if (mBrowserToolbar.isEditing() || mBrowserToolbar.isAnimating()) {
            return;
        }

        final Tab selectedTab = Tabs.getInstance().getSelectedTab();
        final String panelId;
        if (selectedTab != null) {
            mTargetTabForEditingMode = selectedTab.getId();
            panelId = selectedTab.getMostRecentHomePanel();
        } else {
            mTargetTabForEditingMode = null;
            panelId = null;
        }

        final PropertyAnimator animator = new PropertyAnimator(250);
        animator.setUseHardwareLayer(false);

        mBrowserToolbar.startEditing(url, animator);

        final boolean isUserSearchTerm = selectedTab != null &&
                !TextUtils.isEmpty(selectedTab.getUserRequested());
        if (isUserSearchTerm && SwitchBoard.isInExperiment(getContext(), Experiments.SEARCH_TERM)) {
            showBrowserSearchAfterAnimation(animator);
        } else {
            showHomePagerWithAnimator(panelId, animator);
        }

        animator.start();
        Telemetry.startUISession(TelemetryContract.Session.AWESOMESCREEN);
    }

    private void commitEditingMode() {
        if (!mBrowserToolbar.isEditing()) {
            return;
        }

        Telemetry.stopUISession(TelemetryContract.Session.AWESOMESCREEN,
                                TelemetryContract.Reason.COMMIT);

        final String url = mBrowserToolbar.commitEdit();

        // HACK: We don't know the url that will be loaded when hideHomePager is initially called
        // in BrowserToolbar's onStopEditing listener so on the awesomescreen, hideHomePager will
        // use the url "about:home" and return without taking any action. hideBrowserSearch is
        // then called, but since hideHomePager changes both HomePager and LayerView visibility
        // and exited without taking an action, no Views are displayed and graphical corruption is
        // visible instead.
        //
        // Here we call hideHomePager for the second time with the URL to be loaded so that
        // hideHomePager is called with the correct state for the upcoming page load.
        //
        // Expected to be fixed by bug 915825.
        hideHomePager(url);
        loadUrlOrKeywordSearch(url);
        clearSelectedTabApplicationId();
    }

    private void clearSelectedTabApplicationId() {
        final Tab selected = Tabs.getInstance().getSelectedTab();
        if (selected != null) {
            selected.setApplicationId(null);
        }
    }

    private void loadUrlOrKeywordSearch(final String url) {
        // Don't do anything if the user entered an empty URL.
        if (TextUtils.isEmpty(url)) {
            return;
        }

        // If the URL doesn't look like a search query, just load it.
        if (!StringUtils.isSearchQuery(url, true)) {
            Tabs.getInstance().loadUrl(url, Tabs.LOADURL_USER_ENTERED);
            Telemetry.sendUIEvent(TelemetryContract.Event.LOAD_URL, TelemetryContract.Method.ACTIONBAR, "user");
            return;
        }

        // Otherwise, check for a bookmark keyword.
        final BrowserDB db = getProfile().getDB();
        ThreadUtils.postToBackgroundThread(new Runnable() {
            @Override
            public void run() {
                final String keyword;
                final String keywordSearch;

                final int index = url.indexOf(" ");
                if (index == -1) {
                    keyword = url;
                    keywordSearch = "";
                } else {
                    keyword = url.substring(0, index);
                    keywordSearch = url.substring(index + 1);
                }

                final String keywordUrl = db.getUrlForKeyword(getContentResolver(), keyword);

                // If there isn't a bookmark keyword, load the url. This may result in a query
                // using the default search engine.
                if (TextUtils.isEmpty(keywordUrl)) {
                    Tabs.getInstance().loadUrl(url, Tabs.LOADURL_USER_ENTERED);
                    Telemetry.sendUIEvent(TelemetryContract.Event.LOAD_URL, TelemetryContract.Method.ACTIONBAR, "user");
                    return;
                }

                recordSearch(null, "barkeyword");

                // Otherwise, construct a search query from the bookmark keyword.
                // Replace lower case bookmark keywords with URLencoded search query or
                // replace upper case bookmark keywords with un-encoded search query.
                // This makes it match the same behaviour as on Firefox for the desktop.
                final String searchUrl = keywordUrl.replace("%s", URLEncoder.encode(keywordSearch)).replace("%S", keywordSearch);

                Tabs.getInstance().loadUrl(searchUrl, Tabs.LOADURL_USER_ENTERED);
                Telemetry.sendUIEvent(TelemetryContract.Event.LOAD_URL,
                                      TelemetryContract.Method.ACTIONBAR,
                                      "keyword");
            }
        });
    }

    /**
     * Record in Health Report that a search has occurred.
     *
     * @param engine
     *        a search engine instance. Can be null.
     * @param where
     *        where the search was initialized; one of the values in
     *        {@link BrowserHealthRecorder#SEARCH_LOCATIONS}.
     */
    private static void recordSearch(SearchEngine engine, String where) {
        //try {
        //    String identifier = (engine == null) ? "other" : engine.getEngineIdentifier();
        //    JSONObject message = new JSONObject();
        //    message.put("type", BrowserHealthRecorder.EVENT_SEARCH);
        //    message.put("location", where);
        //    message.put("identifier", identifier);
        //    EventDispatcher.getInstance().dispatchEvent(message, null);
        //} catch (Exception e) {
        //    Log.e(LOGTAG, "Error recording search.", e);
        //}
    }

    /**
     * Store search query in SearchHistoryProvider.
     *
     * @param query
     *        a search query to store. We won't store empty queries.
     */
    private void storeSearchQuery(final String query) {
        if (TextUtils.isEmpty(query)) {
            return;
        }

        // Filter out URLs and long suggestions
        if (query.length() > 50 || Pattern.matches("^(https?|ftp|file)://.*", query)) {
            return;
        }

        final GeckoProfile profile = getProfile();
        // Don't bother storing search queries in guest mode
        if (profile.inGuestMode()) {
            return;
        }

        final BrowserDB db = profile.getDB();
        ThreadUtils.postToBackgroundThread(new Runnable() {
            @Override
            public void run() {
                db.getSearches().insert(getContentResolver(), query);
            }
        });
    }

    void filterEditingMode(String searchTerm, AutocompleteHandler handler) {
        if (TextUtils.isEmpty(searchTerm)) {
            hideBrowserSearch();
        } else {
            showBrowserSearch();
            mBrowserSearch.filter(searchTerm, handler);
        }
    }

    /**
     * Selects the target tab for editing mode. This is expected to be the tab selected on editing
     * mode entry, unless it is subsequently overridden.
     *
     * A background tab may be selected while editing mode is active (e.g. popups), causing the
     * new url to load in the newly selected tab. Call this method on editing mode exit to
     * mitigate this.
     *
     * Note that this method is disabled for new tablets because we can see the selected tab in the
     * tab strip and, when the selected tab changes during editing mode as in this hack, the
     * temporarily selected tab is visible to users.
     */
    private void selectTargetTabForEditingMode() {
        if (HardwareUtils.isTablet()) {
            return;
        }

        if (mTargetTabForEditingMode != null) {
            Tabs.getInstance().selectTab(mTargetTabForEditingMode);
        }

        mTargetTabForEditingMode = null;
    }

    /**
     * Shows or hides the home pager for the given tab.
     */
    private void updateHomePagerForTab(Tab tab) {
        // Don't change the visibility of the home pager if we're in editing mode.
        if (mBrowserToolbar.isEditing()) {
            return;
        }

        if (isAboutHome(tab)) {
            String panelId = AboutPages.getPanelIdFromAboutHomeUrl(tab.getURL());
            if (panelId == null) {
                // No panel was specified in the URL. Try loading the most recent
                // home panel for this tab.
                panelId = tab.getMostRecentHomePanel();
            }
            showHomePager(panelId);

            if (mDynamicToolbar.isEnabled()) {
                mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);
            }
        } else {
            hideHomePager();
        }
    }

    @Override
    public void onLocaleReady(final String locale) {
        Log.d(LOGTAG, "onLocaleReady: " + locale);
        super.onLocaleReady(locale);

        HomePanelsManager.getInstance().onLocaleReady(locale);

        if (mMenu != null) {
            mMenu.clear();
            onCreateOptionsMenu(mMenu);
        }

        if (!Versions.feature14Plus) {
            conditionallyNotifyEOL();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(LOGTAG, "onActivityResult: " + requestCode + ", " + resultCode + ", " + data);
        switch (requestCode) {
            case ACTIVITY_REQUEST_PREFERENCES:
                // We just returned from preferences. If our locale changed,
                // we need to redisplay at this point, and do any other browser-level
                // bookkeeping that we associate with a locale change.
                if (resultCode != GeckoPreferences.RESULT_CODE_LOCALE_DID_CHANGE) {
                    Log.d(LOGTAG, "No locale change returning from preferences; nothing to do.");
                    return;
                }

                ThreadUtils.postToBackgroundThread(new Runnable() {
                    @Override
                    public void run() {
                        final LocaleManager localeManager = BrowserLocaleManager.getInstance();
                        final Locale locale = localeManager.getCurrentLocale(getApplicationContext());
                        Log.d(LOGTAG, "Read persisted locale " + locale);
                        if (locale == null) {
                            return;
                        }
                        onLocaleChanged(Locales.getLanguageTag(locale));
                    }
                });
                break;

            case ACTIVITY_REQUEST_TAB_QUEUE:
                TabQueueHelper.processTabQueuePromptResponse(resultCode, this);
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void showFirstrunPager() {
        if (mFirstrunAnimationContainer == null) {
            final ViewStub firstrunPagerStub = (ViewStub) findViewById(R.id.firstrun_pager_stub);
            mFirstrunAnimationContainer = (FirstrunAnimationContainer) firstrunPagerStub.inflate();
            mFirstrunAnimationContainer.load(getApplicationContext(), getSupportFragmentManager());
            mFirstrunAnimationContainer.registerOnFinishListener(new FirstrunAnimationContainer.OnFinishListener() {
                @Override
                public void onFinish() {
                    if (mFirstrunAnimationContainer.showBrowserHint()) {
                        enterEditingMode();
                    }
                }
            });
        }

        mHomePagerContainer.setVisibility(View.VISIBLE);
    }

    private void showHomePager(String panelId) {
        showHomePagerWithAnimator(panelId, null);
    }

    private void showHomePagerWithAnimator(String panelId, PropertyAnimator animator) {
        if (isHomePagerVisible()) {
            // Home pager already visible, make sure it shows the correct panel.
            mHomePager.showPanel(panelId);
            return;
        }

        // This must be called before the dynamic toolbar is set visible because it calls
        // FormAssistPopup.onMetricsChanged, which queues a runnable that undoes the effect of hide.
        // With hide first, onMetricsChanged will return early instead.
        mFormAssistPopup.hide();
        mFindInPageBar.hide();

        // Refresh toolbar height to possibly restore the toolbar padding
        refreshToolbarHeight();

        // Show the toolbar before hiding about:home so the
        // onMetricsChanged callback still works.
        if (mDynamicToolbar.isEnabled()) {
            mDynamicToolbar.setVisible(true, VisibilityTransition.IMMEDIATE);
        }

        if (mHomePager == null) {
            final ViewStub homePagerStub = (ViewStub) findViewById(R.id.home_pager_stub);
            mHomePager = (HomePager) homePagerStub.inflate();

            mHomePager.setOnPanelChangeListener(new HomePager.OnPanelChangeListener() {
                @Override
                public void onPanelSelected(String panelId) {
                    final Tab currentTab = Tabs.getInstance().getSelectedTab();
                    if (currentTab != null) {
                        currentTab.setMostRecentHomePanel(panelId);
                    }
                }
            });

            // Don't show the banner in guest mode.
            if (!Restrictions.isUserRestricted()) {
                final ViewStub homeBannerStub = (ViewStub) findViewById(R.id.home_banner_stub);
                final HomeBanner homeBanner = (HomeBanner) homeBannerStub.inflate();
                mHomePager.setBanner(homeBanner);

                // Remove the banner from the view hierarchy if it is dismissed.
                homeBanner.setOnDismissListener(new HomeBanner.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        mHomePager.setBanner(null);
                        mHomePagerContainer.removeView(homeBanner);
                    }
                });
            }
        }

        mHomePagerContainer.setVisibility(View.VISIBLE);
        mHomePager.load(getSupportLoaderManager(),
                        getSupportFragmentManager(),
                        panelId, animator);

        // Hide the web content so it cannot be focused by screen readers.
        hideWebContentOnPropertyAnimationEnd(animator);
    }

    private void hideWebContentOnPropertyAnimationEnd(final PropertyAnimator animator) {
        if (animator == null) {
            hideWebContent();
            return;
        }

        animator.addPropertyAnimationListener(new PropertyAnimator.PropertyAnimationListener() {
            @Override
            public void onPropertyAnimationStart() {
                mHideWebContentOnAnimationEnd = true;
            }

            @Override
            public void onPropertyAnimationEnd() {
                if (mHideWebContentOnAnimationEnd) {
                    hideWebContent();
                }
            }
        });
    }

    private void hideWebContent() {
        // The view is set to INVISIBLE, rather than GONE, to avoid
        // the additional requestLayout() call.
        mLayerView.setVisibility(View.INVISIBLE);
    }

    /**
     * Hide the Onboarding pager on user action, and don't show any onFinish hints.
     * @param method TelemetryContract method by which action was taken
     * @return boolean of whether pager was visible
     */
    private boolean hideFirstrunPager(TelemetryContract.Method method) {
        if (!isFirstrunVisible()) {
            return false;
        }

        Telemetry.sendUIEvent(TelemetryContract.Event.CANCEL, method, "firstrun-pane");

        // Don't show any onFinish actions when hiding from this Activity.
        mFirstrunAnimationContainer.registerOnFinishListener(null);
        mFirstrunAnimationContainer.hide();
        return true;
    }

    /**
     * Hides the HomePager, using the url of the currently selected tab as the url to be
     * loaded.
     */
    private void hideHomePager() {
        final Tab selectedTab = Tabs.getInstance().getSelectedTab();
        final String url = (selectedTab != null) ? selectedTab.getURL() : null;

        hideHomePager(url);
    }

    /**
     * Hides the HomePager. The given url should be the url of the page to be loaded, or null
     * if a new page is not being loaded.
     */
    private void hideHomePager(final String url) {
        if (!isHomePagerVisible() || AboutPages.isAboutHome(url)) {
            return;
        }

        // Prevent race in hiding web content - see declaration for more info.
        mHideWebContentOnAnimationEnd = false;

        // Display the previously hidden web content (which prevented screen reader access).
        mLayerView.setVisibility(View.VISIBLE);
        mHomePagerContainer.setVisibility(View.GONE);

        if (mHomePager != null) {
            mHomePager.unload();
        }

        mBrowserToolbar.setNextFocusDownId(R.id.layer_view);

        // Refresh toolbar height to possibly restore the toolbar padding
        refreshToolbarHeight();
    }

    private void showBrowserSearchAfterAnimation(PropertyAnimator animator) {
        if (animator == null) {
            showBrowserSearch();
            return;
        }

        animator.addPropertyAnimationListener(new PropertyAnimator.PropertyAnimationListener() {
            @Override
            public void onPropertyAnimationStart() {
            }

            @Override
            public void onPropertyAnimationEnd() {
                showBrowserSearch();
            }
        });
    }

    private void showBrowserSearch() {
        if (mBrowserSearch.getUserVisibleHint()) {
            return;
        }

        mBrowserSearchContainer.setVisibility(View.VISIBLE);

        // Prevent overdraw by hiding the underlying web content and HomePager View
        hideWebContent();
        mHomePagerContainer.setVisibility(View.INVISIBLE);

        final FragmentManager fm = getSupportFragmentManager();

        // In certain situations, showBrowserSearch() can be called immediately after hideBrowserSearch()
        // (see bug 925012). Because of an Android bug (http://code.google.com/p/android/issues/detail?id=61179),
        // calling FragmentTransaction#add immediately after FragmentTransaction#remove won't add the fragment's
        // view to the layout. Calling FragmentManager#executePendingTransactions before re-adding the fragment
        // prevents this issue.
        fm.executePendingTransactions();

        Fragment f = fm.findFragmentById(R.id.search_container);

        // checking if fragment is already present
        if (f != null) {
            fm.beginTransaction().show(f).commitAllowingStateLoss();
            mBrowserSearch.resetScrollState();
        } else {
            // add fragment if not already present
            fm.beginTransaction().add(R.id.search_container, mBrowserSearch, BROWSER_SEARCH_TAG).commitAllowingStateLoss();
        }
        mBrowserSearch.setUserVisibleHint(true);

        // We want to adjust the window size when the keyboard appears to bring the
        // SearchEngineBar above the keyboard. However, adjusting the window size
        // when hiding the keyboard results in graphical glitches where the keyboard was
        // because nothing was being drawn underneath (bug 933422). This can be
        // prevented drawing content under the keyboard (i.e. in the Window).
        //
        // We do this here because there are glitches when unlocking a device with
        // BrowserSearch in the foreground if we use BrowserSearch.onStart/Stop.
        getActivity().getWindow().setBackgroundDrawableResource(android.R.color.white);
    }

    private void hideBrowserSearch() {
        if (!mBrowserSearch.getUserVisibleHint()) {
            return;
        }

        // To prevent overdraw, the HomePager is hidden when BrowserSearch is displayed:
        // reverse that.
        showHomePager(Tabs.getInstance().getSelectedTab().getMostRecentHomePanel());

        mBrowserSearchContainer.setVisibility(View.INVISIBLE);

        getSupportFragmentManager().beginTransaction()
                .hide(mBrowserSearch).commitAllowingStateLoss();
        mBrowserSearch.setUserVisibleHint(false);

        getWindow().setBackgroundDrawable(null);
    }

    /**
     * Hides certain UI elements (e.g. button toast, tabs panel) when the
     * user touches the main layout.
     */
    private class HideOnTouchListener implements TouchEventInterceptor {
        private boolean mIsHidingTabs;
        private final Rect mTempRect = new Rect();

        @Override
        public boolean onInterceptTouchEvent(View view, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                SnackbarHelper.dismissCurrentSnackbar();
            }



            // We need to account for scroll state for the touched view otherwise
            // tapping on an "empty" part of the view will still be considered a
            // valid touch event.
            if (view.getScrollX() != 0 || view.getScrollY() != 0) {
                view.getHitRect(mTempRect);
                mTempRect.offset(-view.getScrollX(), -view.getScrollY());

                int[] viewCoords = new int[2];
                view.getLocationOnScreen(viewCoords);

                int x = (int) event.getRawX() - viewCoords[0];
                int y = (int) event.getRawY() - viewCoords[1];

                if (!mTempRect.contains(x, y))
                    return false;
            }

            // If the tabs panel is showing, hide the tab panel and don't send the event to content.
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && autoHideTabs()) {
                mIsHidingTabs = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (mIsHidingTabs) {
                // Keep consuming events until the gesture finishes.
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    mIsHidingTabs = false;
                }
                return true;
            }
            return false;
        }
    }

    private static Menu findParentMenu(Menu menu, MenuItem item) {
        final int itemId = item.getItemId();

        final int count = (menu != null) ? menu.size() : 0;
        for (int i = 0; i < count; i++) {
            MenuItem menuItem = menu.getItem(i);
            if (menuItem.getItemId() == itemId) {
                return menu;
            }
            if (menuItem.hasSubMenu()) {
                Menu parent = findParentMenu(menuItem.getSubMenu(), item);
                if (parent != null) {
                    return parent;
                }
            }
        }

        return null;
    }

    /**
     * Add the provided item to the provided menu, which should be
     * the root (mMenu).
     */
    private void addAddonMenuItemToMenu(final Menu menu, final MenuItemInfo info) {
        info.added = true;

        final Menu destination;
        if (info.parent == 0) {
            destination = menu;
        } else if (info.parent == GECKO_TOOLS_MENU) {
            // The tools menu only exists in our -v11 resources.
            if (Versions.feature11Plus) {
                final MenuItem tools = menu.findItem(R.id.tools);
                destination = tools != null ? tools.getSubMenu() : menu;
            } else {
                destination = menu;
            }
        } else {
            final MenuItem parent = menu.findItem(info.parent);
            if (parent == null) {
                return;
            }

            Menu parentMenu = findParentMenu(menu, parent);

            if (!parent.hasSubMenu()) {
                parentMenu.removeItem(parent.getItemId());
                destination = parentMenu.addSubMenu(Menu.NONE, parent.getItemId(), Menu.NONE, parent.getTitle());
                if (parent.getIcon() != null) {
                    ((SubMenu) destination).getItem().setIcon(parent.getIcon());
                }
            } else {
                destination = parent.getSubMenu();
            }
        }

        final MenuItem item = destination.add(Menu.NONE, info.id, Menu.NONE, info.label);

        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                GeckoAppShell.notifyObservers("Menu:Clicked", Integer.toString(info.id - ADDON_MENU_OFFSET));
                return true;
            }
        });

        item.setCheckable(info.checkable);
        item.setChecked(info.checked);
        item.setEnabled(info.enabled);
        item.setVisible(info.visible);
    }

    private void addAddonMenuItem(final MenuItemInfo info) {
        if (mAddonMenuItemsCache == null) {
            mAddonMenuItemsCache = new Vector<MenuItemInfo>();
        }

        // Mark it as added if the menu was ready.
        info.added = (mMenu != null);

        // Always cache so we can rebuild after a locale switch.
        mAddonMenuItemsCache.add(info);

        if (mMenu == null) {
            return;
        }

        addAddonMenuItemToMenu(mMenu, info);
    }

    private void removeAddonMenuItem(int id) {
        // Remove add-on menu item from cache, if available.
        if (mAddonMenuItemsCache != null && !mAddonMenuItemsCache.isEmpty()) {
            for (MenuItemInfo item : mAddonMenuItemsCache) {
                 if (item.id == id) {
                     mAddonMenuItemsCache.remove(item);
                     break;
                 }
            }
        }

        if (mMenu == null)
            return;

        final MenuItem menuItem = mMenu.findItem(id);
        if (menuItem != null)
            mMenu.removeItem(id);
    }

    private void updateAddonMenuItem(int id, JSONObject options) {
        // Set attribute for the menu item in cache, if available
        if (mAddonMenuItemsCache != null && !mAddonMenuItemsCache.isEmpty()) {
            for (MenuItemInfo item : mAddonMenuItemsCache) {
                if (item.id == id) {
                    item.label = options.optString("name", item.label);
                    item.checkable = options.optBoolean("checkable", item.checkable);
                    item.checked = options.optBoolean("checked", item.checked);
                    item.enabled = options.optBoolean("enabled", item.enabled);
                    item.visible = options.optBoolean("visible", item.visible);
                    item.added = (mMenu != null);
                    break;
                }
            }
        }

        if (mMenu == null) {
            return;
        }

        final MenuItem menuItem = mMenu.findItem(id);
        if (menuItem != null) {
            menuItem.setTitle(options.optString("name", menuItem.getTitle().toString()));
            menuItem.setCheckable(options.optBoolean("checkable", menuItem.isCheckable()));
            menuItem.setChecked(options.optBoolean("checked", menuItem.isChecked()));
            menuItem.setEnabled(options.optBoolean("enabled", menuItem.isEnabled()));
            menuItem.setVisible(options.optBoolean("visible", menuItem.isVisible()));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Sets mMenu = menu.
        super.onCreateOptionsMenu(menu);

        // Inform the menu about the action-items bar.
        if (menu instanceof GeckoMenu &&
            HardwareUtils.isTablet()) {
            ((GeckoMenu) menu).setActionItemBarPresenter(mBrowserToolbar);
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.browser_app_menu, mMenu);

        // Add add-on menu items, if any exist.
        if (mAddonMenuItemsCache != null && !mAddonMenuItemsCache.isEmpty()) {
            for (MenuItemInfo item : mAddonMenuItemsCache) {
                addAddonMenuItemToMenu(mMenu, item);
            }
        }

        // Action providers are available only ICS+.
        if (Versions.feature14Plus) {
            GeckoMenuItem share = (GeckoMenuItem) mMenu.findItem(R.id.share);
            final GeckoMenuItem quickShare = (GeckoMenuItem) mMenu.findItem(R.id.quickshare);

            GeckoActionProvider provider = GeckoActionProvider.getForType(GeckoActionProvider.DEFAULT_MIME_TYPE, this);

            share.setActionProvider(provider);
            quickShare.setActionProvider(provider);
        }

        return true;
    }

    @Override
    public void openOptionsMenu() {
        hideFirstrunPager(TelemetryContract.Method.MENU);

        // Disable menu access (for hardware buttons) when the software menu button is inaccessible.
        // Note that the software button is always accessible on new tablet.
        if (mBrowserToolbar.isEditing() && !HardwareUtils.isTablet()) {
            return;
        }

        if (ActivityUtils.isFullScreen(this)) {
            return;
        }

        if (areTabsShown()) {
            mTabsPanel.showMenu();
            return;
        }

        // Scroll custom menu to the top
        if (mMenuPanel != null)
            mMenuPanel.scrollTo(0, 0);

        // Scroll menu ListView (potentially in MenuPanel ViewGroup) to top.
        if (mMenu instanceof GeckoMenu) {
            ((GeckoMenu) mMenu).setSelection(0);
        }

        if (!mBrowserToolbar.openOptionsMenu())
            super.openOptionsMenu();

        if (mDynamicToolbar.isEnabled()) {
            mDynamicToolbar.setVisible(true, VisibilityTransition.ANIMATE);
        }
    }

    @Override
    public void closeOptionsMenu() {
        if (!mBrowserToolbar.closeOptionsMenu())
            super.closeOptionsMenu();
    }

    @Override
    public void setFullScreen(final boolean fullscreen) {
        super.setFullScreen(fullscreen);
        ThreadUtils.postToUiThread(new Runnable() {
            @Override
            public void run() {
                if (fullscreen) {
                    if (mDynamicToolbar.isEnabled()) {
                        mDynamicToolbar.setVisible(false, VisibilityTransition.IMMEDIATE);
                        mDynamicToolbar.setPinned(true, PinReason.FULL_SCREEN);
                    } else {
                        setToolbarMargin(0);
                    }
                    mBrowserChrome.setVisibility(View.GONE);
                } else {
                    mBrowserChrome.setVisibility(View.VISIBLE);
                    if (mDynamicToolbar.isEnabled()) {
                        mDynamicToolbar.setPinned(false, PinReason.FULL_SCREEN);
                        mDynamicToolbar.setVisible(true, VisibilityTransition.IMMEDIATE);
                    } else {
                        setToolbarMargin(mBrowserChrome.getHeight());
                    }
                }
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu aMenu) {
        if (aMenu == null)
            return false;

        // Hide the tab history panel when hardware menu button is pressed.
        TabHistoryFragment frag = (TabHistoryFragment) getSupportFragmentManager().findFragmentByTag(TAB_HISTORY_FRAGMENT_TAG);
        if (frag != null) {
            frag.dismiss();
        }

        if (!GeckoThread.isRunning()) {
            aMenu.findItem(R.id.settings).setEnabled(false);
            aMenu.findItem(R.id.help).setEnabled(false);
        }

        Tab tab = Tabs.getInstance().getSelectedTab();
        // Unlike other menu items, the bookmark star is not tinted. See {@link ThemedImageButton#setTintedDrawable}.
        final MenuItem bookmark = aMenu.findItem(R.id.bookmark);
        final MenuItem reader = aMenu.findItem(R.id.reading_list);
        final MenuItem back = aMenu.findItem(R.id.back);
        final MenuItem forward = aMenu.findItem(R.id.forward);
        final MenuItem share = aMenu.findItem(R.id.share);
        final MenuItem quickShare = aMenu.findItem(R.id.quickshare);
        final MenuItem bookmarksList = aMenu.findItem(R.id.bookmarks_list);
        final MenuItem historyList = aMenu.findItem(R.id.history_list);
        final MenuItem saveAsPDF = aMenu.findItem(R.id.save_as_pdf);
        final MenuItem print = aMenu.findItem(R.id.print);
        final MenuItem charEncoding = aMenu.findItem(R.id.char_encoding);
        final MenuItem findInPage = aMenu.findItem(R.id.find_in_page);
        final MenuItem desktopMode = aMenu.findItem(R.id.desktop_mode);
        final MenuItem enterGuestMode = aMenu.findItem(R.id.new_guest_session);
        final MenuItem exitGuestMode = aMenu.findItem(R.id.exit_guest_session);

        // Only show the "Quit" menu item on pre-ICS, television devices,
        // or if the user has explicitly enabled the clear on shutdown pref.
        // (We check the pref last to save the pref read.)
        // In ICS+, it's easy to kill an app through the task switcher.
        final boolean visible = Versions.preICS ||
                                HardwareUtils.isTelevision() ||
                                !PrefUtils.getStringSet(GeckoSharedPrefs.forProfile(this),
                                                        ClearOnShutdownPref.PREF,
                                                        new HashSet<String>()).isEmpty();
        aMenu.findItem(R.id.quit).setVisible(visible);

        if (tab == null || tab.getURL() == null) {
            bookmark.setEnabled(false);
            reader.setEnabled(false);
            back.setEnabled(false);
            forward.setEnabled(false);
            share.setEnabled(false);
            quickShare.setEnabled(false);
            saveAsPDF.setEnabled(false);
            print.setEnabled(false);
            findInPage.setEnabled(false);

            // NOTE: Use MenuUtils.safeSetEnabled because some actions might
            // be on the BrowserToolbar context menu.
            if (Versions.feature11Plus) {
                // There is no page menu prior to v11 resources.
                MenuUtils.safeSetEnabled(aMenu, R.id.page, false);
            }
            MenuUtils.safeSetEnabled(aMenu, R.id.subscribe, false);
            MenuUtils.safeSetEnabled(aMenu, R.id.add_search_engine, false);
            MenuUtils.safeSetEnabled(aMenu, R.id.add_to_launcher, false);

            return true;
        }

        final boolean inGuestMode = GeckoProfile.get(this).inGuestMode();

        final boolean isAboutReader = AboutPages.isAboutReader(tab.getURL());
        bookmark.setEnabled(!isAboutReader);
        bookmark.setVisible(!inGuestMode);
        bookmark.setCheckable(true);
        bookmark.setChecked(tab.isBookmark());
        bookmark.setTitle(resolveBookmarkTitleID(tab.isBookmark()));

        reader.setEnabled(isAboutReader || !AboutPages.isAboutPage(tab.getURL()));
        reader.setVisible(!inGuestMode);
        reader.setCheckable(true);
        final boolean isPageInReadingList = tab.isInReadingList();
        reader.setChecked(isPageInReadingList);
        reader.setTitle(resolveReadingListTitleID(isPageInReadingList));

        if (Versions.feature11Plus) {
            // We don't use icons on GB builds so not resolving icons might conserve resources.
            bookmark.setIcon(resolveBookmarkIconID(tab.isBookmark()));
            reader.setIcon(resolveReadingListIconID(isPageInReadingList));
        }

        back.setEnabled(tab.canDoBack());
        forward.setEnabled(tab.canDoForward());
        desktopMode.setChecked(tab.getDesktopMode());

        View backButtonView = MenuItemCompat.getActionView(back);

        if (backButtonView != null) {
            backButtonView.setOnLongClickListener(new Button.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    Tab tab = Tabs.getInstance().getSelectedTab();
                    if (tab != null) {
                        closeOptionsMenu();
                        return tabHistoryController.showTabHistory(tab,
                                TabHistoryController.HistoryAction.BACK);
                    }
                    return false;
                }
            });
        }

        View forwardButtonView = MenuItemCompat.getActionView(forward);

        if (forwardButtonView != null) {
            forwardButtonView.setOnLongClickListener(new Button.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    Tab tab = Tabs.getInstance().getSelectedTab();
                    if (tab != null) {
                        closeOptionsMenu();
                        return tabHistoryController.showTabHistory(tab,
                                TabHistoryController.HistoryAction.FORWARD);
                    }
                    return false;
                }
            });
        }

        String url = tab.getURL();
        if (AboutPages.isAboutReader(url)) {
            String urlFromReader = ReaderModeUtils.getUrlFromAboutReader(url);
            if (urlFromReader != null) {
                url = urlFromReader;
            }
        }

        // Disable share menuitem for about:, chrome:, file:, and resource: URIs
        final boolean shareVisible = Restrictions.isAllowed(this, Restrictable.SHARE);
        share.setVisible(shareVisible);
        final boolean shareEnabled = StringUtils.isShareableUrl(url) && shareVisible;
        share.setEnabled(shareEnabled);
        MenuUtils.safeSetEnabled(aMenu, R.id.downloads, Restrictions.isAllowed(this, Restrictable.DOWNLOAD));

        // NOTE: Use MenuUtils.safeSetEnabled because some actions might
        // be on the BrowserToolbar context menu.
        if (Versions.feature11Plus) {
            MenuUtils.safeSetEnabled(aMenu, R.id.page, !isAboutHome(tab));
        }
        MenuUtils.safeSetEnabled(aMenu, R.id.subscribe, tab.hasFeeds());
        MenuUtils.safeSetEnabled(aMenu, R.id.add_search_engine, tab.hasOpenSearch());
        MenuUtils.safeSetEnabled(aMenu, R.id.add_to_launcher, !isAboutHome(tab));

        // Action providers are available only ICS+.
        if (Versions.feature14Plus) {
            quickShare.setVisible(shareVisible);
            quickShare.setEnabled(shareEnabled);

            // This provider also applies to the quick share menu item.
            final GeckoActionProvider provider = ((GeckoMenuItem) share).getGeckoActionProvider();
            if (provider != null) {
                Intent shareIntent = provider.getIntent();

                // For efficiency, the provider's intent is only set once
                if (shareIntent == null) {
                    shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    provider.setIntent(shareIntent);
                }

                // Replace the existing intent's extras
                shareIntent.putExtra(Intent.EXTRA_TEXT, url);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, tab.getDisplayTitle());
                shareIntent.putExtra(Intent.EXTRA_TITLE, tab.getDisplayTitle());
                shareIntent.putExtra(ShareDialog.INTENT_EXTRA_DEVICES_ONLY, true);

                // Clear the existing thumbnail extras so we don't share an old thumbnail.
                shareIntent.removeExtra("share_screenshot_uri");

                // Include the thumbnail of the page being shared.
                BitmapDrawable drawable = tab.getThumbnail();
                if (drawable != null) {
                    Bitmap thumbnail = drawable.getBitmap();

                    // Kobo uses a custom intent extra for sharing thumbnails.
                    if (Build.MANUFACTURER.equals("Kobo") && thumbnail != null) {
                        File cacheDir = getExternalCacheDir();

                        if (cacheDir != null) {
                            File outFile = new File(cacheDir, "thumbnail.png");

                            try {
                                final java.io.FileOutputStream out = new java.io.FileOutputStream(outFile);
                                try {
                                    thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out);
                                } finally {
                                    try {
                                        out.close();
                                    } catch (final IOException e) { /* Nothing to do here. */ }
                                }
                            } catch (FileNotFoundException e) {
                                Log.e(LOGTAG, "File not found", e);
                            }

                            shareIntent.putExtra("share_screenshot_uri", Uri.parse(outFile.getPath()));
                        }
                    }
                }
            }
        }

        final boolean privateTabVisible = Restrictions.isAllowed(this, Restrictable.PRIVATE_BROWSING);
        MenuUtils.safeSetVisible(aMenu, R.id.new_private_tab, privateTabVisible);

        // Disable PDF generation (save and print) for about:home and xul pages.
        boolean allowPDF = (!(isAboutHome(tab) ||
                               tab.getContentType().equals("application/vnd.mozilla.xul+xml") ||
                               tab.getContentType().startsWith("video/")));
        saveAsPDF.setEnabled(allowPDF);
        print.setEnabled(allowPDF);
        print.setVisible(Versions.feature19Plus);

        // Disable find in page for about:home, since it won't work on Java content.
        findInPage.setEnabled(!isAboutHome(tab));

        charEncoding.setVisible(GeckoPreferences.getCharEncodingState());

        if (mProfile.inGuestMode()) {
            exitGuestMode.setVisible(true);
        } else {
            enterGuestMode.setVisible(true);
        }

        if (!Restrictions.isAllowed(this, Restrictable.GUEST_BROWSING)) {
            MenuUtils.safeSetVisible(aMenu, R.id.new_guest_session, false);
        }

        if (!Restrictions.isAllowed(this, Restrictable.INSTALL_EXTENSION)) {
            MenuUtils.safeSetVisible(aMenu, R.id.addons, false);
        }

        if (!SwitchBoard.isInExperiment(this, Experiments.BOOKMARKS_HISTORY_MENU)) {
            bookmarksList.setVisible(false);
            historyList.setVisible(false);
        } else {
            // Hide panel menu items if the panels themselves are hidden.
            // If we don't know whether the panels are hidden, just show the menu items.
            final SharedPreferences prefs = GeckoSharedPrefs.forProfile(getContext());
            bookmarksList.setVisible(prefs.getBoolean(HomeConfig.PREF_KEY_BOOKMARKS_PANEL_ENABLED, true));
            historyList.setVisible(prefs.getBoolean(HomeConfig.PREF_KEY_HISTORY_PANEL_ENABLED, true));
        }

        return true;
    }

    private int resolveBookmarkIconID(final boolean isBookmark) {
        if (isBookmark) {
            return R.drawable.star_blue;
        } else {
            return R.drawable.ic_menu_bookmark_add;
        }
    }

    private int resolveBookmarkTitleID(final boolean isBookmark) {
        return (isBookmark ? R.string.bookmark_remove : R.string.bookmark);
    }

    private int resolveReadingListIconID(final boolean isInReadingList) {
        return (isInReadingList ? R.drawable.ic_menu_reader_remove : R.drawable.ic_menu_reader_add);
    }

    private int resolveReadingListTitleID(final boolean isInReadingList) {
        return (isInReadingList ? R.string.reading_list_remove : R.string.overlay_share_reading_list_btn_label);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Tab tab = null;
        Intent intent = null;

        final int itemId = item.getItemId();

        // Track the menu action. We don't know much about the context, but we can use this to determine
        // the frequency of use for various actions.
        String extras = getResources().getResourceEntryName(itemId);
        if (TextUtils.equals(extras, "new_private_tab")) {
            // Mask private browsing
            extras = "new_tab";
        }
        Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.MENU, extras);

        mBrowserToolbar.cancelEdit();

        if (itemId == R.id.bookmark) {
            tab = Tabs.getInstance().getSelectedTab();
            if (tab != null) {
                if (item.isChecked()) {
                    Telemetry.sendUIEvent(TelemetryContract.Event.UNSAVE, TelemetryContract.Method.MENU, "bookmark");
                    tab.removeBookmark();
                    item.setTitle(resolveBookmarkTitleID(false));
                    if (Versions.feature11Plus) {
                        // We don't use icons on GB builds so not resolving icons might conserve resources.
                        item.setIcon(resolveBookmarkIconID(false));
                    }
                } else {
                    Telemetry.sendUIEvent(TelemetryContract.Event.SAVE, TelemetryContract.Method.MENU, "bookmark");
                    tab.addBookmark();
                    item.setTitle(resolveBookmarkTitleID(true));
                    if (Versions.feature11Plus) {
                        // We don't use icons on GB builds so not resolving icons might conserve resources.
                        item.setIcon(resolveBookmarkIconID(true));
                    }
                }
            }
            return true;
        }

        if (itemId == R.id.reading_list) {
            tab = Tabs.getInstance().getSelectedTab();
            if (tab != null) {
                if (item.isChecked()) {
                    Telemetry.sendUIEvent(TelemetryContract.Event.UNSAVE, TelemetryContract.Method.MENU, "reading_list");
                    tab.removeFromReadingList();
                    item.setTitle(resolveReadingListTitleID(false));
                    if (Versions.feature11Plus) {
                        // We don't use icons on GB builds so not resolving icons might conserve resources.
                        item.setIcon(resolveReadingListIconID(false));
                    }
                } else {
                    Telemetry.sendUIEvent(TelemetryContract.Event.SAVE, TelemetryContract.Method.MENU, "reading_list");
                    tab.addToReadingList();
                    item.setTitle(resolveReadingListTitleID(true));
                    if (Versions.feature11Plus) {
                        // We don't use icons on GB builds so not resolving icons might conserve resources.
                        item.setIcon(resolveReadingListIconID(true));
                    }
                }
            }
            return true;
        }

        if (itemId == R.id.share) {
            tab = Tabs.getInstance().getSelectedTab();
            if (tab != null) {
                String url = tab.getURL();
                if (url != null) {
                    if (AboutPages.isAboutReader(url)) {
                        url = ReaderModeUtils.getUrlFromAboutReader(url);
                    }

                    // Context: Sharing via chrome list (no explicit session is active)
                    Telemetry.sendUIEvent(TelemetryContract.Event.SHARE, TelemetryContract.Method.LIST, "menu");

                    IntentHelper.openUriExternal(url, "text/plain", "", "", Intent.ACTION_SEND, tab.getDisplayTitle(), false);
                }
            }
            return true;
        }

        if (itemId == R.id.reload) {
            tab = Tabs.getInstance().getSelectedTab();
            if (tab != null)
                tab.doReload(false);
            return true;
        }

        if (itemId == R.id.back) {
            tab = Tabs.getInstance().getSelectedTab();
            if (tab != null)
                tab.doBack();
            return true;
        }

        if (itemId == R.id.forward) {
            tab = Tabs.getInstance().getSelectedTab();
            if (tab != null)
                tab.doForward();
            return true;
        }

        if (itemId == R.id.bookmarks_list) {
            final String url = AboutPages.getURLForBuiltinPanelType(PanelType.BOOKMARKS);
            Tabs.getInstance().loadUrl(url);
            Telemetry.startUISession(TelemetryContract.Session.EXPERIMENT, Experiments.BOOKMARKS_HISTORY_MENU);
            return true;
        }

        if (itemId == R.id.history_list) {
            final String url = AboutPages.getURLForBuiltinPanelType(PanelType.HISTORY);
            Tabs.getInstance().loadUrl(url);
            Telemetry.startUISession(TelemetryContract.Session.EXPERIMENT, Experiments.BOOKMARKS_HISTORY_MENU);
            return true;
        }

        if (itemId == R.id.save_as_pdf) {
            Telemetry.sendUIEvent(TelemetryContract.Event.SAVE, TelemetryContract.Method.MENU, "pdf");
            GeckoAppShell.notifyObservers("SaveAs:PDF", null);
            return true;
        }

        if (itemId == R.id.print) {
            Telemetry.sendUIEvent(TelemetryContract.Event.SAVE, TelemetryContract.Method.MENU, "print");
            PrintHelper.printPDF(this);
            return true;
        }

        if (itemId == R.id.settings) {
            intent = new Intent(this, GeckoPreferences.class);

            // We want to know when the Settings activity returns, because
            // we might need to redisplay based on a locale change.
            startActivityForResult(intent, ACTIVITY_REQUEST_PREFERENCES);
            return true;
        }

        if (itemId == R.id.help) {
            final String VERSION = AppConstants.MOZ_APP_VERSION;
            final String OS = AppConstants.OS_TARGET;
            final String LOCALE = Locales.getLanguageTag(Locale.getDefault());

            final String URL = getResources().getString(R.string.help_link, VERSION, OS, LOCALE);
            Tabs.getInstance().loadUrlInTab(URL);
            return true;
        }

        if (itemId == R.id.addons) {
            Tabs.getInstance().loadUrlInTab(AboutPages.ADDONS);
            return true;
        }

        if (itemId == R.id.logins) {
            Tabs.getInstance().loadUrlInTab(AboutPages.LOGINS);
            return true;
        }

        if (itemId == R.id.downloads) {
            Tabs.getInstance().loadUrlInTab(AboutPages.DOWNLOADS);
            return true;
        }

        if (itemId == R.id.char_encoding) {
            GeckoAppShell.notifyObservers("CharEncoding:Get", null);
            return true;
        }

        if (itemId == R.id.find_in_page) {
            mFindInPageBar.show();
            return true;
        }

        if (itemId == R.id.desktop_mode) {
            Tab selectedTab = Tabs.getInstance().getSelectedTab();
            if (selectedTab == null)
                return true;
            JSONObject args = new JSONObject();
            try {
                args.put("desktopMode", !item.isChecked());
                args.put("tabId", selectedTab.getId());
            } catch (JSONException e) {
                Log.e(LOGTAG, "error building json arguments", e);
            }
            GeckoAppShell.notifyObservers("DesktopMode:Change", args.toString());
            return true;
        }

        if (itemId == R.id.new_tab) {
            addTab();
            return true;
        }

        if (itemId == R.id.new_private_tab) {
            addPrivateTab();
            return true;
        }

        if (itemId == R.id.new_guest_session) {
            showGuestModeDialog(GuestModeDialog.ENTERING);
            return true;
        }

        if (itemId == R.id.exit_guest_session) {
            showGuestModeDialog(GuestModeDialog.LEAVING);
            return true;
        }

        // We have a few menu items that can also be in the context menu. If
        // we have not already handled the item, give the context menu handler
        // a chance.
        if (onContextItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMenuItemLongClick(MenuItem item) {
        if (item.getItemId() == R.id.reload) {
            Tab tab = Tabs.getInstance().getSelectedTab();
            if (tab != null) {
                tab.doReload(true);

                Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.MENU, "reload_force");
            }
            return true;
        }

        return super.onMenuItemLongClick(item);
    }

    public void showGuestModeDialog(final GuestModeDialog type) {
        final Prompt ps = new Prompt(this, new Prompt.PromptCallback() {
            @Override
            public void onPromptFinished(String result) {
                try {
                    int itemId = new JSONObject(result).getInt("button");
                    if (itemId == 0) {
                        String args = "";
                        if (type == GuestModeDialog.ENTERING) {
                            args = GUEST_BROWSING_ARG;
                        } else {
                            GeckoProfile.leaveGuestSession(BrowserApp.this);

                            // Now's a good time to make sure we're not displaying the Guest Browsing notification.
                            GuestSession.hideNotification(BrowserApp.this);
                        }

                        doRestart(args);
                    }
                } catch(JSONException ex) {
                    Log.e(LOGTAG, "Exception reading guest mode prompt result", ex);
                }
            }
        });

        Resources res = getResources();
        ps.setButtons(new String[] {
            res.getString(R.string.guest_session_dialog_continue),
            res.getString(R.string.guest_session_dialog_cancel)
        });

        int titleString = 0;
        int msgString = 0;
        if (type == GuestModeDialog.ENTERING) {
            titleString = R.string.new_guest_session_title;
            msgString = R.string.new_guest_session_text;
        } else {
            titleString = R.string.exit_guest_session_title;
            msgString = R.string.exit_guest_session_text;
        }

        ps.show(res.getString(titleString), res.getString(msgString), null, ListView.CHOICE_MODE_NONE);
    }

    /**
     * This will detect if the key pressed is back. If so, will show the history.
     */
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // If the tab search history is already shown, do nothing.
            TabHistoryFragment frag = (TabHistoryFragment) getSupportFragmentManager().findFragmentByTag(TAB_HISTORY_FRAGMENT_TAG);
            if (frag != null) {
                return false;
            }

            Tab tab = Tabs.getInstance().getSelectedTab();
            if (tab != null  && !tab.isEditing()) {
                return tabHistoryController.showTabHistory(tab, TabHistoryController.HistoryAction.ALL);
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    /*
     * If the app has been launched a certain number of times, and we haven't asked for feedback before,
     * open a new tab with about:feedback when launching the app from the icon shortcut.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        String action = intent.getAction();

        final boolean isViewAction = Intent.ACTION_VIEW.equals(action);
        final boolean isBookmarkAction = GeckoApp.ACTION_HOMESCREEN_SHORTCUT.equals(action);
        final boolean isTabQueueAction = TabQueueHelper.LOAD_URLS_ACTION.equals(action);
        final boolean isViewMultipleAction = ACTION_VIEW_MULTIPLE.equals(action);

        if (mInitialized && (isViewAction || isBookmarkAction)) {
            // Dismiss editing mode if the user is loading a URL from an external app.
            mBrowserToolbar.cancelEdit();

            // Hide firstrun-pane if the user is loading a URL from an external app.
            hideFirstrunPager(TelemetryContract.Method.NONE);

            if (isBookmarkAction) {
                // GeckoApp.ACTION_HOMESCREEN_SHORTCUT means we're opening a bookmark that
                // was added to Android's homescreen.
                Telemetry.sendUIEvent(TelemetryContract.Event.LOAD_URL, TelemetryContract.Method.HOMESCREEN);
            }
        }

        showTabQueuePromptIfApplicable(intent);

        super.onNewIntent(intent);

        if (AppConstants.MOZ_ANDROID_BEAM && NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            String uri = intent.getDataString();
            GeckoAppShell.sendEventToGecko(GeckoEvent.createURILoadEvent(uri));
        }

        // Only solicit feedback when the app has been launched from the icon shortcut.
        if (GuestSession.NOTIFICATION_INTENT.equals(action)) {
            GuestSession.handleIntent(this, intent);
        }

        // If the user has clicked the tab queue notification then load the tabs.
        if (TabQueueHelper.TAB_QUEUE_ENABLED && mInitialized && isTabQueueAction) {
            Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.NOTIFICATION, "tabqueue");
            ThreadUtils.postToBackgroundThread(new Runnable() {
                @Override
                public void run() {
                    openQueuedTabs();
                }
            });
        }

        // Custom intent action for opening multiple URLs at once
        if (isViewMultipleAction) {
            List<String> urls = intent.getStringArrayListExtra("urls");
            if (urls != null) {
                openUrls(urls);
            }

            // Launched from a "content notification"
            if (intent.hasExtra(CheckForUpdatesAction.EXTRA_CONTENT_NOTIFICATION)) {
                Telemetry.sendUIEvent(TelemetryContract.Event.ACTION, TelemetryContract.Method.NOTIFICATION, "content_update");
            }
        }

        if (!mInitialized || !Intent.ACTION_MAIN.equals(action)) {
            return;
        }

        // Check to see how many times the app has been launched.
        final String keyName = getPackageName() + ".feedback_launch_count";
        final StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskReads();

        // Faster on main thread with an async apply().
        try {
            SharedPreferences settings = getPreferences(Activity.MODE_PRIVATE);
            int launchCount = settings.getInt(keyName, 0);
            if (launchCount < FEEDBACK_LAUNCH_COUNT) {
                // Increment the launch count and store the new value.
                launchCount++;
                settings.edit().putInt(keyName, launchCount).apply();

                // If we've reached our magic number, show the feedback page.
                if (launchCount == FEEDBACK_LAUNCH_COUNT) {
                    GeckoAppShell.notifyObservers("Feedback:Show", null);
                }
            }
        } finally {
            StrictMode.setThreadPolicy(savedPolicy);
        }
    }

    private void openUrls(List<String> urls) {
        try {
            JSONArray array = new JSONArray();
            for (String url : urls) {
                array.put(url);
            }

            JSONObject object = new JSONObject();
            object.put("urls", array);

            GeckoAppShell.notifyObservers("Tabs:OpenMultiple", object.toString());
        } catch (JSONException e) {
            Log.e(LOGTAG, "Unable to create JSON for opening multiple URLs");
        }
    }

    private void showTabQueuePromptIfApplicable(final Intent intent) {
        ThreadUtils.postToBackgroundThread(new Runnable() {
            @Override
            public void run() {
                // We only want to show the prompt if the browser has been opened from an external url
                if (TabQueueHelper.TAB_QUEUE_ENABLED && mInitialized
                                                     && Intent.ACTION_VIEW.equals(intent.getAction())
                                                     && !intent.getBooleanExtra(BrowserContract.SKIP_TAB_QUEUE_FLAG, false)
                                                     && TabQueueHelper.shouldShowTabQueuePrompt(BrowserApp.this)) {
                    Intent promptIntent = new Intent(BrowserApp.this, TabQueuePrompt.class);
                    startActivityForResult(promptIntent, ACTIVITY_REQUEST_TAB_QUEUE);
                }
            }
        });
    }

    @Override
    protected NotificationClient makeNotificationClient() {
        // The service is local to Fennec, so we can use it to keep
        // Fennec alive during downloads.
        return new ServiceNotificationClient(getApplicationContext());
    }

    private void resetFeedbackLaunchCount() {
        SharedPreferences settings = getPreferences(Activity.MODE_PRIVATE);
        settings.edit().putInt(getPackageName() + ".feedback_launch_count", 0).apply();
    }

    // HomePager.OnUrlOpenListener
    @Override
    public void onUrlOpen(String url, EnumSet<OnUrlOpenListener.Flags> flags) {
        if (flags.contains(OnUrlOpenListener.Flags.OPEN_WITH_INTENT)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } else if (!maybeSwitchToTab(url, flags)) {
            openUrlAndStopEditing(url);
            clearSelectedTabApplicationId();
        }
    }

    // HomePager.OnUrlOpenInBackgroundListener
    @Override
    public void onUrlOpenInBackground(final String url, EnumSet<OnUrlOpenInBackgroundListener.Flags> flags) {
        if (url == null) {
            throw new IllegalArgumentException("url must not be null");
        }
        if (flags == null) {
            throw new IllegalArgumentException("flags must not be null");
        }

        final boolean isPrivate = flags.contains(OnUrlOpenInBackgroundListener.Flags.PRIVATE);

        int loadFlags = Tabs.LOADURL_NEW_TAB | Tabs.LOADURL_BACKGROUND;
        if (isPrivate) {
            loadFlags |= Tabs.LOADURL_PRIVATE;
        }

        final Tab newTab = Tabs.getInstance().loadUrl(url, loadFlags);

        // We switch to the desired tab by unique ID, which closes any window
        // for a race between opening the tab and closing it, and switching to
        // it. We could also switch to the Tab explicitly, but we don't want to
        // hold a reference to the Tab itself in the anonymous listener class.
        final int newTabId = newTab.getId();

        final SnackbarHelper.SnackbarCallback callback = new SnackbarHelper.SnackbarCallback() {
            @Override
            public void onClick(View v) {
                Telemetry.sendUIEvent(TelemetryContract.Event.SHOW, TelemetryContract.Method.TOAST, "switchtab");

                maybeSwitchToTab(newTabId);
            }
        };

        final String message = isPrivate ?
                getResources().getString(R.string.new_private_tab_opened) :
                getResources().getString(R.string.new_tab_opened);
        final String buttonMessage = getResources().getString(R.string.switch_button_message);

        SnackbarHelper.showSnackbarWithAction(this, message, Snackbar.LENGTH_LONG, buttonMessage, callback);
    }

    // BrowserSearch.OnSearchListener
    @Override
    public void onSearch(SearchEngine engine, String text) {
        // Don't store searches that happen in private tabs. This assumes the user can only
        // perform a search inside the currently selected tab, which is true for searches
        // that come from SearchEngineRow.
        if (!Tabs.getInstance().getSelectedTab().isPrivate()) {
            storeSearchQuery(text);
        }
        recordSearch(engine, "barsuggest");
        openUrlAndStopEditing(text, engine.name);
    }

    // BrowserSearch.OnEditSuggestionListener
    @Override
    public void onEditSuggestion(String suggestion) {
        mBrowserToolbar.onEditSuggestion(suggestion);
    }

    @Override
    public int getLayout() { return R.layout.gecko_app; }

    @Override
    protected String getDefaultProfileName() throws NoMozillaDirectoryException {
        return GeckoProfile.getDefaultProfileName(this);
    }

    // For use from tests only.
    @RobocopTarget
    public ReadingListHelper getReadingListHelper() {
        return mReadingListHelper;
    }

    /**
     * Launch UI that lets the user update Firefox.
     *
     * This depends on the current channel: Release and Beta both direct to the
     * Google Play Store.  If updating is enabled, Aurora, Nightly, and custom
     * builds open about:, which provides an update interface.
     *
     * If updating is not enabled, this simply logs an error.
     *
     * @return true if update UI was launched.
     */
    protected boolean handleUpdaterLaunch() {
        if (AppConstants.RELEASE_BUILD) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + getPackageName()));
            startActivity(intent);
            return true;
        }

        if (AppConstants.MOZ_UPDATER) {
            Tabs.getInstance().loadUrlInTab(AboutPages.UPDATER);
            return true;
        }

        Log.w(LOGTAG, "No candidate updater found; ignoring launch request.");
        return false;
    }

    /* Implementing ActionModeCompat.Presenter */
    @Override
    public void startActionModeCompat(final ActionModeCompat.Callback callback) {
        // If actionMode is null, we're not currently showing one. Flip to the action mode view
        if (mActionMode == null) {
            mActionBarFlipper.showNext();
            DynamicToolbarAnimator toolbar = mLayerView.getDynamicToolbarAnimator();

            // If the toolbar is dynamic and not currently showing, just slide it in
            if (mDynamicToolbar.isEnabled() && toolbar.getToolbarTranslation() != 0) {
                mDynamicToolbar.setTemporarilyVisible(true, VisibilityTransition.ANIMATE);
            } else {
                // Otherwise, we animate the actionbar itself
                mActionBar.animateIn();
            }

            mDynamicToolbar.setPinned(true, PinReason.ACTION_MODE);
        } else {
            // Otherwise, we're already showing an action mode. Just finish it and show the new one
            mActionMode.finish();
        }

        mActionMode = new ActionModeCompat(BrowserApp.this, callback, mActionBar);
        if (callback.onCreateActionMode(mActionMode, mActionMode.getMenu())) {
            mActionMode.invalidate();
        }
    }

    /* Implementing ActionModeCompat.Presenter */
    @Override
    public void endActionModeCompat() {
        if (mActionMode == null) {
            return;
        }

        mActionMode.finish();
        mActionMode = null;
        mDynamicToolbar.setPinned(false, PinReason.ACTION_MODE);

        mActionBarFlipper.showPrevious();

        // Only slide the urlbar out if it was hidden when the action mode started
        // Don't animate hiding it so that there's no flash as we switch back to url mode
        mDynamicToolbar.setTemporarilyVisible(false, VisibilityTransition.IMMEDIATE);
    }

    @WorkerThread // synchronous SharedPrefs write.
    private static void uploadTelemetry(final Context context, final GeckoProfile profile,
            final org.mozilla.gecko.search.SearchEngine defaultEngine) {
        if (!TelemetryUploadService.isUploadEnabledByProfileConfig(context, profile)) {
            return;
        }

        final SharedPreferences sharedPrefs = GeckoSharedPrefs.forProfileName(context, profile.getName());
        final int seq = sharedPrefs.getInt(TelemetryConstants.PREF_SEQ_COUNT, 1);

        // We store synchronously before sending the Intent to ensure this sequence number will not be re-used.
        sharedPrefs.edit().putInt(TelemetryConstants.PREF_SEQ_COUNT, seq + 1).commit();

        final Intent i = new Intent(TelemetryConstants.ACTION_UPLOAD_CORE);
        i.setClass(context, TelemetryUploadService.class);
        i.putExtra(TelemetryConstants.EXTRA_DEFAULT_SEARCH_ENGINE, defaultEngine.getIdentifier());
        i.putExtra(TelemetryConstants.EXTRA_DOC_ID, UUID.randomUUID().toString());
        i.putExtra(TelemetryConstants.EXTRA_PROFILE_NAME, profile.getName());
        i.putExtra(TelemetryConstants.EXTRA_PROFILE_PATH, profile.getDir().getAbsolutePath());
        i.putExtra(TelemetryConstants.EXTRA_SEQ, seq);
        context.startService(i);
    }

    private static class UploadTelemetryCallback implements SearchEngineManager.SearchEngineCallback {
        private final WeakReference<BrowserApp> activityWeakReference;

        public UploadTelemetryCallback(final BrowserApp activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        // May be called from any thread.
        @Override
        public void execute(final org.mozilla.gecko.search.SearchEngine engine) {
            // Don't waste resources queueing to the background thread if we don't have a reference.
            if (this.activityWeakReference.get() == null) {
                return;
            }

            // The containing method can be called from onStart: queue this work so that
            // the first launch of the activity doesn't trigger profile init too early.
            //
            // Additionally, uploadTelemetry must be called from a worker thread.
            ThreadUtils.postToBackgroundThread(new Runnable() {
                @WorkerThread
                @Override
                public void run() {
                    final BrowserApp activity = activityWeakReference.get();
                    if (activity == null) {
                        return;
                    }
                    uploadTelemetry(activity, activity.getProfile(), engine);
                }
            });
        }
    }

    public static interface TabStripInterface {
        public void refresh();
        void setOnTabChangedListener(OnTabAddedOrRemovedListener listener);
        interface OnTabAddedOrRemovedListener {
            void onTabChanged();
        }
    }

    @Override
    protected StartupAction getStartupAction(final String passedURL, final String action) {
        final boolean inGuestMode = GeckoProfile.get(this).inGuestMode();
        if (inGuestMode) {
            return StartupAction.GUEST;
        }
        if (Restrictions.isRestrictedProfile(this)) {
            return StartupAction.RESTRICTED;
        }
        if (ACTION_HOMESCREEN_SHORTCUT.equals(action)) {
            return StartupAction.SHORTCUT;
        }
        return (passedURL == null ? StartupAction.NORMAL : StartupAction.URL);
    }
}
