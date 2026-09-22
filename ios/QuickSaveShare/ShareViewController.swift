import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private let toast = UIView()
    private let iconView = UIImageView()
    private let messageLabel = UILabel()

    /// 与主 App `QuickSaveStyle` 的配色保持一致（Extension 是独立 target，无法直接复用）
    private enum Palette {
        static let ink = UIColor(red: 0.063, green: 0.165, blue: 0.212, alpha: 0.94)
        static let message = UIColor.white
        static let success = UIColor(red: 0.737, green: 0.914, blue: 0.894, alpha: 1)
        static let failure = UIColor(red: 1.0, green: 0.855, blue: 0.827, alpha: 1)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        // 透明背景 + 居中胶囊，让 Extension 呈现为浮层提示而非整页
        view.backgroundColor = .clear
        configureToast()
        showToast(message: "正在保存分享内容…", state: .loading)
        loadSharedText()
    }

    private func configureToast() {
        toast.backgroundColor = Palette.ink
        toast.layer.cornerRadius = 14
        toast.layer.cornerCurve = .continuous
        toast.translatesAutoresizingMaskIntoConstraints = false
        toast.alpha = 0

        iconView.contentMode = .scaleAspectFit
        iconView.setContentHuggingPriority(.required, for: .horizontal)
        iconView.setContentCompressionResistancePriority(.required, for: .horizontal)
        iconView.widthAnchor.constraint(equalToConstant: 18).isActive = true
        iconView.heightAnchor.constraint(equalToConstant: 18).isActive = true

        messageLabel.font = .preferredFont(forTextStyle: .subheadline)
        messageLabel.textColor = .white
        messageLabel.numberOfLines = 0

        let row = UIStackView(arrangedSubviews: [iconView, messageLabel])
        row.axis = .horizontal
        row.spacing = 10
        row.alignment = .center
        row.translatesAutoresizingMaskIntoConstraints = false
        toast.addSubview(row)
        view.addSubview(toast)

        NSLayoutConstraint.activate([
            row.leadingAnchor.constraint(equalTo: toast.leadingAnchor, constant: 14),
            row.trailingAnchor.constraint(equalTo: toast.trailingAnchor, constant: -14),
            row.topAnchor.constraint(equalTo: toast.topAnchor, constant: 12),
            row.bottomAnchor.constraint(equalTo: toast.bottomAnchor, constant: -12),

            toast.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            toast.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            toast.leadingAnchor.constraint(greaterThanOrEqualTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            toast.trailingAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24)
        ])
    }

    private func loadSharedText() {
        guard let item = extensionContext?.inputItems.first as? NSExtensionItem,
              extensionContext?.inputItems.count == 1,
              let attachments = item.attachments,
              attachments.count == 1,
              let provider = attachments.first,
              provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
              !provider.hasItemConformingToTypeIdentifier(UTType.url.identifier)
        else {
            finish(with: "不支持的分享内容", success: false)
            return
        }

        provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] item, error in
            DispatchQueue.main.async {
                guard let self else { return }
                if let error {
                    self.finish(with: "读取失败：\(error.localizedDescription)", success: false)
                    return
                }
                let text: String?
                if let string = item as? String {
                    text = string
                } else if let string = item as? NSString {
                    text = string as String
                } else if let data = item as? Data {
                    text = String(data: data, encoding: .utf8)
                } else {
                    text = nil
                }
                guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                    self.finish(with: "分享内容为空", success: false)
                    return
                }
                self.save(text)
            }
        }
    }

    private func save(_ text: String) {
        let preferences = AppGroupPreferencesStore(appGroupIdentifier: SharedPayloadStore.appGroupIdentifier)
        let repository = ClipRepositoryImpl(
            preferences: preferences,
            files: BookmarkFileDataSource()
        )
        let category = repository.selectedCategory
        Task { [weak self] in
            let result = await repository.saveEntry(text: text, category: category)
            await MainActor.run {
                switch result {
                case .success:
                    self?.finish(with: "已保存", success: true)
                case let .failure(error):
                    self?.finish(with: error.errorDescription ?? "保存失败", success: false)
                }
            }
        }
    }

    private enum ToastState {
        case loading
        case success
        case failure

        var icon: UIImage? {
            switch self {
            case .loading: return UIImage(systemName: "ellipsis.circle")
            case .success: return UIImage(systemName: "checkmark.circle.fill")
            case .failure: return UIImage(systemName: "exclamationmark.triangle.fill")
            }
        }

        var iconColor: UIColor {
            switch self {
            case .loading: return Palette.message
            case .success: return Palette.success
            case .failure: return Palette.failure
            }
        }
    }

    private func showToast(message: String, state: ToastState) {
        iconView.image = state.icon
        iconView.tintColor = state.iconColor
        messageLabel.text = message
        UIView.animate(withDuration: 0.2) { self.toast.alpha = 1 }
    }

    private func finish(with message: String, success: Bool) {
        showToast(message: message, state: success ? .success : .failure)

        // Android 端分享结果用 Toast.LENGTH_SHORT(2s)，但其 Toast 不阻塞界面；
        // iOS sheet 会遮住原 App，故略缩短为 1.5s
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            guard let self else { return }
            if success {
                self.extensionContext?.completeRequest(returningItems: nil)
            } else {
                self.extensionContext?.cancelRequest(withError: NSError(
                    domain: "QuickSave.ShareExtension",
                    code: 1,
                    userInfo: [NSLocalizedDescriptionKey: message]
                ))
            }
        }
    }
}