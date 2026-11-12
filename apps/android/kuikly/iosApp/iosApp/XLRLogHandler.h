//
//  日志适配器（Kuikly 推荐宿主实现）
//  没有它，Kotlin 侧的异常和框架内部报错在 iOS 上完全看不见 —— 排线上问题全靠这个。
//

#import <Foundation/Foundation.h>
#import <OpenKuiklyIOSRender/KRLogModule.h>

NS_ASSUME_NONNULL_BEGIN

@interface XLRLogHandler : NSObject <KuiklyLogProtocol>

@end

NS_ASSUME_NONNULL_END
