package com.kangjj.hook.plugin;

import android.os.Bundle;
import android.widget.Toast;

public class PluginActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugin_main);
        // 占位式 报错
        // Toast.makeText(appActivity, "plugin", Toast.LENGTH_SHORT).show();

        // Hook式 不会报错  this 当前运行宿主  插件中的dexElements 和 宿主中的dexElements
        Toast.makeText(this, "plugin", Toast.LENGTH_SHORT).show();
    }
}
