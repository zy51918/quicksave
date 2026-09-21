import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private let statusLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        configureView()
        loadSharedText()
    }

    private func configureView() {
        let titleLabel = UILabel()
        titleLabel.text = "QuickSave"
        titleLabel.font = .preferredFont(forTextStyle: .headline)
        titleLabel.textAlignment = .center

        statusLabel.text = "正在保存分享内容…"
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 0
        statusLabel.textColor = .secondaryLabel

        let stack = UIStackView(arrangedSubviews: [titleLabel, statusLabel])
        stack.axis = .vertical
        stack.spacing = 18
        stack.alignment = .fill
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)
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

    private func finish(with message: String, success: Bool) {
        statusLabel.text = message
        statusLabel.textColor = success ? .systemGreen : .systemRed
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { [weak self] in
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
