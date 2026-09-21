import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @StateObject private var model: SettingsViewModel
    @State private var importingFile = false
    @State private var showingAddCategory = false
    @State private var categoryDraft = ""
    @State private var renamingCategory: String?
    @State private var renameDraft = ""
    @State private var errorMessage: String?

    init(repository: ClipRepository) {
        _model = StateObject(wrappedValue: SettingsViewModel(repository: repository))
    }

    var body: some View {
        List {
            Section {
                if model.targetFileConfigured {
                    Label("已配置目标文件", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(Color.quickSaveTeal)
                    Button("重新选择文件") { importingFile = true }
                    Button("移除文件配置", role: .destructive) { model.clearTargetFile() }
                } else {
                    Label("尚未设置保存文件", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(Color.quickSaveCoral)
                    Button("选择保存文件") { importingFile = true }
                }
                Text("保存的文字将追加到文件末尾，每条记录包含时间戳。")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            } header: {
                Text("保存目标文件")
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
                errorMessage = "无法选择文件：\(error.localizedDescription)"
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
            .disabled(renameDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        } message: {
            Text("分类名不能为空或重复")
        }
        .alert("文件选择失败", isPresented: Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )) {
            Button("知道了", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "未知错误")
        }
    }
}
