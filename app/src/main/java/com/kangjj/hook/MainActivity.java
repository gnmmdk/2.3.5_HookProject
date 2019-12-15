package com.kangjj.hook;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView textView = new TextView(this);
        textView.setText(R.string.app_name);
    }

    /**
     * 启动TestActivity
     * @param view
     */
    public void startTestActivity(View view) {
//         startActivity(new Intent(this, TestActivity.class));
//        startActivity(new Intent(this, Test2Activity.class));

        // 宿主中  去启动插件里面的PluginActivity -- (插件里面的Activity) todo test plugin
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com.kangjj.hook.plugin", "com.kangjj.hook.plugin.PluginActivity"));
        startActivity(intent);


        //todo test 9.0
//        startActivity(new Intent(this, TestActivity.class));
    }
}
