package com.kangjj.hook;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * 适配7.1.1源码 注意一定要申请存储权限
 */
public class HookApplication extends Application {
    // 增加权限的管理
    private static List<String> activityList = new ArrayList<>();

    static {
        activityList.add(TestActivity.class.getName()); // 有权限
    }


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
//         融合方式Dex方式
//        try{
//            //将该代码替换到课堂代码是可以运行， hook跟android版本有关，也需要配置AndroidStudio的环境 compileSdkVersion  targetSdkVersion?
//            plugin2AppAction();
//        }catch (Exception e){
//            e.printStackTrace();
//            Log.d("hook", "plugin2AppAction 失败 e:" + e.toString());
//        }

        try {
//          自己创造一个LoadedApk.ClassLoader 添加到 mPackages，此LoadedApk 专门用来加载插件里面的 class
//          HookCallback也要处理
            customLoadedApkAction();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("hook", "customLoadedApkAction 失败 e:" + e.toString());
        }
    }

    /**
     * 要在执行 AMS之前，替换可用的 Activity，替换在AndroidManifest里面配置的Activity
     * @throws Exception
     */
    private void hookAmsAction() throws Exception{

        Class mIActivityManagerClass = Class.forName("android.app.IActivityManager");

        /**
         * 为了拿到 gDefault
         *  通过 ActivityManagerNative 拿到 gDefault变量(对象)
         */
        final Class mActivityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");
        Field gDefaultField = mActivityManagerNativeClass.getDeclaredField("gDefault");
        gDefaultField.setAccessible(true);      // 授权
        Object gDefault = gDefaultField.get(null);
        //获取到IActivityManager
        final Object mIActivityManager = mActivityManagerNativeClass.getMethod("getDefault").invoke(null);


        Object mIActivityManageProxy= Proxy.newProxyInstance(getClassLoader(), new Class[]{mIActivityManagerClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if(method.getName().equals("startActivity")){
                    // 做自己的业务逻辑
                    // 换成 可以 通过 AMS检查的 ProxyActivity

                    // 用ProxyActivity 绕过了 AMS检查
                    Intent oldIntent = (Intent) args[2];
                    Intent intent = new Intent(HookApplication.this,ProxyActivity.class);
                    intent.putExtra("actionIntent",oldIntent);// 把之前Activity保存 携带过去
                    args[2] = intent;
                }
                Log.d("hook", "拦截到了IActivityManager里面的方法" + method.getName());
                // 让系统继续正常往下执行
                return method.invoke(mIActivityManager,args);
            }
        });


        //替换点 将mIinstance替换代理IActivityManager
        Class mSingletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField= mSingletonClass.getDeclaredField("mInstance");//Singleton里面的mInstatance是IActivityManager
        mInstanceField.setAccessible(true);// 让虚拟机不要检测 权限修饰符
        mInstanceField.set(gDefault,mIActivityManageProxy);          //mInstance所属的类是gDefault
    }


    private void hookLuanchActivity() throws Exception{
        Field mCallbackField = Handler.class.getDeclaredField("mCallback");
        mCallbackField.setAccessible(true);


        /**
         * handler对象怎么来
         * 1.寻找H，先寻找ActivityThread
         *
         * 执行此方法 public static ActivityThread currentActivityThread()
         *
         * 通过ActivityThread 找到 H
         *
         */
        Class mActivityThreadClass = Class.forName("android.app.ActivityThread");
        Method mActivityThreadMethod = mActivityThreadClass.getMethod("currentActivityThread");
        // 获得ActivityThrea对象
        Object  mActivityThreadObj = mActivityThreadMethod.invoke(null);
//        Field mActivityThreadField = mActivityThreadClass.getDeclaredField("sCurrentActivityThread");
//        mActivityThreadField.setAccessible(true);
//        Object mActivityThreadObj = mActivityThreadField.get(null);

        Field mHField = mActivityThreadClass.getDeclaredField("mH");
        mHField.setAccessible(true);
        Handler mH = (Handler) mHField.get(mActivityThreadObj);

        mCallbackField.set(mH ,new HookCallback(mH)); //ActivityThread对象里面的mH
    }
    public static final int LAUNCH_ACTIVITY         = 100;
    class HookCallback implements Handler.Callback{

        private Handler mH;

        public HookCallback(Handler mH) {
            this.mH = mH;
        }
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case LAUNCH_ACTIVITY:
                    try {
                        // 做我们在自己的业务逻辑（把ProxyActivity 换成  TestActivity）
                        Object obj = msg.obj;//ActivityClientRecord
                        // 我们要获取之前Hook携带过来的 TestActivity
                        Field mIntentField = obj.getClass().getDeclaredField("intent");
                        mIntentField.setAccessible(true);
                        Intent proxyIntent = (Intent) mIntentField.get(obj);
                        Intent actionIntent = proxyIntent.getParcelableExtra("actionIntent");
                        if(actionIntent!=null){
                            /*if(activityList.contains(actionIntent.getComponent().getClassName())){
                                mIntentField.set(obj,actionIntent);
                            }else{
                                mIntentField.set(obj,new Intent(HookApplication.this,PermissionActivity.class));
                            }*/
                            mIntentField.set(obj,actionIntent);
                            /*****************************************LoadedApk方式*******************************************/
                            Field activityInfoField = obj.getClass().getDeclaredField("activityInfo");
                            activityInfoField.setAccessible(true);
                            ActivityInfo activityInfo = (ActivityInfo) activityInfoField.get(obj);

                            if(actionIntent.getPackage() == null){// 证明是插件
                                activityInfo.applicationInfo.packageName = actionIntent.getComponent().getPackageName();

                                //Hook拦截此 getPackageInfo做自己的逻辑
                                hookGetPackageInfo();
                            }else{
                                activityInfo.applicationInfo.packageName = actionIntent.getPackage();
                            }
                            /*****************************************LoadedApk方式*******************************************/
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
            }
            mH.handleMessage(msg);      //这里也可以不传mH进来，但是最后要返回 return false
            return true;        //表示不执行系统的代码
//            return false;//表示继续执行系统的代码
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
        Method ensureStringBlocksMethod = assetManager.getClass().getDeclaredMethod("ensureStringBlocks");
        ensureStringBlocksMethod.setAccessible(true);
        ensureStringBlocksMethod.invoke(assetManager);  // 执行了ensureStringBlocks  string.xml  color.xml   anim.xml 被初始化
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

    /**
     * 自己创造一个LoadedApk.ClassLoader添加到mPackages，此LoadedApk专门用来加载插件里面的class
     * @throws Exception
     */
    private void customLoadedApkAction() throws Exception{
        pluginFile = new File(Environment.getExternalStorageDirectory() + File.separator + "kangjj.plugin");
        if (!pluginFile.exists()) {
            throw new FileNotFoundException("插件包不存在..." + pluginFile.getAbsolutePath());
        }

        Class<?> mActivityThreadClazz = Class.forName("android.app.ActivityThread");
        Method mCurrentActivityThreadMethod = mActivityThreadClazz.getMethod("currentActivityThread");
        Object mActivityThreadObj = mCurrentActivityThreadMethod.invoke(null);

        Field mPackagesField = mActivityThreadClazz.getDeclaredField("mPackages");
        mPackagesField.setAccessible(true);
        Object mPackagesObj = mPackagesField.get(mActivityThreadObj);

        Class<?> mCompatibilityInfoClazz = Class.forName("android.content.res.CompatibilityInfo");
        Field mCompatibilityInfoField = mCompatibilityInfoClazz.getField("DEFAULT_COMPATIBILITY_INFO");
        Object mCompatibilityInfoObj = mCompatibilityInfoField.get(null);

        ApplicationInfo applicationInfo = getApplicationInfoAction();

        // 使用getPackageInfoNoCheck创建LoadedApk，以供下方加入到mPackages
        //public final LoadedApk getPackageInfoNoCheck(ApplicationInfo ai,CompatibilityInfo compatInfo)
        Method mGetPackageInfoNoCheckMethod = mActivityThreadClazz.getMethod("getPackageInfoNoCheck", ApplicationInfo.class, mCompatibilityInfoClazz);
        Object mLoadedApk = mGetPackageInfoNoCheckMethod.invoke(mActivityThreadObj,applicationInfo , mCompatibilityInfoObj);

        File dirFile = getDir("pluginPathDir",Context.MODE_PRIVATE);
        ClassLoader classLoader = new PluginClassLoader(pluginFile.getAbsolutePath(),dirFile.getAbsolutePath(),null,getClassLoader());

        Field mClassLoaderField = mLoadedApk.getClass().getDeclaredField("mClassLoader");
        mClassLoaderField.setAccessible(true);
        mClassLoaderField.set(mLoadedApk,classLoader);// 替换 LoadedApk 里面的 ClassLoader

        //ArrayMap<String, WeakReference<LoadedApk>> mPackages
        Map mPackages = (Map)mPackagesObj;
        // 添加自定义的 LoadedApk 专门加载 插件里面的 class
        // 最终的目标 mPackages.put(插件的包名，插件的LoadedApk);
        WeakReference weakReference = new WeakReference(mLoadedApk);// 放入 自定义的LoadedApk --》 插件的
        mPackages.put(applicationInfo.packageName,weakReference);// 增加了我们自己的LoadedApk
    }

    private ApplicationInfo getApplicationInfoAction() throws Exception{
        Class<?> mPackageParserClazz = Class.forName("android.content.pm.PackageParser");
        Object mPackageParserObj = mPackageParserClazz.newInstance();

        Class<?> mPackageClazz = Class.forName("android.content.pm.PackageParser$Package");

        Class<?> mPackageUserStateClazz = Class.forName("android.content.pm.PackageUserState");
        Object mPackageUserStateObj = mPackageUserStateClazz.newInstance();
        Method mGenerateApplicationInfoMethod = mPackageParserClazz.getMethod("generateApplicationInfo", mPackageClazz, int.class, mPackageUserStateClazz);

//        public Package parsePackage(File packageFile, int flags)
        Method mParsePackageMethod = mPackageParserClazz.getMethod("parsePackage", File.class, int.class);
        Object mPackageObj = mParsePackageMethod.invoke(mPackageParserObj, pluginFile, PackageManager.GET_ACTIVITIES);

//        public static ApplicationInfo generateApplicationInfo(Package p, int flags,PackageUserState state)
        ApplicationInfo applicationInfo = (ApplicationInfo) mGenerateApplicationInfoMethod.invoke(null, mPackageObj, 0, mPackageUserStateObj);

        // 获得的 ApplicationInfo 就是插件的 ApplicationInfo
        // 我们这里获取的 ApplicationInfo
        // applicationInfo.publicSourceDir = 插件的路径；
        // applicationInfo.sourceDir = 插件的路径；
        String pluginPath = pluginFile.getAbsolutePath();
        applicationInfo.publicSourceDir = pluginPath;
        applicationInfo.sourceDir = pluginPath;

        return applicationInfo;
    }

    private void hookGetPackageInfo() throws Exception{

        Class<?> mActivityThreadClazz = Class.forName("android.app.ActivityThread");
        Field sPackageManagerField = mActivityThreadClazz.getDeclaredField("sPackageManager");
        sPackageManagerField.setAccessible(true);
        final Object mIPackageManagerObj = sPackageManagerField.get(null);

//        Method mGetPackageManagerMethod = mActivityThreadClazz.getMethod("getPackageManager");
//        final Object mIPackageManagerObj = mGetPackageManagerMethod.invoke(null);

        Class<?> mIPackageManagerCLazz = Class.forName("android.content.pm.IPackageManager");
        Object mIPackageManagerProxy = Proxy.newProxyInstance(getClassLoader(), new Class[]{mIPackageManagerCLazz}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if(method.getName().equals("getPackageInfo")){
                    return new PackageInfo();
                }
                return method.invoke(mIPackageManagerObj,args);
            }
        });
        // 替换  狸猫换太子   换成我们自己的 动态代理
        sPackageManagerField.set(null,mIPackageManagerProxy);
    }
}
