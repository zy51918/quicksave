import SwiftUI

extension Color {
    static let quickSavePaper = Color(red: 0.957, green: 0.969, blue: 0.969)
    static let quickSaveInk = Color(red: 0.063, green: 0.165, blue: 0.212)
    static let quickSaveInkSoft = Color(red: 0.271, green: 0.380, blue: 0.416)

    /// 品牌主色（浅绿）。亮度极高（白字在其上仅 1.46:1），因此**只作填充**：
    /// 实色按钮底、选中胶囊底。其上的文字/图标一律用 `quickSaveLimeInk`。
    static let quickSaveTeal = Color(red: 0.773, green: 0.882, blue: 0.400)

    /// 主色填充之上的文字/图标（8.6:1）。
    static let quickSaveLimeInk = Color(red: 0.176, green: 0.216, blue: 0.043)

    /// 浅色底上的主色文字与描边（Paper 上 7.6:1）。
    static let quickSaveTealDark = Color(red: 0.275, green: 0.333, blue: 0.067)

    /// 浅色容器底（剪切板卡背景）。
    static let quickSaveTealPale = Color(red: 0.929, green: 0.965, blue: 0.816)

    static let quickSaveCoral = Color(red: 0.725, green: 0.302, blue: 0.239)
    static let quickSaveCoralPale = Color(red: 1.0, green: 0.855, blue: 0.827)

    /// 深色背景之上的中性文字/图标。对应 Android 深色主题的 `onSurfaceVariant`。
    ///
    /// 控制中心控件的「保存中」态用它：控件背板由系统渲染为深色，
    /// 原用的 `quickSaveInkSoft`（`#45616A`）在其上仅约 1.5:1，图标几乎不可见；
    /// 本色约 6.0:1，同时保持中性灰调，与品牌绿/警示红明确区分。
    static let quickSaveOnSurfaceVariant = Color(red: 0.725, green: 0.808, blue: 0.816)
}

struct QuickSaveCard<Content: View>: View {
    let background: Color
    let content: Content

    init(background: Color = .white, @ViewBuilder content: () -> Content) {
        self.background = background
        self.content = content()
    }

    var body: some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct SectionLabel: View {
    let eyebrow: String
    let title: String

    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(eyebrow)
                    .font(.caption.monospaced().weight(.semibold))
                    .foregroundStyle(Color.quickSaveTealDark)
                Text(title)
                    .font(.headline)
                    .foregroundStyle(Color.quickSaveInk)
            }
            Rectangle()
                .fill(Color.quickSaveInkSoft.opacity(0.25))
                .frame(width: 48, height: 1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 轻量结果反馈浮层，对齐 Android 端 Toast：不阻塞操作、自动消失、点击可提前关闭
struct QuickSaveToast: View {
    let feedback: Feedback
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: feedback.isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .font(.subheadline)
                .foregroundStyle(feedback.isError ? Color.quickSaveCoralPale : Color.quickSaveTealPale)
            Text(feedback.message)
                .font(.subheadline)
                .foregroundStyle(Color.white)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color.quickSaveInk.opacity(0.94), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
        .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .onTapGesture(perform: onDismiss)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isStaticText)
    }
}

private struct ToastModifier: ViewModifier {
    @Binding var feedback: Feedback?

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .bottom) {
                if let feedback {
                    QuickSaveToast(feedback: feedback) { self.feedback = nil }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 16)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.85), value: feedback?.id)
            .task(id: feedback?.id) {
                guard let current = feedback else { return }
                // 对齐 Android Toast 时长：成功 LENGTH_SHORT(2s)，失败 LENGTH_LONG(3.5s)
                let seconds: Double = current.isError ? 3.5 : 2.0
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                guard !Task.isCancelled, feedback?.id == current.id else { return }
                feedback = nil
            }
    }
}

extension View {
    /// 以浮层 Toast 展示结果反馈，替代阻塞式 alert
    func quickSaveToast(_ feedback: Binding<Feedback?>) -> some View {
        modifier(ToastModifier(feedback: feedback))
    }
}
