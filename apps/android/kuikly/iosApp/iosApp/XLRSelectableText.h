#import <UIKit/UIKit.h>
#import "KuiklyRenderViewExportProtocol.h"

NS_ASSUME_NONNULL_BEGIN

/// Kuikly host for a read-only UITextView. UIKit owns selection, handles and menu.
@interface XLRSelectableText : UIView <KuiklyRenderViewExportProtocol>
@end

NS_ASSUME_NONNULL_END
