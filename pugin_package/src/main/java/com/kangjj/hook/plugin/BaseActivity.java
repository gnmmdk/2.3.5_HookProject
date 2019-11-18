package com.kangjj.hook.plugin;

import android.app.Activity;
import android.content.res.AssetManager;
import android.content.res.Resources;

public class BaseActivity extends Activity {

    @Override
    public Resources getResources() {
        if (getApplication() != null && getApplication().getResources() != null) {  //这里插件和宿主已经融为一体，所以获取到的是HookApplication
            return getApplication().getResources();
        }
        return super.getResources();
    }

    @Override
    public AssetManager getAssets() {
        if (getApplication() != null && getApplication().getAssets() != null) {
            return getApplication().getAssets();
        }
        return super.getAssets();
    }
}
