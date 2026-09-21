import SwiftUI

extension Color {
    static let quickSavePaper = Color(red: 0.957, green: 0.969, blue: 0.969)
    static let quickSaveInk = Color(red: 0.063, green: 0.165, blue: 0.212)
    static let quickSaveInkSoft = Color(red: 0.271, green: 0.380, blue: 0.416)
    static let quickSaveTeal = Color(red: 0.031, green: 0.498, blue: 0.482)
    static let quickSaveTealPale = Color(red: 0.737, green: 0.914, blue: 0.894)
    static let quickSaveCoral = Color(red: 0.725, green: 0.302, blue: 0.239)
    static let quickSaveCoralPale = Color(red: 1.0, green: 0.855, blue: 0.827)
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
                    .foregroundStyle(Color.quickSaveTeal)
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
