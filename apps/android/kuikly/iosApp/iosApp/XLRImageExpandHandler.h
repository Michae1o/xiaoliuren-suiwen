//
//  图片加载适配器（Kuikly 要求宿主必须实现）
//  安卓那边对应 androidApp/KRImageAdapter.kt。
//
//  本工程图片分两类：
//    1. bundle 里的本地资源（皮肤图、星图、图标）——按路径从 mainBundle 取
//    2. 网络图（理论上没有，但 LLM 那边若回图片链接要走这条）
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <OpenKuiklyIOSRender/KuiklyRenderBridge.h>

NS_ASSUME_NONNULL_BEGIN

@interface XLRImageExpandHandler : NSObject <KuiklyRenderComponentExpandProtocol>

@end

NS_ASSUME_NONNULL_END
