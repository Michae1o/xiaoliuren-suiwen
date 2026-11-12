#import "XLRLogHandler.h"

@implementation XLRLogHandler

+ (void)load {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        [KRLogModule registerLogHandler:[self new]];
    });
}

/// 异步打印：性能优先，不卡渲染线程（日志条数和平台侧顺序不重要时这样最省）
- (BOOL)asyncLogEnable {
    return YES;
}

- (void)logInfo:(NSString *)message {
    NSLog(@"[Kuikly] %@", message);
}

- (void)logDebug:(NSString *)message {
#if DEBUG
    NSLog(@"[Kuikly·dbg] %@", message);
#endif
}

- (void)logError:(NSString *)message {
    // 错误用不同的前缀，方便在 Xcode / Console 里筛
    NSLog(@"[Kuikly·ERROR] %@", message);
}

@end
