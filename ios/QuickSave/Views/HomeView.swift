import SwiftUI

struct HomeView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var model: HomeViewModel
    private let dependencies: AppDependencies
    @State private var showingAddCategory = false
    @State private var categoryDraft = ""

    init(dependencies: AppDependencies = .shared) {
        self.dependencies = dependencies
        _model = StateObject(wrappedValue: HomeViewModel(
            repository: dependencies.repository,
            payloads: dependencies.sharedPayloads
        ))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !model.targetFileConfigured {
                        NoFileWarningCard(repository: dependencies.repository)
                    }

                    CategoryChipRow(
                        categories: model.categories,
                        selectedCategory: model.selectedCategory,
                        onSelect: model.selectCategory,
                        onAdd: {
                            categoryDraft = ""
                            showingAddCategory = true
                        }
                    )

                    SectionLabel(eyebrow: "FROM CLIPBOARD", title: "刚刚复制的内容")
                    if let clipText = model.clipText {
                        ClipboardCard(
                            text: clipText,
                            isSaving: model.isClipSaving,
                            onSave: model.saveClipboard
                        )
                    } else {
                        EmptyClipboardState()
                    }

                    SectionLabel(eyebrow: "OR WRITE IT HERE", title: "手动记录")
                    ManualInputCard(
                        text: $model.manualInputText,
                        isSaving: model.isManualSaving,
                        onSave: model.saveManualInput
                    )

                    if model.targetFileConfigured {
                        ClearFileAction {
                            model.showClearConfirmation = true
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 16)
            }
            .background(Color.quickSavePaper.ignoresSafeArea())
            .navigationTitle("快速归档")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink {
                        SettingsView(repository: dependencies.repository)
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("打开设置")
                }
            }
        }
        .task {
            model.refreshClipboard()
            model.consumeSharedPayload()
            model.validateTargetFileAccess()
        }
        .onChange(of: scenePhase) { phase in
            guard phase == .active else { return }
            model.refreshClipboard()
            model.consumeSharedPayload()
            model.validateTargetFileAccess()
        }
        .alert("清空保存文件", isPresented: $model.showClearConfirmation) {
            Button("取消", role: .cancel) {}
            Button("清空", role: .destructive) {
                model.clearSavedFile()
            }
        } message: {
            Text("确认清空文件内全部内容？此操作不可恢复。")
        }
        .alert("新增分类", isPresented: $showingAddCategory) {
            TextField("分类名称", text: $categoryDraft)
            Button("取消", role: .cancel) {}
            Button("确定") {
                model.addCategory(categoryDraft)
            }
            .disabled(categoryDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.categories.contains(categoryDraft.trimmingCharacters(in: .whitespacesAndNewlines)))
        } message: {
            Text("分类名不能为空或重复")
        }
        .quickSaveToast($model.feedback)
    }
}

private struct NoFileWarningCard: View {
    let repository: ClipRepository

    var body: some View {
        QuickSaveCard(background: Color.quickSaveCoralPale) {
            HStack(alignment: .top, spacing: 12) {
                Rectangle()
                    .fill(Color.quickSaveCoral)
                    .frame(width: 4)
                VStack(alignment: .leading, spacing: 6) {
                    Text("还差一步就能保存")
                        .font(.headline)
                        .foregroundStyle(Color.quickSaveInk)
                    Text("先选择一个目标文件，QuickSave 才能把内容写进去。")
                        .font(.subheadline)
                        .foregroundStyle(Color.quickSaveInkSoft)
                    NavigationLink("去选择文件") {
                        SettingsView(repository: repository)
                    }
                    .buttonStyle(.bordered)
                    .tint(Color.quickSaveCoral)
                }
            }
        }
    }
}

private struct CategoryChipRow: View {
    let categories: [String]
    let selectedCategory: String?
    let onSelect: (String?) -> Void
    let onAdd: () -> Void

    var body: some View {
        QuickSaveCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    Text("分类（可选）").font(.subheadline.weight(.semibold))
                    Text("用于之后查找").font(.caption).foregroundStyle(Color.quickSaveInkSoft)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(categories, id: \.self) { category in
                            Button {
                                onSelect(selectedCategory == category ? nil : category)
                            } label: {
                                HStack(spacing: 5) {
                                    if selectedCategory == category {
                                        Image(systemName: "checkmark")
                                    }
                                    Text(category)
                                }
                                .font(.subheadline)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .foregroundStyle(selectedCategory == category ? Color.white : Color.quickSaveTeal)
                                .background(selectedCategory == category ? Color.quickSaveTeal : Color.clear, in: Capsule())
                                .overlay(Capsule().stroke(Color.quickSaveTeal, lineWidth: selectedCategory == category ? 0 : 1))
                            }
                            .accessibilityLabel("分类 \(category)")
                        }
                        Button(action: onAdd) {
                            Label("新增", systemImage: "plus")
                                .font(.subheadline)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .foregroundStyle(Color.quickSaveTeal)
                                .overlay(Capsule().stroke(Color.quickSaveTeal, lineWidth: 1))
                        }
                    }
                }
            }
        }
    }
}

private struct ClipboardCard: View {
    let text: String
    let isSaving: Bool
    let onSave: () -> Void

    var body: some View {
        QuickSaveCard(background: Color.quickSaveTealPale) {
            VStack(alignment: .leading, spacing: 14) {
                Label {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("当前剪切板").font(.headline)
                        Text("准备好保存到文件").font(.caption).foregroundStyle(Color.quickSaveInkSoft)
                    }
                } icon: {
                    Image(systemName: "doc.on.clipboard.fill")
                        .font(.title2)
                        .foregroundStyle(Color.white)
                        .frame(width: 36, height: 36)
                        .background(Color.quickSaveTeal, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                Text(text)
                    .font(.body)
                    .foregroundStyle(Color.quickSaveInk)
                    .lineLimit(4)
                SaveButton(title: isSaving ? "保存中…" : "保存到文件", isEnabled: !isSaving, action: onSave)
            }
        }
    }
}

private struct EmptyClipboardState: View {
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "doc.on.clipboard")
            Text("剪切板为空，请先在其他应用复制文字")
                .font(.subheadline)
        }
        .foregroundStyle(Color.quickSaveInkSoft)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.quickSaveInkSoft.opacity(0.08), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct ManualInputCard: View {
    @Binding var text: String
    let isSaving: Bool
    let onSave: () -> Void

    var body: some View {
        QuickSaveCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("写下一条新的记录").font(.headline)
                ZStack(alignment: .topLeading) {
                    TextEditor(text: $text)
                        .frame(minHeight: 110, maxHeight: 190)
                        .padding(7)
                        .scrollContentBackground(.hidden)
                        .background(Color.clear)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.quickSaveInkSoft.opacity(0.45)))
                    if text.isEmpty {
                        Text("在此输入要保存的文字")
                            .foregroundStyle(Color.quickSaveInkSoft.opacity(0.8))
                            .padding(.horizontal, 13)
                            .padding(.vertical, 15)
                            .allowsHitTesting(false)
                    }
                }
                SaveButton(title: isSaving ? "保存中…" : "保存到文件", isEnabled: !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSaving, action: onSave)
            }
        }
    }
}

private struct SaveButton: View {
    let title: String
    let isEnabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: "square.and.arrow.down")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(Color.quickSaveTeal)
        .disabled(!isEnabled)
    }
}

private struct ClearFileAction: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label("清空保存文件内容", systemImage: "trash")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(Color.quickSaveCoral)
    }
}
