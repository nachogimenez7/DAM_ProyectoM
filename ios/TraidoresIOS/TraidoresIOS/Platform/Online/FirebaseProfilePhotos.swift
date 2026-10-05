import CryptoKit
import FirebaseAuth
import FirebaseFirestore
import FirebaseStorage
import Foundation
import ImageIO
import Observation
import UIKit

enum GalleryPhotoCodec {
    static let maxBytes = 256 * 1024
    static func jpeg(_ input: Data) throws -> Data {
        guard input.count <= 15_000_000, let source = CGImageSourceCreateWithData(input as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: 512,
              ] as CFDictionary) else { throw OnlineError.invalidProfile }
        let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
        let side: CGFloat = 512
        let picture = UIImage(cgImage: image)
        let scale = side / min(picture.size.width, picture.size.height)
        let size = CGSize(width: picture.size.width * scale, height: picture.size.height * scale)
        // Redrawing also removes orientation/GPS/EXIF metadata from the output.
        let square = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { context in
            UIColor.black.setFill(); context.fill(CGRect(x: 0, y: 0, width: side, height: side))
            picture.draw(in: CGRect(x: (side - size.width) / 2, y: (side - size.height) / 2,
                                   width: size.width, height: size.height))
        }
        for quality in [0.85, 0.7, 0.55, 0.4, 0.25] {
            if let data = square.jpegData(compressionQuality: quality), data.count <= maxBytes { return data }
        }
        throw OnlineError.server("No pudimos preparar esta foto. Elegí otra imagen.")
    }
}

/// Durable, UID-scoped publication queue. Text edits never write fotoPerfil. A new
/// selection replaces the queued selection; an old completion cannot clear it.
@MainActor @Observable
final class FirebaseProfilePhotos {
    private(set) var state: PhotoSyncState = .idle
    private(set) var owner: String?
    private(set) var preview: PendingProfilePhoto?
    @ObservationIgnored private var operation: Task<Void, Error>?
    @ObservationIgnored private var generation = 0
    #if DEBUG
    @ObservationIgnored var beforePublicationForTesting: (() async -> Void)?
    #endif
    private struct Pending: Codable, Equatable {
        let jpeg: Data?
        var revision: String {
            jpeg.map { SHA256.hash(data: $0).map { String(format: "%02x", $0) }.joined() } ?? "remove"
        }
    }
    private func key(_ uid: String) -> String { "online.pendingPhoto.\(uid)" }
    private func pending(_ uid: String) -> Pending? {
        UserDefaults.menuStore.data(forKey: key(uid)).flatMap { try? JSONDecoder().decode(Pending.self, from: $0) }
    }

    func activate(uid: String) {
        if owner != uid { clear(); owner = uid }
        if FirebaseSetup.profileStorageEnabled, operation == nil, let edit = pending(uid) {
            preview = edit.jpeg.map(PendingProfilePhoto.image) ?? .removal
            state = .pending
            Task { try? await self.flush() }
        }
    }
    func clear() {
        generation += 1; operation?.cancel(); operation = nil
        owner = nil; state = .idle; preview = nil
        #if DEBUG
        beforePublicationForTesting = nil
        #endif
        // The previous UID's pending bytes stay on disk for its next authenticated session.
    }
    func setPhoto(_ data: Data) async throws {
        try await enqueue(Pending(jpeg: GalleryPhotoCodec.jpeg(data)))
    }
    func removePhoto() async throws { try await enqueue(Pending(jpeg: nil)) }
    private func enqueue(_ edit: Pending) async throws {
        guard let owner else { throw OnlineError.accountRequired }
        try ensureOwner(owner)
        UserDefaults.menuStore.set(try JSONEncoder().encode(edit), forKey: key(owner))
        preview = edit.jpeg.map(PendingProfilePhoto.image) ?? .removal
        state = .pending
        try await flush()
    }
    func flush() async throws {
        if let operation { return try await operation.value }
        guard let uid = owner else { throw OnlineError.accountRequired }
        try ensureOwner(uid)
        guard pending(uid) != nil else { return }
        let token = generation
        let work = Task { @MainActor in
            do {
                while let edit = self.pending(uid) {
                    try self.ensureOwner(uid)
                    try Task.checkCancellation()
                    self.state = .uploading
                    let storage = Storage.storage()
                    var url = ""
                    if let bytes = edit.jpeg {
                        let reference = storage.reference().child("profilePhotos/\(uid)/avatar_\(edit.revision).jpg")
                        let metadata = StorageMetadata()
                        metadata.contentType = "image/jpeg"; metadata.cacheControl = "private,max-age=3600"
                        _ = try await reference.putDataAsync(bytes, metadata: metadata)
                        var components = URLComponents(url: try await reference.downloadURL(), resolvingAgainstBaseURL: false)!
                        components.queryItems = (components.queryItems ?? []) + [URLQueryItem(name: "v", value: edit.revision)]
                        url = components.url!.absoluteString
                    }
                    #if DEBUG
                    if FirebaseSetup.emulatorHost != nil { await self.beforePublicationForTesting?() }
                    #endif
                    try self.ensureOwner(uid)
                    try Task.checkCancellation()
                    // Confirm the URL before clearing the local outbox. Cosmetic edits and
                    // history counters cannot be overwritten by a photo upload.
                    try await Firestore.firestore().collection("perfiles_publicos").document(uid)
                        .updateData(["fotoPerfil": url, "fotoPlayGames": "", "actualizadaEn": FieldValue.serverTimestamp()])
                    try self.ensureOwner(uid)
                    if edit.jpeg == nil {
                        // Removal deletes every published version, including a prior upload
                        // whose completion was interrupted. Replacement retains versions used
                        // by an active match's roster snapshot.
                        let objects = try await storage.reference().child("profilePhotos/\(uid)").listAll()
                        for reference in objects.items {
                            try self.ensureOwner(uid)
                            try await reference.delete()
                        }
                    }
                    if self.pending(uid) == edit { UserDefaults.menuStore.removeObject(forKey: self.key(uid)) }
                    if self.pending(uid) == nil {
                        self.preview = nil
                        self.state = url.isEmpty ? .idle : .published(URL(string: url)!)
                    }
                }
            } catch {
                if self.generation == token {
                    let mapped = FirebaseAccountService.onlineError(error)
                    self.state = .failed(mapped)
                }
                throw error
            }
        }
        operation = work
        defer { if generation == token { operation = nil } }
        try await work.value
    }
    private func ensureOwner(_ uid: String) throws {
        guard owner == uid, let user = Auth.auth().currentUser, !user.isAnonymous,
              user.uid == uid else { throw OnlineError.sessionExpired }
    }
}
