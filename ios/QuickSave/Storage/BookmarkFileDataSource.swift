import Foundation

enum FileDataSourceError: Error {
    case missing
    case stale
    case inaccessible
}

protocol FileDataSource: Sendable {
    func appendLine(_ line: String, to bookmark: Data) async throws
    func clearFile(bookmark: Data) async throws
}

actor BookmarkFileDataSource: FileDataSource {
    private let fileManager: FileManager

    init(fileManager: FileManager = .default) {
        self.fileManager = fileManager
    }

    static func makeBookmark(for url: URL) throws -> Data {
        guard url.startAccessingSecurityScopedResource() else {
            throw FileDataSourceError.inaccessible
        }
        defer { url.stopAccessingSecurityScopedResource() }
        return try url.bookmarkData(options: [.withSecurityScope], includingResourceValuesForKeys: nil, relativeTo: nil)
    }

    func appendLine(_ line: String, to bookmark: Data) async throws {
        let url = try resolve(bookmark)
        let data = Data(line.utf8)
        guard fileManager.fileExists(atPath: url.path) else { throw FileDataSourceError.missing }
        let handle = try FileHandle(forWritingTo: url)
        defer {
            try? handle.close()
            url.stopAccessingSecurityScopedResource()
        }
        try handle.seekToEnd()
        try handle.write(contentsOf: data)
    }

    func clearFile(bookmark: Data) async throws {
        let url = try resolve(bookmark)
        defer { url.stopAccessingSecurityScopedResource() }
        guard fileManager.fileExists(atPath: url.path) else { throw FileDataSourceError.missing }
        try Data().write(to: url, options: .atomic)
    }

    func isAccessible(bookmark: Data?) -> Bool {
        guard let bookmark else { return false }
        do {
            let url = try resolve(bookmark)
            url.stopAccessingSecurityScopedResource()
            return true
        } catch {
            return false
        }
    }

    private func resolve(_ bookmark: Data) throws -> URL {
        var isStale = false
        let url: URL
        do {
            url = try URL(
                resolvingBookmarkData: bookmark,
                options: [.withoutUI, .withoutMounting],
                relativeTo: nil,
                bookmarkDataIsStale: &isStale
            )
        } catch {
            throw FileDataSourceError.inaccessible
        }
        guard !isStale else { throw FileDataSourceError.stale }
        guard url.startAccessingSecurityScopedResource() else { throw FileDataSourceError.inaccessible }
        return url
    }
}
