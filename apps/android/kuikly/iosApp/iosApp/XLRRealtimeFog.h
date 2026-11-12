//
//  XLRRealtimeFog.h
//  Kuikly 侧 XLRRealtimeFog 组件在 iOS 的原生实现。
//
//  ⚠ 类名必须是 XLRRealtimeFog：Kuikly 按 viewName() 的返回值找同名类，
//     这里对不上就永远创建不出来（安卓那边是在 MainActivity 里 renderViewExport 注册的，
//     iOS 没有注册这一步，纯靠类名约定）。
//

#import <UIKit/UIKit.h>
#import "KuiklyRenderViewExportProtocol.h"

NS_ASSUME_NONNULL_BEGIN

@interface XLRRealtimeFog : UIView <KuiklyRenderViewExportProtocol>

@end

NS_ASSUME_NONNULL_END
