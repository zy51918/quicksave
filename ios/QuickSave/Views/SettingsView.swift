import Foundation
import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @StateObject private var model: SettingsViewModel
    @State private var importingFile = false
    @State private var exportingFile = false
    @State private var newDocument = QuickSaveTextDocument()
    @State private var showingAddCategory = false
    @State private var categoryDraft = ""
    @State private var renamingCategory: String?
    @State private var renameDraft = ""
    @State private var errorMessage: Feedback?

    init(repository: ClipRepository) {
        _model = StateObject(wrappedValue: SettingsViewModel(repository: repository))
    }

    private var renameCandidate: String {
        renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isRenameValid: Bool {
        guard let oldName = renamingCategory else { return false }
        return !renameCandidate.isEmpty && (renameCandidate == oldName || !model.categories.contains(renameCandidate))
    }

    var body: some View {
        List {
            Section {
                if model.targetFileConfigured {
                    Label("已配置目标文件", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(Color.quickSaveTealDark)
                    Button("重新选择文件") { importingFile = true }
                    Button("移除文件配置", role: .destructive) { model.clearTargetFile() }
                } else {
                    Label("尚未设置保存文件", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(Color.quickSaveCoral)
                    Button("选择保存文件") { exportingFile = true }
                }
                Text("保存的文字将追加到文件末尾，每条记录包含时间戳。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } header: {
                Text("保存目标文件")
            }

            Section {
                if #available(iOS 18.0, *) {
                    Label("添加到控制中心", systemImage: "archivebox")
                        .font(.subheadline.weight(.semibold))
                    Text("下拉控制中心 → 点左上「＋」→ 找到 QuickSave → 添加。\n添加后可一键保存剪切板文字，无需先打开 App。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Label("当前系统不支持", systemImage: "info.circle")
                        .foregroundStyle(Color.quickSaveCoral)
                    Text("控制中心控件需要 iOS 18 及以上系统。你的设备仍可使用主页保存与分享保存。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } header: {
                Text("快捷入口")
            }

            Section {
                if model.categories.isEmpty {
                    Text("暂无分类，点击下方按钮添加")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(model.categories, id: \.self) { category in
                        HStack {
                            Image(systemName: "line.3.horizontal")
                                .foregroundStyle(.secondary)
                            Text(category)
                            Spacer()
                            Button("重命名") {
                                renameDraft = category
                                renamingCategory = category
                            }
                            .font(.subheadline)
                            Button("删除", role: .destructive) {
                                model.deleteCategory(category)
                            }
                            .font(.subheadline)
                        }
                    }
                    .onMove { source, destination in
                        model.moveCategory(from: source, to: destination)
                    }
                }
                Button {
                    categoryDraft = ""
                    showingAddCategory = true
                } label: {
                    Label("新增分类", systemImage: "plus")
                }
            } header: {
                Text("分类管理")
            } footer: {
                Text("重命名分类不会修改已保存的记录。长按列表项可拖拽排序。")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { EditButton() }
        .task {
            model.validateTargetFileAccess()
        }
        .fileImporter(
            isPresented: $importingFile,
            allowedContentTypes: [.plainText],
            allowsMultipleSelection: false
        ) { result in
            do {
                guard let url = try result.get().first else { return }
                let bookmark = try BookmarkFileDataSource.makeBookmark(for: url)
                model.setTargetFile(bookmark: bookmark)
            } catch {
                errorMessage = Feedback(message: "无法选择文件：\(error.localizedDescription)", isError: true)
            }
        }
        .fileExporter(
            isPresented: $exportingFile,
            document: newDocument,
            contentType: .plainText,
            defaultFilename: "quicksave.txt"
        ) { result in
            do {
                let url = try result.get()
                let bookmark = try BookmarkFileDataSource.makeBookmark(for: url)
                model.setTargetFile(bookmark: bookmark)
            } catch {
                errorMessage = Feedback(message: "无法创建文件：\(error.localizedDescription)", isError: true)
            }
        }
        .alert("新增分类", isPresented: $showingAddCategory) {
            TextField("分类名称", text: $categoryDraft)
            Button("取消", role: .cancel) {}
            Button("确定") { model.addCategory(categoryDraft) }
                .disabled(categoryDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.categories.contains(categoryDraft.trimmingCharacters(in: .whitespacesAndNewlines)))
        } message: {
            Text("分类名不能为空或重复")
        }
        .alert("重命名分类", isPresented: Binding(
            get: { renamingCategory != nil },
            set: { if !$0 { renamingCategory = nil } }
        )) {
            TextField("分类名称", text: $renameDraft)
            Button("取消", role: .cancel) { renamingCategory = nil }
            Button("确定") {
                if let oldName = renamingCategory {
                    model.renameCategory(oldName, to: renameDraft)
                }
                renamingCategory = nil
            }
            .disabled(!isRenameValid)
        } message: {
            Text("分类名不能为空或重复")
        }
        .quickSaveToast($errorMessage)
    }
}

struct QuickSaveTextDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText] }

    var text: String

    init(text: String = "") {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let text = String(data: data, encoding: .utf8)
        else {
            self.text = ""
            return
        }
        self.text = text
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
