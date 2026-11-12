//
//  Kuikly 页面容器（iOS）
//  对应安卓的 MainActivity：把渲染器挂到根视图、转发生命周期、
//  告诉渲染器业务代码编进了哪个 framework。
//

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface XLRRenderViewController : UIViewController

/// @param pageName Kuikly 页面名（Kotlin 侧 @Page("xxx") 的 xxx）——本工程是 "Yi"
/// @param pageData 页面参数，Kotlin 侧通过 pageData.params 取
- (instancetype)initWithPageName:(NSString *)pageName pageData:(NSDictionary *)pageData;

@end

NS_ASSUME_NONNULL_END
