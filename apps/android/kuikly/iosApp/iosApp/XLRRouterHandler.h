//
//  页面路由适配器（Kuikly 要求宿主必须实现）
//  安卓那边对应 androidApp/KRRouterAdapter.kt。
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <OpenKuiklyIOSRender/KRRouterModule.h>

NS_ASSUME_NONNULL_BEGIN

@interface XLRRouterHandler : NSObject <KRRouterProtocol>

@end

NS_ASSUME_NONNULL_END
