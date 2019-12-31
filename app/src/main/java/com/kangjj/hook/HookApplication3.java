package com.kangjj.hook;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * @Description: 9.0Hook
 * @Author: jj.kang
 * @Email: 345498912@qq.com
 * @ProjectName: 2.3.5_HookProject
 * @Package: com.kangjj.hook
 * @CreateDate: 2019/12/15 9:52
 */
public class HookApplication3 extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            hookAmsAction();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("hook", "hookAmsAction 失败 e:" + e.toString());
        }

        try {
            hookLuanchActivity();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("hook", "hookLuanchActivity 失败 e:" + e.toString());
        }

        try{
            plugin2AppAction();
        }catch (Exception e){
            e.printStackTrace();
            Log.d("hook", "plugin2AppAction 失败 e:" + e.toString());
        }
    }

    /**
     * Hook的技巧 找到Hook点 对他jinxingHook 他要什么就给他什么
     * @throws Exception
     */
    private void hookAmsAction() throws Exception{
        Class<?> mActivityManagerClass = Class.forName("android.app.ActivityManager");
        Field mIActivityManagerSingletonField = mActivityManagerClass.getDeclaredField("IActivityManagerSingleton");
        mIActivityManagerSingletonField.setAccessible(true);
        Object mIActivityManagerSingleton = mIActivityManagerSingletonField.get(null);

        Class<?> singletonClazz = Class.forName("android.util.Singleton");
        Field mInstanceField = singletonClazz.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);                  //IActivityManager对象

        Class mIActivityManagerClass = Class.forName("android.app.IActivityManager");
        final Object mIActivityManager = mActivityManagerClass.getMethod("getService").invoke(null);
        Object mIActivityManagerProxy = Proxy.newProxyInstance(
                getClassLoader(),
                new Class[]{mIActivityManagerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if(method.getName().equals("startActivity")){
                            Intent proxyIntent = new Intent(HookApplication3.this,ProxyActivity.class);
                            proxyIntent.putExtra("targetIntent",(Intent)args[2]);
                            args[2] = proxyIntent;
                        }
                        return method.invoke(mIActivityManager,args);
                    }
                });

        mInstanceField.set(mIActivityManagerSingleton,mIActivityManagerProxy);

    }

    /**
     * 真正加载的是偶，把代理Activity给换成真实的目标
     * @throws Exception
     */
    private void hookLuanchActivity() throws Exception{
        Field mCallbackField = Handler.class.getDeclaredField("mCallback");
        mCallbackField.setAccessible(true);

        Class<?> mActivityThreadClazz = Class.forName("android.app.ActivityThread");
        Method mCurrentActivityThreadMethod = mActivityThreadClazz.getDeclaredMethod("currentActivityThread");
        Object mActivityThreadObj = mCurrentActivityThreadMethod.invoke(null);
        Field mHField = mActivityThreadClazz.getDeclaredField("mH");
        mHField.setAccessible(true);
        Object mH = mHField.get(mActivityThreadObj);
        mCallbackField.set(mH,new MyCallback());
    }
    public static final int EXECUTE_TRANSACTION = 159;
    private class MyCallback implements Handler.Callback{

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case EXECUTE_TRANSACTION:
                    try{
                        Object mClientTransaction = msg.obj;//ClientTransaction

                        Field mActivityCallbacksField = mClientTransaction.getClass().getDeclaredField("mActivityCallbacks");
                        mActivityCallbacksField.setAccessible(true);
                        List mActivityCallbacks = (List) mActivityCallbacksField.get(msg.obj);//List<ClientTransactionItem>

                        Class<?> mLaunchActivityItemClazz = Class.forName("android.app.servertransaction.LaunchActivityItem");//LaunchActivityItem extends ClientTransactionItem

                        if(mActivityCallbacks.size()==0 ){
                            return false;// 然后 继续 加载 系统的 正常执行下去吧
                        }
                        Object mClientTranscationItem = mActivityCallbacks.get(0);
                        // 再次验证 (是否有关联  LaunchActivityItem  -- mClientTransactionItem )
                        if(mLaunchActivityItemClazz.isInstance(mClientTranscationItem) == false){
                            return false;
                        }
                        Field mIntentField = mLaunchActivityItemClazz.getDeclaredField("mIntent");
                        mIntentField.setAccessible(true);
                        Intent proxyIntent = (Intent)mIntentField.get(mClientTranscationItem);
                        Intent targetIntent = proxyIntent.getParcelableExtra("targetIntent");
                        if(targetIntent!=null){
                            mIntentField.set(mClientTranscationItem,targetIntent);
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    break;
            }
            return false;
        }
    }


    private File pluginFile;
    private AssetManager assetManager;
    private Resources resources;
    /**
     * 把插件的dexElements 和宿主的dexElements融为一体
     * @throws Exception
     */
    private void plugin2AppAction() throws Exception {
        // 第一步：找到宿主 dexElements 得到此对象
        //zygoteInit --> BootClassLoader.getInstance();     handleSystemServerProcess PathClassLoaderFactory --》PathClassLoader
        PathClassLoader pathClassLoader = (PathClassLoader) this.getClassLoader();        // PathClassLoader代表是宿主
        Class baseDexClassLoaderClass = Class.forName("dalvik.system.BaseDexClassLoader");
        Field pathListField= baseDexClassLoaderClass.getDeclaredField("pathList");
        pathListField.setAccessible(true);
        Object pathListObj = pathListField.get(pathClassLoader);

        Field dexElementsField= pathListObj.getClass().getDeclaredField("dexElements");
        dexElementsField.setAccessible(true);
        //本质是Element[] dexElements
        Object dexElements = dexElementsField.get(pathListObj);

        // 第二步：找到插件 dexElements 得到此对象，代表插件 DexClassLoader--代表插件
        pluginFile = new File(Environment.getExternalStorageDirectory() + File.separator + "kangjj.plugin");
//        pluginFile = new File("/sdcard/" + File.separator + "kangjj.plugin");
        if (!pluginFile.exists()) {
            throw new FileNotFoundException("没有找到插件包!!");
        }
        File fileDir = this.getDir("pluginDir", Context.MODE_PRIVATE);// data/data/包名/pluginDir/
        DexClassLoader dexClassLoader = new DexClassLoader(pluginFile.getAbsolutePath(),fileDir.getAbsolutePath(),null,getClassLoader());

        Class baseDexClassLoaderClassPlugin = Class.forName("dalvik.system.BaseDexClassLoader");
        Field pathListFieldPlugin = baseDexClassLoaderClassPlugin.getDeclaredField("pathList");
        pathListFieldPlugin.setAccessible(true);
        Object pathListObjPlugin = pathListFieldPlugin.get(dexClassLoader);

        Field dexElementsFieldPlugin= pathListObjPlugin.getClass().getDeclaredField("dexElements");
        dexElementsFieldPlugin.setAccessible(true);
        //本质是Element[] dexElements
        Object dexElementsPlugin = dexElementsFieldPlugin.get(pathListObjPlugin);
        // 第三步：创建出 新的 dexElements []
        int mainDexLength = Array.getLength(dexElements);
        int pluginDexLength = Array.getLength(dexElementsPlugin);
        int sumDexLength = mainDexLength + pluginDexLength;

        // 参数一：int[]  String[] ...  我们需要Element[]
        // 参数二：数组对象的长度
        // 本质就是 Element[] newDexElements
        Object newDexElements = Array.newInstance(dexElements.getClass().getComponentType(), sumDexLength);
        // 第四步：宿主dexElements + 插件dexElements =----> 融合  新的 newDexElements
        for (int i = 0; i < sumDexLength; i++) {
            if(i<mainDexLength){
                Array.set(newDexElements,i,Array.get(dexElements,i));
            }else{
                Array.set(newDexElements,i,Array.get(dexElementsPlugin,i-mainDexLength));
            }
        }

        // 第五步：把新的 newDexElements，设置到宿主中去
        dexElementsField.set(pathListObj,newDexElements);

        //处理加载插件中的布局、资源
        doPluaginLayoutLoad();


    }

    private void doPluaginLayoutLoad() throws Exception{
        assetManager = AssetManager.class.newInstance();
        /*public int addAssetPath(String path)    @hide 只能用反射*/
        Method addAssetPathMethodPlugin = assetManager.getClass().getDeclaredMethod("addAssetPath", String.class);
//        addAssetPathMethodPlugin.setAccessible(true);//public 不需要setAccessible
        addAssetPathMethodPlugin.invoke(assetManager, pluginFile.getAbsolutePath());
        Resources r = getResources();// 拿到的是宿主的 配置信息 这里有可能拿到了插件的Resources，不过没关系，因为已经被宿主赋值了
        // 实例化此方法 final StringBlock[] ensureStringBlocks()
        // 9.0是在AssetManager里面获的  private ApkAssets[] mApkAssets -》 private StringBlock mStringBlock
        //9.0 AssetManager的无参构造函数已经实例化了。 AssetManager.class.newInstance()已经调用到 直接获取mApkAssets？
        //TODO  9.0没有此方法，把ensureStringBlocks放入到了ApkAssets[] mApkAssets内部，并且该值在上方的addAssetpath->addAssetPathInternal内部已经赋值416行，所以下面代码屏蔽。
//        Method ensureStringBlocksMethod = assetManager.getClass().getDeclaredMethod("ensureStringBlocks");
//        ensureStringBlocksMethod.setAccessible(true);
//        ensureStringBlocksMethod.invoke(assetManager);  // 执行了ensureStringBlocks  string.xml  color.xml   anim.xml 被初始化
        // 特殊：专门加载插件资源
        resources = new Resources(assetManager,r.getDisplayMetrics(),r.getConfiguration());

    }

    @Override
    public Resources getResources() {
        return resources == null ? super.getResources():resources;
    }

    @Override
    public AssetManager getAssets() {
        return assetManager == null ? super.getAssets() : assetManager;
    }
}
