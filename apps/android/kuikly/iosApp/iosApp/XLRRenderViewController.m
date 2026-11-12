#import "XLRRenderViewController.h"
#import <OpenKuiklyIOSRender/KuiklyRenderViewControllerBaseDelegator.h>
#import <OpenKuiklyIOSRender/KuiklyRenderContextProtocol.h>

@interface XLRRenderViewController () <KuiklyRenderViewControllerBaseDelegatorDelegate>

@property (nonatomic, strong) KuiklyRenderViewControllerBaseDelegator *delegator;

@end

@implementation XLRRenderViewController {
    NSDictionary *_pageData;
}

- (instancetype)initWithPageName:(NSString *)pageName pageData:(NSDictionary *)pageData {
    if (self = [super init]) {
        /* 和安卓 onAttach(container, "", PAGE_NAME, mapOf("appId" to 1)) 保持同样的参数，
           免得共享层在两端读到不一样的东西。 */
        NSMutableDictionary *merged = [(pageData ?: @{}) mutableCopy];
        if (merged[@"appId"] == nil) {
            merged[@"appId"] = @1;
        }
        NSString *lang = [[NSLocale preferredLanguages] firstObject];
        merged[@"sysLang"] = lang ?: @"zh-Hans";
        _pageData = merged;

        /* 直接指定 framework 名（比只走 fetchContextCode 回调更明确）。
           "shared" 必须和 shared/build.gradle.kts 的 baseName 一致：
           SDK 会按 <首字母大写>KuiklyCoreEntry 去找 Kotlin/Native 的入口类，即 SharedKuiklyCoreEntry。 */
        _delegator = [[KuiklyRenderViewControllerBaseDelegator alloc] initWithPageName:pageName
                                                                              pageData:merged
                                                                         frameworkName:@"shared"];
        _delegator.delegate = self;
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];

    /* 冷启动第一帧还没画 Kuikly 内容时露的就是这个底色。
       用骨白，别用纯白 —— 纯白在深色皮肤上会闪一下（安卓那边同样踩过）。 */
    self.view.backgroundColor = [UIColor colorWithRed:0.949 green:0.937 blue:0.910 alpha:1.0];

    [_delegator viewDidLoadWithView:self.view];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [self.navigationController setNavigationBarHidden:YES animated:NO];
    [_delegator viewWillAppear];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [_delegator viewDidAppear];
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [_delegator viewWillDisappear];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [_delegator viewDidDisappear];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [_delegator viewDidLayoutSubviews];
}

#pragma mark - KuiklyRenderViewControllerBaseDelegatorDelegate

- (UIView *)createLoadingView {
    UIView *loadingView = [[UIView alloc] init];
    loadingView.backgroundColor = self.view.backgroundColor;
    return loadingView;
}

- (UIView *)createErrorView {
    /* 加载失败时把原因显示出来 —— 没有 Mac 的时候，这是唯一能看见
       「Kotlin 侧到底报了什么」的窗口。 */
    UIView *errorView = [[UIView alloc] init];
    errorView.backgroundColor = [UIColor colorWithRed:0.949 green:0.937 blue:0.910 alpha:1.0];

    UILabel *label = [[UILabel alloc] init];
    label.translatesAutoresizingMaskIntoConstraints = NO;
    label.numberOfLines = 0;
    label.font = [UIFont monospacedSystemFontOfSize:12 weight:UIFontWeightRegular];
    label.textColor = [UIColor colorWithRed:0.45 green:0.16 blue:0.14 alpha:1.0];
    label.text = @"卦台页面加载失败，详情见 Xcode / 设备日志";
    [errorView addSubview:label];

    [NSLayoutConstraint activateConstraints:@[
        [label.leadingAnchor constraintEqualToAnchor:errorView.leadingAnchor constant:20],
        [label.trailingAnchor constraintEqualToAnchor:errorView.trailingAnchor constant:-20],
        [label.centerYAnchor constraintEqualToAnchor:errorView.centerYAnchor],
    ]];
    return errorView;
}

/// 告诉渲染器：业务 Kotlin 代码编进了哪个 framework。
/// ⚠ 名字必须和 shared/build.gradle.kts 里的 baseName 一致（"shared"）。
- (void)fetchContextCodeWithPageName:(NSString *)pageName resultCallback:(KuiklyContextCodeCallback)callback {
    if (callback) {
        callback(@"shared", nil);
    }
}

- (void)onUnhandledException:(NSString *)exReason stack:(NSString *)callstackStr mode:(KuiklyContextMode)mode {
    NSLog(@"[XLR] Kuikly 未捕获异常: %@\n%@", exReason, callstackStr);
}

- (void)onPageLoadComplete:(BOOL)isSucceed error:(NSError *)error mode:(KuiklyContextMode)mode {
    if (!isSucceed) {
        NSLog(@"[XLR] 页面加载失败: %@", error);
    }
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end
