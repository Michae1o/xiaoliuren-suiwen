#import "XLRSelectableText.h"
#import "KRComponentDefine.h"
#import <math.h>
#import <QuartzCore/QuartzCore.h>
#import <QuartzCore/QuartzCore.h>

// A plain horizontal swipe belongs to the page. Do not reject UIKit's selection
// recognizers: only reject this scroll view's own pan before a range is selected.
@interface XLRSelectionTextView : UITextView
@end
@implementation XLRSelectionTextView
- (BOOL)gestureRecognizerShouldBegin:(UIGestureRecognizer *)gestureRecognizer {
    if (gestureRecognizer == self.panGestureRecognizer && self.selectedRange.length == 0) {
        CGPoint velocity = [self.panGestureRecognizer velocityInView:self];
        if (fabs(velocity.x) > fabs(velocity.y)) return NO;
    }
    return [super gestureRecognizerShouldBegin:gestureRecognizer];
}
@end

@interface XLRSelectableText () <UITextViewDelegate, UIGestureRecognizerDelegate>
@property (nonatomic, strong) XLRSelectionTextView *textView;
@property (nonatomic, strong) UIView *streamCursor;
@property (nonatomic, assign) BOOL busy;
@property (nonatomic, copy) NSString *identity;
@property (nonatomic, copy) KuiklyRenderCallback metricsCallback;
@property (nonatomic, copy) KuiklyRenderCallback selectionCallback;
@property (nonatomic, assign) BOOL active;
@property (nonatomic, assign) NSInteger clearEpoch;
@property (nonatomic, assign) BOOL changingText;
@property (nonatomic, assign) BOOL reportedSelection;
@property (nonatomic, assign) BOOL metricsPending;
@property (nonatomic, assign) CGFloat reportedHeight;
@property (nonatomic, assign) CGFloat reportedWidth;
//   ① 在自己窗口上挂一个"点别处"的 tap（不抢别的触摸）→ 清选区
//   ② 文本视图失焦（点别处 / 菜单收起 / 切页）→ 清选区
//   这两条都走 clearSelection → reportSelection(false) → Compose 侧 CardSelection.end 解锁。
@property (nonatomic, strong) UITapGestureRecognizer *outsideTap;
@property (nonatomic, assign) BOOL clearing;
@end

// Selecting another card must not leave the old page gesture lock active.
static __weak XLRSelectableText *XLRSelectedTextOwner;

@implementation XLRSelectableText
@synthesize hr_rootView;

- (instancetype)initWithFrame:(CGRect)frame {
    if ((self = [super initWithFrame:frame])) {
        _identity = @"";
        _active = YES;
        _reportedHeight = -1;
        _reportedWidth = -1;
        _textView = [[XLRSelectionTextView alloc] initWithFrame:CGRectZero];
        _textView.delegate = self;
        _textView.editable = NO;
        _textView.selectable = YES;
        _textView.scrollEnabled = NO;
        _textView.backgroundColor = UIColor.clearColor;
        _textView.textContainerInset = UIEdgeInsetsZero;
        _textView.textContainer.lineFragmentPadding = 0;
        _textView.textContainer.widthTracksTextView = YES;
        _textView.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever;
        _textView.alwaysBounceHorizontal = NO;
        _textView.showsHorizontalScrollIndicator = NO;
        _textView.directionalLockEnabled = YES;
        _textView.dataDetectorTypes = UIDataDetectorTypeNone;
        [self addSubview:_textView];
        _streamCursor = [[UIView alloc] initWithFrame:CGRectZero];
        _streamCursor.userInteractionEnabled = NO;
        _streamCursor.isAccessibilityElement = NO;
        _streamCursor.hidden = YES;
        [_textView addSubview:_streamCursor];
    }
    return self;
}

- (void)layoutSubviews {
    [super layoutSubviews];
    self.textView.frame = self.bounds;
    [self updateStreamCursor];
    [self scheduleMetrics];
}

- (void)didMoveToWindow {
    [super didMoveToWindow];
    if (!self.window) [self clearSelection];
}

- (void)hrv_setPropWithKey:(NSString *)propKey propValue:(id)propValue {
    if ([propKey isEqualToString:@"textSpec"]) {
        [self applySpec:propValue];
        return;
    }
    if ([propKey isEqualToString:@"onMetrics"]) {
        self.metricsCallback = propValue == NSNull.null ? nil : propValue;
        self.reportedHeight = -1;
        [self scheduleMetrics];
        return;
    }
    if ([propKey isEqualToString:@"onSelection"]) {
        self.selectionCallback = propValue == NSNull.null ? nil : propValue;
        if (self.selectionCallback) self.selectionCallback(@{@"active": @(self.reportedSelection)});
        return;
    }
    KUIKLY_SET_CSS_COMMON_PROP;
}

- (void)hrv_callWithMethod:(NSString *)method params:(NSString *)params callback:(KuiklyRenderCallback)callback {
    if ([method isEqualToString:@"clearSelection"]) {
        [self clearSelection];
        if (callback) callback(@{});
        return;
    }
    KUIKLY_CALL_CSS_METHOD;
}

- (void)hrv_removeFromSuperview {
    [self clearSelection];
    [self removeFromSuperview];
}

static UIColor *XLRTextColor(id value) {
    if (![value isKindOfClass:NSString.class]) return UIColor.labelColor;
    NSString *hex = [(NSString *)value stringByReplacingOccurrencesOfString:@"#" withString:@""];
    if (hex.length != 6 && hex.length != 8) return UIColor.labelColor;
    unsigned long long number = 0;
    if (![[NSScanner scannerWithString:hex] scanHexLongLong:&number]) return UIColor.labelColor;
    if (hex.length == 6) number = (number << 8) | 0xff;
    return [UIColor colorWithRed:((number >> 24) & 0xff) / 255.0
                           green:((number >> 16) & 0xff) / 255.0
                            blue:((number >> 8) & 0xff) / 255.0
                           alpha:(number & 0xff) / 255.0];
}

static UIFont *XLRTextFont(NSDictionary *run) {
    CGFloat size = MAX(1, [run[@"size"] doubleValue]);
    if (!run[@"size"]) size = 13.5;
    NSInteger weight = [run[@"weight"] integerValue];
    UIFontWeight fontWeight = weight >= 700 ? UIFontWeightBold :
                              weight >= 600 ? UIFontWeightSemibold :
                              weight >= 500 ? UIFontWeightMedium : UIFontWeightRegular;
    UIFont *font = [UIFont systemFontOfSize:size weight:fontWeight];
    if ([run[@"serif"] boolValue]) {
        UIFontDescriptor *descriptor = [font.fontDescriptor fontDescriptorWithDesign:UIFontDescriptorSystemDesignSerif];
        if (descriptor) font = [UIFont fontWithDescriptor:descriptor size:size];
    }
    return font;
}

static NSAttributedString *XLRBuildText(NSArray *paragraphs) {
    NSMutableAttributedString *result = [[NSMutableAttributedString alloc] init];
    for (NSUInteger index = 0; index < paragraphs.count; index++) {
        id item = paragraphs[index];
        if (![item isKindOfClass:NSDictionary.class]) continue;
        NSDictionary *paragraph = item;
        NSArray *runs = [paragraph[@"runs"] isKindOfClass:NSArray.class] ? paragraph[@"runs"] : @[];
        NSUInteger start = result.length;
        CGFloat lineHeight = 0;
        for (id value in runs) {
            if (![value isKindOfClass:NSDictionary.class]) continue;
            NSDictionary *run = value;
            NSString *text = [run[@"text"] isKindOfClass:NSString.class] ? run[@"text"] : @"";
            UIFont *font = XLRTextFont(run);
            lineHeight = MAX(lineHeight, run[@"lineHeight"] ? [run[@"lineHeight"] doubleValue] : font.lineHeight);
            NSMutableDictionary *attrs = [@{
                NSFontAttributeName: font,
                NSForegroundColorAttributeName: XLRTextColor(run[@"color"])
            } mutableCopy];
            if (run[@"letterSpacing"]) attrs[NSKernAttributeName] = run[@"letterSpacing"];
            if (run[@"baselineOffset"]) attrs[NSBaselineOffsetAttributeName] = run[@"baselineOffset"];
            [result appendAttributedString:[[NSAttributedString alloc] initWithString:text attributes:attrs]];
        }
        // Preserve paragraph boundaries in the copied plain text. Empty paragraphs
        // have a small line height; no invisible selection-only characters are used.
        if (index + 1 < paragraphs.count) {
            NSDictionary *attrs = result.length > start ? [result attributesAtIndex:result.length - 1 effectiveRange:NULL] :
                @{NSFontAttributeName: [UIFont systemFontOfSize:1], NSForegroundColorAttributeName: UIColor.clearColor};
            [result appendAttributedString:[[NSAttributedString alloc] initWithString:@"\n" attributes:attrs]];
        }
        if (result.length > start) {
            NSMutableParagraphStyle *style = [[NSMutableParagraphStyle alloc] init];
            style.paragraphSpacingBefore = MAX(0, [paragraph[@"spaceBefore"] doubleValue]);
            style.paragraphSpacing = MAX(0, [paragraph[@"spaceAfter"] doubleValue]);
            style.minimumLineHeight = lineHeight > 0 ? lineHeight : 7;
            style.maximumLineHeight = style.minimumLineHeight;
            style.lineBreakMode = NSLineBreakByWordWrapping;
            [result addAttribute:NSParagraphStyleAttributeName value:style range:NSMakeRange(start, result.length - start)];
        }
    }
    return result;
}

- (void)applySpec:(id)value {
    NSDictionary *spec = nil;
    if ([value isKindOfClass:NSString.class]) {
        NSData *data = [(NSString *)value dataUsingEncoding:NSUTF8StringEncoding];
        id parsed = data ? [NSJSONSerialization JSONObjectWithData:data options:0 error:nil] : nil;
        if ([parsed isKindOfClass:NSDictionary.class]) spec = parsed;
    } else if ([value isKindOfClass:NSDictionary.class]) {
        spec = value;
    }
    if (!spec) return;
    NSString *identity = [spec[@"identity"] isKindOfClass:NSString.class] ? spec[@"identity"] : @"";
    BOOL sameIdentity = [identity isEqualToString:self.identity];
    BOOL active = spec[@"active"] ? [spec[@"active"] boolValue] : YES;
    NSInteger clearEpoch = [spec[@"clearEpoch"] integerValue];
    if (!sameIdentity || !active || clearEpoch != self.clearEpoch) [self clearSelection];
    self.clearEpoch = clearEpoch;
    if (!sameIdentity) self.reportedHeight = -1;
    self.identity = identity;
    self.active = active;
    self.busy = [spec[@"busy"] boolValue];
    self.textView.selectable = active;
    self.textView.scrollEnabled = [spec[@"scrollable"] boolValue];
    self.textView.showsVerticalScrollIndicator = self.textView.scrollEnabled;
    // Insets are points, in the same coordinate space as Kuikly's iOS view frame.
    NSDictionary *inset = [spec[@"inset"] isKindOfClass:NSDictionary.class] ? spec[@"inset"] : @{};
    self.textView.textContainerInset = UIEdgeInsetsMake(MAX(0, [inset[@"top"] doubleValue]),
        MAX(0, [inset[@"left"] doubleValue]), MAX(0, [inset[@"bottom"] doubleValue]),
        MAX(0, [inset[@"right"] doubleValue]));
    NSArray *paragraphs = [spec[@"paragraphs"] isKindOfClass:NSArray.class] ? spec[@"paragraphs"] : @[];
    NSAttributedString *next = XLRBuildText(paragraphs);
    if (![self.textView.attributedText isEqualToAttributedString:next]) {
        NSString *previous = self.textView.text ?: @"";
        NSRange selected = self.textView.selectedRange;
        CGPoint offset = self.textView.contentOffset;
        self.changingText = YES;
        self.textView.attributedText = next;
        if (sameIdentity && active && [next.string hasPrefix:previous] &&
            selected.location != NSNotFound && NSMaxRange(selected) <= next.length) {
            self.textView.selectedRange = selected;
        } else {
            self.textView.selectedRange = NSMakeRange(0, 0);
        }
        self.changingText = NO;
        if (sameIdentity) self.textView.contentOffset = offset;
        else self.textView.contentOffset = CGPointZero;
    }
    [self reportSelection];
    [self updateStreamCursor];
    [self setNeedsLayout];
    [self scheduleMetrics];
}

// A decoration, never an attributed character: copying cannot include the
// streaming cursor. UITextInput supplies its position after the last glyph.
- (void)updateStreamCursor {
    BOOL visible = self.busy && self.active && self.textView.selectedRange.length == 0;
    self.streamCursor.hidden = !visible;
    if (!visible) {
        [self.streamCursor.layer removeAnimationForKey:@"streamBlink"];
        return;
    }
    CGRect caret = [self.textView caretRectForPosition:self.textView.endOfDocument];
    if (CGRectIsNull(caret) || CGRectIsInfinite(caret)) {
        self.streamCursor.hidden = YES;
        return;
    }
    CGFloat height = MIN(16, MAX(8, caret.size.height));
    self.streamCursor.frame = CGRectMake(caret.origin.x, caret.origin.y + (caret.size.height - height) / 2, 1.5, height);
    UIColor *color = self.textView.attributedText.length ?
        [self.textView.attributedText attribute:NSForegroundColorAttributeName
                                       atIndex:self.textView.attributedText.length - 1 effectiveRange:NULL] : UIColor.labelColor;
    self.streamCursor.backgroundColor = color ?: UIColor.labelColor;
    if (!UIAccessibilityIsReduceMotionEnabled() && ![self.streamCursor.layer animationForKey:@"streamBlink"]) {
        CABasicAnimation *blink = [CABasicAnimation animationWithKeyPath:@"opacity"];
        blink.fromValue = @1;
        blink.toValue = @0.15;
        blink.duration = 0.55;
        blink.autoreverses = YES;
        blink.repeatCount = HUGE_VALF;
        [self.streamCursor.layer addAnimation:blink forKey:@"streamBlink"];
    }
}

- (void)scheduleMetrics {
    if (self.metricsPending) return;
    self.metricsPending = YES;
    __weak typeof(self) weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        XLRSelectableText *view = weakSelf;
        if (!view) return;
        view.metricsPending = NO;
        CGFloat width = view.bounds.size.width;
        if (width <= 0 || !view.metricsCallback) return;
        [view updateStreamCursor];
        CGFloat height = ceil([view.textView sizeThatFits:CGSizeMake(width, CGFLOAT_MAX)].height);
        if (!view.streamCursor.hidden) {
            height = MAX(height, ceil(CGRectGetMaxY(view.streamCursor.frame) + view.textView.textContainerInset.bottom));
        }
        if (fabs(height - view.reportedHeight) > 0.5 || fabs(width - view.reportedWidth) > 0.5) {
            view.reportedHeight = height;
            view.reportedWidth = width;
            view.metricsCallback(@{@"height": @(height), @"width": @(width), @"identity": view.identity});
        }
    });
}

- (void)reportSelection {
    BOOL selected = self.active && self.textView.selectedRange.length > 0;
    [self updateStreamCursor];
    if (selected && XLRSelectedTextOwner != self) {
        [XLRSelectedTextOwner clearSelection];
        XLRSelectedTextOwner = self;
    } else if (!selected && XLRSelectedTextOwner == self) {
        XLRSelectedTextOwner = nil;
    }
    // 选区在 ⇒ 挂"点别处清选区"；选区没了 ⇒ 立刻摘掉（别让页面手势一直锁着）。
    if (selected) [self installOutsideTap]; else [self removeOutsideTap];
    if (selected == self.reportedSelection) return;
    self.reportedSelection = selected;
    if (self.selectionCallback) self.selectionCallback(@{@"active": @(selected)});
}

// ---- 选区退场：点别处 / 失焦 ----
- (void)installOutsideTap {
    if (self.outsideTap) return;
    UIWindow *window = self.window;
    if (!window) return;
    UITapGestureRecognizer *tap = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(handleOutsideTap:)];
    // 不抢任何既有触摸：只做"顺便清选区"。
    tap.cancelsTouchesInView = NO;
    tap.delaysTouchesEnded = NO;
    tap.delegate = self;
    [window addGestureRecognizer:tap];
    self.outsideTap = tap;
}

- (void)removeOutsideTap {
    if (!self.outsideTap) return;
    [self.outsideTap.view removeGestureRecognizer:self.outsideTap];
    self.outsideTap = nil;
}

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer {
    return YES;
}

- (void)handleOutsideTap:(UITapGestureRecognizer *)tap {
    if (self.textView.selectedRange.length == 0) { [self removeOutsideTap]; return; }
    CGPoint point = [tap locationInView:self];
    if (!CGRectContainsPoint(CGRectInset(self.bounds, -2, -2), point)) [self clearSelection];
}

// 失焦（点别处、菜单收起、切页）也把选区辞掉 —— 这是"钉死"的主要来源。
- (void)textViewDidEndEditing:(UITextView *)textView {
    if (!self.clearing) [self clearSelection];
}

- (void)textViewDidChangeSelection:(UITextView *)textView {
    if (!self.changingText) [self reportSelection];
}

- (void)clearSelection {
    if (self.clearing) return;
    self.clearing = YES;
    self.changingText = YES;
    self.textView.selectedRange = NSMakeRange(0, 0);
    [self.textView resignFirstResponder];
    self.changingText = NO;
    [self removeOutsideTap];
    self.clearing = NO;
    [self reportSelection];
}

- (void)dealloc {
    [self removeOutsideTap];
}

@end
