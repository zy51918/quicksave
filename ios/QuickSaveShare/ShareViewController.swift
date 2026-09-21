import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private let statusLabel = UILabel()
    private let saveButton = UIButton(type: .system)
    private var sharedText: String?

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

        statusLabel.text = "正在读取分享内容…"
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 0
        statusLabel.textColor = .secondaryLabel

        saveButton.setTitle("导入到 QuickSave", for: .normal)
        saveButton.titleLabel?.font = .preferredFont(forTextStyle: .headline)
        saveButton.addTarget(self, action: #selector(save), for: .touchUpInside)
        saveButton.isEnabled = false

        let stack = UIStackView(arrangedSubviews: [titleLabel, statusLabel, saveButton])
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
              let provider = item.attachments?.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) })
        else {
            finish(with: "没有可导入的文字", success: false)
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
                    self.finish(with: "没有可导入的文字", success: false)
                    return
                }
                self.sharedText = text
                self.statusLabel.text = "文字已准备好，回到 QuickSave 后可保存"
                self.saveButton.isEnabled = true
            }
        }
    }

    @objc private func save() {
        guard let sharedText else { return }
        do {
            try SharedPayloadStore().write(sharedText)
            finish(with: "已导入，打开 QuickSave 完成保存", success: true)
        } catch {
            finish(with: error.localizedDescription, success: false)
        }
    }

    private func finish(with message: String, success: Bool) {
        statusLabel.text = message
        statusLabel.textColor = success ? .systemGreen : .systemRed
        saveButton.isEnabled = false
        if success {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) { [weak self] in
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
        }
    }
}
