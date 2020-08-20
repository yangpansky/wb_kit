#import "WeiboKitPlugin.h"
#import <Weibo_SDK/WeiboSDK.h>

@interface WeiboKitPlugin () <WeiboSDKDelegate>
// yangpan modify
@property (nonatomic,strong) FlutterMethodChannel *channel;
@property (nonatomic,copy) NSString *redirectURI;
@property (nonatomic,copy) NSString *accessToken;
@end

@implementation WeiboKitPlugin {
// yangpan modify
    // FlutterMethodChannel * _channel;
}

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
  FlutterMethodChannel *channel =
      [FlutterMethodChannel methodChannelWithName:@"v7lin.github.io/weibo_kit"
                                  binaryMessenger:[registrar messenger]];

// yangpan modify
//   WeiboKitPlugin *instance = [[WeiboKitPlugin alloc] initWithChannel:channel];
  WeiboKitPlugin *instance = [self.class sharedInstance];
  instance.channel = channel;

  [registrar addApplicationDelegate:instance];
  [registrar addMethodCallDelegate:instance channel:channel];
}

// yangpan modify
+ (instancetype)sharedInstance{
    static id _instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        _instance = [self.class new];
    });
    
    return _instance;
}

static NSString * const METHOD_REGISTERAPP = @"registerApp";
static NSString * const METHOD_ISINSTALLED = @"isInstalled";
static NSString * const METHOD_CANSHAREINWBAPP = @"canShareInWeiboApp";

static NSString * const METHOD_AUTH = @"auth";
static NSString * const METHOD_AUTHOUT = @"authOut";
static NSString * const METHOD_SHARETEXT = @"shareText";
static NSString * const METHOD_SHAREIMAGE = @"shareImage";
static NSString * const METHOD_SHAREWEBPAGE = @"shareWebpage";

static NSString * const METHOD_ONAUTHRESP = @"onAuthResp";
static NSString * const METHOD_ONSHAREMSGRESP = @"onShareMsgResp";

static NSString * const ARGUMENT_KEY_APPKEY = @"appKey";
static NSString * const ARGUMENT_KEY_SCOPE = @"scope";
static NSString * const ARGUMENT_KEY_REDIRECTURL = @"redirectUrl";
static NSString * const ARGUMENT_KEY_TEXT = @"text";
static NSString * const ARGUMENT_KEY_TITLE = @"title";
static NSString * const ARGUMENT_KEY_DESCRIPTION = @"description";
static NSString * const ARGUMENT_KEY_THUMBDATA = @"thumbData";
static NSString * const ARGUMENT_KEY_IMAGEDATA = @"imageData";
static NSString * const ARGUMENT_KEY_IMAGEURI = @"imageUri";
static NSString * const ARGUMENT_KEY_WEBPAGEURL = @"webpageUrl";

static NSString * const ARGUMENT_KEY_RESULT_ERRORCODE = @"errorCode";
static NSString * const ARGUMENT_KEY_RESULT_ERRORMESSAGE = @"errorMessage";
static NSString * const ARGUMENT_KEY_RESULT_USERID = @"userId";
static NSString * const ARGUMENT_KEY_RESULT_ACCESSTOKEN = @"accessToken";
static NSString * const ARGUMENT_KEY_RESULT_REFRESHTOKEN = @"refreshToken";
static NSString * const ARGUMENT_KEY_RESULT_EXPIRESIN = @"expiresIn";

// yangpan modify
// -(instancetype)initWithChannel:(FlutterMethodChannel *)channel {
//     self = [super init];
//     if (self) {
//         _channel = channel;
//     }
//     return self;
// }

- (void)handleMethodCall:(FlutterMethodCall *)call
                  result:(FlutterResult)result {
  if ([METHOD_REGISTERAPP isEqualToString:call.method]) {
      NSString * appKey = call.arguments[ARGUMENT_KEY_APPKEY];
      self.redirectURI = call.arguments[ARGUMENT_KEY_REDIRECTURL];
      [WeiboSDK registerApp:appKey];
      result(nil);
  } else if ([METHOD_ISINSTALLED isEqualToString:call.method]) {
      result([NSNumber numberWithBool:[WeiboSDK isWeiboAppInstalled]]);
  } else if ([METHOD_CANSHAREINWBAPP isEqualToString:call.method]) {
      result([NSNumber numberWithBool:[WeiboSDK isCanShareInWeiboAPP]]);
  }else if ([METHOD_AUTH isEqualToString:call.method]) {
      [self handleAuthCall:call result:result];
  }else if ([METHOD_AUTHOUT isEqualToString:call.method]) {
      [self handleAuthOutCall:call result:result];
  } else if ([METHOD_SHARETEXT isEqualToString:call.method]) {
      [self handleShareTextCall:call result:result];
  } else if ([METHOD_SHAREWEBPAGE isEqualToString:call.method]) {
      [self handleShareMediaCall:call result:result];
  } else if ([METHOD_SHAREIMAGE isEqualToString:call.method]) {
       [self handleShareImageCall:call result:result];
   } else {
      result(FlutterMethodNotImplemented);
  }
}

- (void)request:(WBHttpRequest *)request didFinishLoadingWithResult:(NSString *)result
{
    if([request.tag isEqualToString:@"WeiboKitPluginAuthOut"]) {
        self.accessToken = nil;
    }
}

-(void)handleAuthOutCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    [WeiboSDK logOutWithToken:self.accessToken delegate:self withTag:@"WeiboKitPluginAuthOut"];
    result(nil);
}

-(void)handleAuthCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    WBAuthorizeRequest * request = [WBAuthorizeRequest request];
    request.scope = call.arguments[ARGUMENT_KEY_SCOPE];
    request.redirectURI = call.arguments[ARGUMENT_KEY_REDIRECTURL];
    if (request.redirectURI != nil && request.redirectURI.length > 0) {
        self.redirectURI = request.redirectURI;
    }
    request.shouldShowWebViewForAuthIfCannotSSO = YES;
    request.shouldOpenWeiboAppInstallPageIfNotInstalled = NO;
    [WeiboSDK sendRequest:request];
    result(nil);
}

-(void)handleShareTextCall:(FlutterMethodCall*)call result:(FlutterResult)result {

    WBMessageObject * message = [WBMessageObject message];
    message.text = call.arguments[ARGUMENT_KEY_TEXT];
    
//    WBSendMessageToWeiboRequest * request = [WBSendMessageToWeiboRequest request];
//    request.message = message;

    WBAuthorizeRequest *authRequest = [WBAuthorizeRequest request];
    authRequest.redirectURI = self.redirectURI;
    authRequest.scope = @"all";
    // 通过这种初始化方式可以使用web分享
    // 另外，未安装微博客户端，只支持文字分享以及单张图片分享
    WBSendMessageToWeiboRequest *request = [WBSendMessageToWeiboRequest requestWithMessage:message authInfo:authRequest access_token:self.accessToken];
    [WeiboSDK sendRequest:request];
    result(nil);
}

-(void)handleShareImageCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    WBMessageObject * message = [WBMessageObject message];
    message.text = call.arguments[ARGUMENT_KEY_TEXT];
    WBImageObject * object = [WBImageObject object];
    FlutterStandardTypedData * imageData = call.arguments[ARGUMENT_KEY_IMAGEDATA];
    if (imageData != nil) {
        object.imageData = imageData.data;
    } else {
        NSString * imageUri = call.arguments[ARGUMENT_KEY_IMAGEURI];
        NSURL * imageUrl = [NSURL URLWithString:imageUri];
        object.imageData = [NSData dataWithContentsOfFile:imageUrl.path];
    }
    message.imageObject = object;
    
//    WBSendMessageToWeiboRequest * request = [WBSendMessageToWeiboRequest request];
//    request.message = message;

    WBAuthorizeRequest *authRequest = [WBAuthorizeRequest request];
    authRequest.redirectURI = self.redirectURI;
    authRequest.scope = @"all";
    // 通过这种初始化方式可以使用web分享
    // 另外，未安装微博客户端，只支持文字分享以及单张图片分享
    WBSendMessageToWeiboRequest *request = [WBSendMessageToWeiboRequest requestWithMessage:message authInfo:authRequest access_token:self.accessToken];
    [WeiboSDK sendRequest:request];
    result(nil);
}

-(void)handleShareMediaCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    WBMessageObject * message = [WBMessageObject message];
    WBWebpageObject * object = [WBWebpageObject object];
    object.objectID = [[NSUUID UUID].UUIDString stringByReplacingOccurrencesOfString:@"-" withString:@""];
    object.title = call.arguments[ARGUMENT_KEY_TITLE];
    object.description = call.arguments[ARGUMENT_KEY_DESCRIPTION];
    FlutterStandardTypedData * thumbData = call.arguments[ARGUMENT_KEY_THUMBDATA];
    if (thumbData != nil) {
        object.thumbnailData = thumbData.data;
    }
    object.webpageUrl = call.arguments[ARGUMENT_KEY_WEBPAGEURL];
    message.mediaObject = object;
    
//    WBSendMessageToWeiboRequest * request = [WBSendMessageToWeiboRequest request];
//    request.message = message;
    
    WBAuthorizeRequest *authRequest = [WBAuthorizeRequest request];
    authRequest.redirectURI = self.redirectURI;
    authRequest.scope = @"all";
    // 通过这种初始化方式可以使用web分享
    // 另外，未安装微博客户端，只支持文字分享以及单张图片分享
    WBSendMessageToWeiboRequest *request = [WBSendMessageToWeiboRequest requestWithMessage:message authInfo:authRequest access_token:self.accessToken];
    [WeiboSDK sendRequest:request];
    result(nil);
}

// yangpan modify
// # pragma mark - AppDelegate

// -(BOOL)application:(UIApplication *)application handleOpenURL:(NSURL *)url {
//     return [WeiboSDK handleOpenURL:url delegate:self];
// }

// -(BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation {
//     return [WeiboSDK handleOpenURL:url delegate:self];
// }

// - (BOOL)application:(UIApplication *)application openURL:(NSURL *)url options:(NSDictionary<UIApplicationOpenURLOptionsKey,id> *)options {
//     return [WeiboSDK handleOpenURL:url delegate:self];
// }

# pragma mark - WeiboSDKDelegate

-(void)didReceiveWeiboRequest:(WBBaseRequest *)request {
    
}

-(void)didReceiveWeiboResponse:(WBBaseResponse *)response {
    NSMutableDictionary * dictionary = [NSMutableDictionary dictionary];
    [dictionary setValue:[NSNumber numberWithInteger:response.statusCode] forKey:ARGUMENT_KEY_RESULT_ERRORCODE];
    if ([response isKindOfClass:[WBAuthorizeResponse class]]) {
        if (response.statusCode == WeiboSDKResponseStatusCodeSuccess) {
            WBAuthorizeResponse * authorizeResponse = (WBAuthorizeResponse *) response;
            NSString * userId = authorizeResponse.userID;
            NSString * accessToken = authorizeResponse.accessToken;
            NSString * refreshToken = authorizeResponse.refreshToken;
            long long expiresIn = ceil(authorizeResponse.expirationDate.timeIntervalSinceNow);// 向上取整
            [dictionary setValue:userId forKey:ARGUMENT_KEY_RESULT_USERID];
            [dictionary setValue:accessToken forKey:ARGUMENT_KEY_RESULT_ACCESSTOKEN];
            [dictionary setValue:refreshToken forKey:ARGUMENT_KEY_RESULT_REFRESHTOKEN];
            [dictionary setValue:[NSNumber numberWithLongLong:expiresIn] forKey:ARGUMENT_KEY_RESULT_EXPIRESIN];
            if (accessToken != nil && accessToken.length > 0){
                self.accessToken = accessToken;
            }
        }
        [_channel invokeMethod:METHOD_ONAUTHRESP arguments:dictionary];
    } else if ([response isKindOfClass:[WBSendMessageToWeiboResponse class]]) {
        if (response.statusCode == WeiboSDKResponseStatusCodeSuccess) {
            WBSendMessageToWeiboResponse * sendMessageToWeiboResponse = (WBSendMessageToWeiboResponse *) response;
            NSString *_token = [sendMessageToWeiboResponse.authResponse accessToken];
            if (_token != nil && _token.length > 0){
                self.accessToken = _token;
            }
        }
        [_channel invokeMethod:METHOD_ONSHAREMSGRESP arguments:dictionary];
    }
}

@end
