#import "XLRRouterHandler.h"
#import "XLRRenderViewController.h"

@implementation XLRRouterHandler

+ (void)load {
    [KRRouterModule registerRouterHandler:[self new]];
}

- (void)openPageWithName:(NSString *)pageName
                pageData:(NSDictionary *)pageData
              controller:(UIViewController *)controller {
    /* 卦台是单页应用：共享层内部的「页」是 Compose 组合里的切换，不走原生路由。
       真跳到别的 Kuikly 页面时才建一个新容器。 */
    UIViewController *vc = [[XLRRenderViewController alloc] initWithPageName:pageName pageData:pageData];
    if (controller.navigationController) {
        [controller.navigationController pushViewController:vc animated:YES];
    } else {
        vc.modalPresentationStyle = UIModalPresentationFullScreen;
        [controller presentViewController:vc animated:YES completion:nil];
    }
}

- (void)closePage:(UIViewController *)controller {
    if (controller.navigationController.viewControllers.count <= 1) {
        // 根 VC 退不出去，说明是模态弹出来的
        [controller.navigationController dismissViewControllerAnimated:YES completion:nil];
    } else {
        [controller.navigationController popViewControllerAnimated:YES];
    }
}

@end
