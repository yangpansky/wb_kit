package io.github.v7lin.weibo_kit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sina.weibo.sdk.WbSdk;
import com.sina.weibo.sdk.api.ImageObject;
import com.sina.weibo.sdk.api.TextObject;
import com.sina.weibo.sdk.api.WebpageObject;
import com.sina.weibo.sdk.api.WeiboMultiMessage;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WbAuthListener;
import com.sina.weibo.sdk.auth.WbConnectErrorMessage;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.share.WbShareCallback;
import com.sina.weibo.sdk.share.WbShareHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class WeiboKit implements MethodChannel.MethodCallHandler, PluginRegistry.ActivityResultListener, PluginRegistry.NewIntentListener, WbShareCallback {

    private static class WeiboErrorCode {
        public static final int SUCCESS = 0;//成功
        public static final int USERCANCEL = -1;//用户取消发送
        public static final int SENT_FAIL = -2;//发送失败
        public static final int AUTH_DENY = -3;//授权失败
        public static final int USERCANCEL_INSTALL = -4;//用户取消安装微博客户端
        public static final int PAY_FAIL = -5;//支付失败
        public static final int SHARE_IN_SDK_FAILED = -8;//分享失败 详情见response UserInfo
        public static final int UNSUPPORT = -99;//不支持的请求
        public static final int UNKNOWN = -100;
    }

    private static final String METHOD_REGISTERAPP = "registerApp";
    private static final String METHOD_ISINSTALLED = "isInstalled";
    private static final String METHOD_AUTH = "auth";
    private static final String METHOD_SHARETEXT = "shareText";
    private static final String METHOD_SHAREIMAGE = "shareImage";
    private static final String METHOD_SHAREWEBPAGE = "shareWebpage";

    private static final String METHOD_ONAUTHRESP = "onAuthResp";
    private static final String METHOD_ONSHAREMSGRESP = "onShareMsgResp";

    private static final String ARGUMENT_KEY_APPKEY = "appKey";
    private static final String ARGUMENT_KEY_SCOPE = "scope";
    private static final String ARGUMENT_KEY_REDIRECTURL = "redirectUrl";
    private static final String ARGUMENT_KEY_TEXT = "text";
    private static final String ARGUMENT_KEY_TITLE = "title";
    private static final String ARGUMENT_KEY_DESCRIPTION = "description";
    private static final String ARGUMENT_KEY_THUMBDATA = "thumbData";
    private static final String ARGUMENT_KEY_IMAGEDATA = "imageData";
    private static final String ARGUMENT_KEY_IMAGEURI = "imageUri";
    private static final String ARGUMENT_KEY_WEBPAGEURL = "webpageUrl";

    private static final String ARGUMENT_KEY_RESULT_ERRORCODE = "errorCode";
    private static final String ARGUMENT_KEY_RESULT_ERRORMESSAGE = "errorMessage";
    private static final String ARGUMENT_KEY_RESULT_USERID = "userId";
    private static final String ARGUMENT_KEY_RESULT_ACCESSTOKEN = "accessToken";
    private static final String ARGUMENT_KEY_RESULT_REFRESHTOKEN = "refreshToken";
    private static final String ARGUMENT_KEY_RESULT_EXPIRESIN = "expiresIn";

    //

    private Context applicationContext;
    private Activity activity;

    private MethodChannel channel;

//    private IWBAPI iwbapi;

    private SsoHandler mSsoHandler;
    private WbShareHandler mShareHandler;

    public WeiboKit() {
        super();
    }

    public WeiboKit(Context applicationContext, Activity activity) {
        this.applicationContext = applicationContext;
        this.activity = activity;
    }

    //

    public void setApplicationContext(@Nullable Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void setActivity(@Nullable Activity activity) {
        this.activity = activity;
    }

    public void startListening(@NonNull BinaryMessenger messenger) {
        channel = new MethodChannel(messenger, "v7lin.github.io/weibo_kit");
        channel.setMethodCallHandler(this);
    }

    public void stopListening() {
        channel.setMethodCallHandler(null);
        channel = null;
    }

    // --- MethodCallHandler

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (METHOD_REGISTERAPP.equals(call.method)) {
            String appKey = call.argument(ARGUMENT_KEY_APPKEY);
            String scope = call.argument(ARGUMENT_KEY_SCOPE);
            String redirectUrl = call.argument(ARGUMENT_KEY_REDIRECTURL);

//            iwbapi = WBAPIFactory.createWBAPI(activity);
//            iwbapi.registerApp(applicationContext, new AuthInfo(applicationContext, appKey, redirectUrl, scope));
            WbSdk.install(applicationContext, new AuthInfo(applicationContext, appKey, redirectUrl, scope));
            mSsoHandler = new SsoHandler(activity);
            result.success(null);
        } else if (METHOD_ISINSTALLED.equals(call.method)) {
//            result.success(iwbapi.isWBAppInstalled());
            result.success(WbSdk.isWbInstall(applicationContext));
        } else if (METHOD_AUTH.equals(call.method)) {
            handleAuthCall(call, result);
        } else if (METHOD_SHARETEXT.equals(call.method)) {
            handleShareTextCall(call, result);
        } else if (METHOD_SHAREIMAGE.equals(call.method) ||
                METHOD_SHAREWEBPAGE.equals(call.method)) {
            handleShareMediaCall(call, result);
        } else {
            result.notImplemented();
        }
    }

    private void handleAuthCall(MethodCall call, MethodChannel.Result result) {
        if (mSsoHandler != null){
            mSsoHandler.authorize(new WbAuthListener() {
                @Override
                public void onSuccess(Oauth2AccessToken oauth2AccessToken) {
                    Map<String, Object> map = new HashMap<>();
                    if (oauth2AccessToken.isSessionValid()) {
                        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SUCCESS);
                        map.put(ARGUMENT_KEY_RESULT_USERID, oauth2AccessToken.getUid());
                        map.put(ARGUMENT_KEY_RESULT_ACCESSTOKEN, oauth2AccessToken.getToken());
                        map.put(ARGUMENT_KEY_RESULT_REFRESHTOKEN, oauth2AccessToken.getRefreshToken());
                        long expiresIn = (long) Math.ceil((oauth2AccessToken.getExpiresTime() - System.currentTimeMillis()) / 1000.0);
                        map.put(ARGUMENT_KEY_RESULT_EXPIRESIN, expiresIn);// 向上取整
                    } else {
                        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.UNKNOWN);
                    }
                    if (channel != null) {
                        channel.invokeMethod(METHOD_ONAUTHRESP, map);
                    }
                }

                @Override
                public void cancel() {
                    Map<String, Object> map = new HashMap<>();
                    map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.USERCANCEL);
                    if (channel != null) {
                        channel.invokeMethod(METHOD_ONAUTHRESP, map);
                    }
                }

                @Override
                public void onFailure(WbConnectErrorMessage wbConnectErrorMessage) {
                    Map<String, Object> map = new HashMap<>();
                    map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.UNKNOWN);
                    channel.invokeMethod(METHOD_ONAUTHRESP, map);
                }
            });
        }

//        if (iwbapi != null) {
//            iwbapi.authorize(new WbAuthListener() {
//                @Override
//                public void onComplete(Oauth2AccessToken token) {
//                    Map<String, Object> map = new HashMap<>();
//                    if (token.isSessionValid()) {
//                        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SUCCESS);
//                        map.put(ARGUMENT_KEY_RESULT_USERID, token.getUid());
//                        map.put(ARGUMENT_KEY_RESULT_ACCESSTOKEN, token.getAccessToken());
//                        map.put(ARGUMENT_KEY_RESULT_REFRESHTOKEN, token.getRefreshToken());
//                        long expiresIn = (long) Math.ceil((token.getExpiresTime() - System.currentTimeMillis()) / 1000.0);
//                        map.put(ARGUMENT_KEY_RESULT_EXPIRESIN, expiresIn);// 向上取整
//                    } else {
//                        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.UNKNOWN);
//                    }
//                    if (channel != null) {
//                        channel.invokeMethod(METHOD_ONAUTHRESP, map);
//                    }
//                }
//
//                @Override
//                public void onError(UiError uiError) {
//                    Map<String, Object> map = new HashMap<>();
//                    map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.UNKNOWN);
//                    channel.invokeMethod(METHOD_ONAUTHRESP, map);
//                }
//
//                @Override
//                public void onCancel() {
//                    Map<String, Object> map = new HashMap<>();
//                    map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.USERCANCEL);
//                    if (channel != null) {
//                        channel.invokeMethod(METHOD_ONAUTHRESP, map);
//                    }
//                }
//            });
//        }
        result.success(null);
    }

    private void handleShareTextCall(MethodCall call, MethodChannel.Result result) {
        if (mShareHandler == null){
            mShareHandler = new WbShareHandler(activity);
            mShareHandler.registerApp();
            mShareHandler.setProgressColor(0xffc4c4c4);
        }


        WeiboMultiMessage message = new WeiboMultiMessage();

        TextObject object = new TextObject();
        object.text = call.argument(ARGUMENT_KEY_TEXT);// 1024

        message.textObject = object;

//        if (iwbapi != null) {
//            iwbapi.shareMessage(message, false);
//        }
        mShareHandler.shareMessage(message, false);
        result.success(null);
    }

    private void handleShareMediaCall(MethodCall call, MethodChannel.Result result) {
        if (mShareHandler == null){
            mShareHandler = new WbShareHandler(activity);
            mShareHandler.registerApp();
            mShareHandler.setProgressColor(0xffc4c4c4);
        }

        WeiboMultiMessage message = new WeiboMultiMessage();

        if (METHOD_SHAREIMAGE.equals(call.method)) {
            if (call.hasArgument(ARGUMENT_KEY_TEXT)) {
                TextObject object = new TextObject();
                object.text = call.argument(ARGUMENT_KEY_TEXT);// 1024

                message.textObject = object;
            }

            ImageObject object = new ImageObject();
            if (call.hasArgument(ARGUMENT_KEY_IMAGEDATA)) {
                object.imageData = call.argument(ARGUMENT_KEY_IMAGEDATA);// 2 * 1024 * 1024
            } else if (call.hasArgument(ARGUMENT_KEY_IMAGEURI)) {
                String imageUri = call.argument(ARGUMENT_KEY_IMAGEURI);
                object.imagePath = Uri.parse(imageUri).getPath();// 512 - 10 * 1024 * 1024
            }

            message.mediaObject = object;
        } else if (METHOD_SHAREWEBPAGE.equals(call.method)) {
            WebpageObject object = new WebpageObject();
            object.identify = UUID.randomUUID().toString();
            object.title = call.argument(ARGUMENT_KEY_TITLE);// 512
            object.description = call.argument(ARGUMENT_KEY_DESCRIPTION);// 1024
            object.thumbData = call.argument(ARGUMENT_KEY_THUMBDATA);// 32 * 1024
            object.defaultText = call.argument(ARGUMENT_KEY_DESCRIPTION);
            object.actionUrl = call.argument(ARGUMENT_KEY_WEBPAGEURL);// 512

            message.mediaObject = object;
        }

//        if (iwbapi != null) {
//            iwbapi.shareMessage(message, false);
//        }
        mShareHandler.shareMessage(message, false);
        result.success(null);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mSsoHandler != null) {
            mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
        }
        return true;
    }

//    @Override
//    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
//        switch (requestCode) {
//            case 32973:
//                if (iwbapi != null) {
//                    iwbapi.authorizeCallback(requestCode, resultCode, data);
//                }
//                return true;
//            case 10001:
//                if (iwbapi != null) {
//                    iwbapi.doResultIntent(data, new WbShareCallback() {
//                        @Override
//                        public void onComplete() {
//                            Map<String, Object> map = new HashMap<>();
//                            map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SUCCESS);
//                            if (channel != null) {
//                                channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
//                            }
//                        }
//
//                        @Override
//                        public void onError(UiError uiError) {
//                            Map<String, Object> map = new HashMap<>();
//                            map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SHARE_IN_SDK_FAILED);
//                            if (channel != null) {
//                                channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
//                            }
//                        }
//
//                        @Override
//                        public void onCancel() {
//                            Map<String, Object> map = new HashMap<>();
//                            map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.USERCANCEL);
//                            if (channel != null) {
//                                channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
//                            }
//                        }
//                    });
//                }
//                return true;
//        }
//        return false;
//    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mShareHandler == null) {
            mShareHandler = new WbShareHandler(activity);
            mShareHandler.registerApp();
            mShareHandler.setProgressColor(0xff33b5e5);
        }
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object object = bundle.get("_weibo_resp_errcode");
            if (object != null) {
                mShareHandler.doResultIntent(intent, this);
            }
        }
    }

    @Override
    public void onWbShareSuccess() {
        Map<String, Object> map = new HashMap<>();
        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SUCCESS);
        if (channel != null) {
            channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
        }
    }

    @Override
    public void onWbShareCancel() {
        Map<String, Object> map = new HashMap<>();
        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.USERCANCEL);
        if (channel != null) {
            channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
        }
    }

    @Override
    public void onWbShareFail() {
        Map<String, Object> map = new HashMap<>();
        map.put(ARGUMENT_KEY_RESULT_ERRORCODE, WeiboErrorCode.SHARE_IN_SDK_FAILED);
        if (channel != null) {
            channel.invokeMethod(METHOD_ONSHAREMSGRESP, map);
        }
    }
}
