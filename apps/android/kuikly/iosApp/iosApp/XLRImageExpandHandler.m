#import "XLRImageExpandHandler.h"

/*
 * 图片加载适配器（Kuikly 要求宿主必须实现）
 * 安卓对应 androidApp/adapter/KRImageAdapter.kt —— src 的三种形态必须一一对齐：
 *
 *   assets://common/fog/a.png   安卓从 APK 的 assets 读；iOS 从 main bundle 同路径读
 *   data:image/png;base64,...   内嵌图（开屏/占位这类）——直接解码
 *   file:///var/...             本地文件
 *   http(s)://...               网络图（本工程基本用不到，但留一条）
 *
 * ⚠ 顺序很重要：assets:// 判断必须在 file/网络之前，否则会被当成相对路径找错地方。
 */

@implementation XLRImageExpandHandler

+ (void)load {
    [KuiklyRenderBridge registerComponentExpandHandler:[self new]];
}

/// 按 bundle 内的相对路径找图（assets:// 剥掉前缀后的那部分）
/// ⚠ project.yml 把 assets 目录作为 folder reference 打进包，所以实际位置是
///   <App>.app/Assets/common/...（实测 ipa 里就是这层结构），而不是 bundle 根。
///   两个位置都试，省得以后改打包方式又要回来动这里。
static UIImage *XLRImageFromBundlePath(NSString *path) {
    if (path.length == 0) {
        return nil;
    }
    NSString *resource = NSBundle.mainBundle.resourcePath;
    NSArray<NSString *> *candidates = @[
        [@"Assets" stringByAppendingPathComponent:path],
        path,
    ];
    for (NSString *rel in candidates) {
        NSString *full = [resource stringByAppendingPathComponent:rel];
        UIImage *img = [UIImage imageWithContentsOfFile:full];
        if (img) {
            return img;
        }
    }
    // 资源被压平到根目录的情况（Xcode 有时会把目录结构摊开）
    UIImage *img = [UIImage imageNamed:path];
    if (img) {
        return img;
    }
    img = [UIImage imageNamed:[path lastPathComponent]];
    if (img) {
        return img;
    }
    // 绝对路径
    return [UIImage imageWithContentsOfFile:path];
}

/// 解内嵌的 base64 图（data:image/xxx;base64,<payload>）
static UIImage *XLRImageFromDataURL(NSString *src) {
    NSRange comma = [src rangeOfString:@","];
    if (comma.location == NSNotFound) {
        return nil;
    }
    NSString *payload = [src substringFromIndex:comma.location + 1];
    NSData *data = [[NSData alloc] initWithBase64EncodedString:payload
                                                       options:NSDataBase64DecodingIgnoreUnknownCharacters];
    if (data.length == 0) {
        return nil;
    }
    return [UIImage imageWithData:data];
}

- (BOOL)hr_setImageWithUrl:(NSString *)loadURL
               imageParams:(NSDictionary *)imageParams
                  complete:(ImageCompletionBlock)completeBlock {
    // src 为空 = 取消图片
    if (loadURL.length == 0) {
        if (completeBlock) {
            completeBlock(nil, nil, nil);
        }
        return YES;
    }

    NSURL *srcUrl = [NSURL URLWithString:loadURL];

    // ---- 1. assets://（和安卓同一套路径规则）
    if ([loadURL hasPrefix:@"assets://"]) {
        NSString *path = [loadURL substringFromIndex:@"assets://".length];
        UIImage *img = XLRImageFromBundlePath(path);
        if (completeBlock) {
            completeBlock(img, img ? nil : [NSError errorWithDomain:@"XLRImage"
                                                               code:404
                                                           userInfo:@{NSLocalizedDescriptionKey:
                                                                          [NSString stringWithFormat:@"bundle 里找不到 assets://%@", path]}],
                          srcUrl);
        }
        return YES;
    }

    // ---- 2. base64 内嵌图
    if ([loadURL hasPrefix:@"data:"]) {
        UIImage *img = XLRImageFromDataURL(loadURL);
        if (completeBlock) {
            completeBlock(img, img ? nil : [NSError errorWithDomain:@"XLRImage"
                                                               code:400
                                                           userInfo:@{NSLocalizedDescriptionKey: @"base64 图片解码失败"}],
                          srcUrl);
        }
        return YES;
    }

    // ---- 3. 网络图
    BOOL isRemote = ([srcUrl.scheme isEqualToString:@"http"] || [srcUrl.scheme isEqualToString:@"https"]);
    if (isRemote) {
        [[NSURLSession.sharedSession dataTaskWithURL:srcUrl
                                   completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
            UIImage *image = (data.length > 0) ? [UIImage imageWithData:data] : nil;
            // ⚠ 必须回主线程：SDK 拿到图就直接 setImage
            dispatch_async(dispatch_get_main_queue(), ^{
                if (completeBlock) {
                    completeBlock(image, error, srcUrl);
                }
            });
        }] resume];
        return YES;
    }

    // ---- 4. file:// 与裸路径
    UIImage *img = nil;
    if ([srcUrl.scheme isEqualToString:@"file"]) {
        img = [UIImage imageWithContentsOfFile:srcUrl.path];
    }
    if (!img) {
        img = XLRImageFromBundlePath(loadURL);
    }
    if (completeBlock) {
        completeBlock(img, img ? nil : [NSError errorWithDomain:@"XLRImage"
                                                           code:404
                                                       userInfo:@{NSLocalizedDescriptionKey:
                                                                      [NSString stringWithFormat:@"找不到图片: %@", loadURL]}],
                      srcUrl ?: [NSURL URLWithString:loadURL]);
    }
    return YES;
}

@end
