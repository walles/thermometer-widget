package net.launchpad.thermometer;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper activity for preferences and log viewing.
 */
public class ThermometerActions extends Activity {
    public static final String SHOW_LOGS_EXTRA = "SHOW_LOGS_EXTRA";

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        ActionBar actionBar = getActionBar();
        assert actionBar != null;

        Tab selectedTab = actionBar.getSelectedTab();
        assert selectedTab != null;

        outState.putInt("selectedTab", selectedTab.getPosition());
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        assert actionBar != null;

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        actionBar.addTab(actionBar.newTab().
                setText("Settings").
                setTabListener(new TabListener<ThermometerConfigure>(
                        this, "settings", ThermometerConfigure.class)));

        actionBar.addTab(actionBar.newTab().
                setText("Logs").
                setTabListener(new TabListener<ThermometerLogViewer>(
                        this, "logs", ThermometerLogViewer.class)));

        if (savedInstanceState != null) {
            int selectedTab = savedInstanceState.getInt("selectedTab", 0);
            actionBar.selectTab(actionBar.getTabAt(selectedTab));
        } else if (getIntent().getBooleanExtra(SHOW_LOGS_EXTRA, false)) {
            // "1" == the index of the Logs tab
            actionBar.selectTab(actionBar.getTabAt(1));
        }
    }
}

/**
 * From http://developer.android.com/guide/topics/ui/actionbar.html#Tabs
 *
 * @param <T> The Fragment we're listening for
 */
class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private Fragment mFragment;
    private final Activity mActivity;
    private final String mTag;
    private final Class<T> mClass;

    /** Constructor used each time a new tab is created.
     * @param activity  The host Activity, used to instantiate the fragment
     * @param tag  The identifier tag for the fragment
     * @param clz  The fragment's Class, used to instantiate the fragment
     */
    public TabListener(Activity activity, String tag, Class<T> clz) {
        mActivity = activity;
        mTag = tag;
        mClass = clz;
    }

    /* The following are each of the ActionBar.TabListener callbacks */
    @Override
    public void onTabSelected(Tab tab, @NotNull FragmentTransaction ft) {
        // Check if the fragment is already initialized
        if (mFragment == null) {
            // If not, instantiate and add it to the activity
            mFragment = Fragment.instantiate(mActivity, mClass.getName());
            ft.add(android.R.id.content, mFragment, mTag);
        } else {
            // If it exists, simply attach it in order to show it
            ft.attach(mFragment);
        }
    }

    @Override
    public void onTabUnselected(Tab tab, @NotNull FragmentTransaction ft) {
        if (mFragment != null) {
            // Detach the fragment, because another one is being attached
            ft.detach(mFragment);
        }
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
        // User selected the already selected tab. Usually do nothing.
    }
}
