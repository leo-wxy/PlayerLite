#pragma once

#include <cstdint>
#include <filesystem>
#include <list>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace cachecore {

struct CacheLookupSnapshot {
    std::string resource_key;
    std::string data_file_path;
    std::string config_file_path;
    std::string extra_file_path;
    int64_t data_file_size_bytes = 0;
    int32_t block_size_bytes = 64 * 1024;
    int64_t content_length = -1;
    int64_t duration_ms = -1;
    std::vector<int64_t> cached_blocks;
    int64_t last_access_epoch_ms = -1;
};

class ProviderBridge {
public:
    virtual ~ProviderBridge() = default;

    virtual std::vector<uint8_t> ReadAt(int64_t provider_handle, int64_t offset, int32_t size) = 0;
    virtual void CancelInFlightRead(int64_t provider_handle) = 0;
    virtual int64_t QueryContentLength(int64_t provider_handle) = 0;
    virtual void Close(int64_t provider_handle) = 0;
};

class CacheRuntime {
public:
    bool Init(const std::string& cache_root_path, int64_t memory_cache_cap_bytes);
    void Shutdown();
    bool IsInitialized() const;
    void SetProviderBridge(ProviderBridge* bridge);

    int64_t OpenSession(
            const std::string& resource_key,
            int32_t block_size_bytes,
            int64_t content_length_hint,
            int64_t duration_ms_hint,
            int64_t provider_handle);
    bool CloseSession(int64_t session_id);

    std::vector<uint8_t> Read(int64_t session_id, int32_t size);
    std::vector<uint8_t> ReadAt(int64_t session_id, int64_t offset, int32_t size);
    int64_t Seek(int64_t session_id, int64_t offset, int32_t whence);
    void CancelPendingRead(int64_t session_id);

    std::optional<CacheLookupSnapshot> Lookup(const std::string& resource_key);
    std::vector<CacheLookupSnapshot> LookupByPrefix(const std::string& prefix, int32_t limit);
    bool ClearAll();
    bool ReleaseProviderHandle(int64_t provider_handle);

    static std::string BuildLookupJson(const CacheLookupSnapshot& snapshot);
    static std::string BuildLookupArrayJson(const std::vector<CacheLookupSnapshot>& snapshots);

private:
    struct SessionStorageFiles {
        std::filesystem::path data_file;
        std::filesystem::path config_file;
        std::filesystem::path extra_file;
        int32_t block_size_bytes = 64 * 1024;
        int64_t content_length = -1;
        int64_t duration_ms = -1;
        std::unordered_set<int64_t> cached_blocks;
    };

    struct SessionState {
        int64_t session_id = -1;
        std::string resource_key;
        int64_t provider_handle = -1;
        int64_t current_offset = 0;
        bool closed = false;
        SessionStorageFiles storage;
        std::unordered_map<int64_t, std::vector<uint8_t>> memory_blocks;
    };

    struct StorageSnapshot {
        int32_t block_size_bytes = 64 * 1024;
        int64_t content_length = -1;
        int64_t duration_ms = -1;
        std::unordered_set<int64_t> block_indexes;
        int64_t last_access_epoch_ms = -1;
    };

    struct MemoryKey {
        int64_t session_id = -1;
        int64_t block_index = -1;

        bool operator==(const MemoryKey& other) const {
            return session_id == other.session_id && block_index == other.block_index;
        }
    };

    struct MemoryKeyHash {
        std::size_t operator()(const MemoryKey& key) const {
            return static_cast<std::size_t>(key.session_id * 1315423911ULL + key.block_index * 2654435761ULL);
        }
    };

    bool EnsureRootDirLocked();
    std::filesystem::path RootPathLocked() const;

    bool EnsureStorageFilesLocked(
            const std::string& resource_key,
            int32_t block_size_bytes,
            int64_t content_length_hint,
            int64_t duration_ms_hint,
            SessionStorageFiles* out_storage);
    StorageSnapshot ReadStorageSnapshotLocked(
            const std::filesystem::path& config_file,
            int32_t fallback_block_size_bytes,
            int64_t fallback_content_length,
            int64_t fallback_duration_ms);
    bool PersistConfigLocked(const SessionState& session);

    std::vector<uint8_t> ReadAtLocked(SessionState* session, int64_t offset, int32_t size);
    bool LoadOrFetchBlockLocked(SessionState* session, int64_t block_index, std::vector<uint8_t>* out_block);
    std::vector<uint8_t> ReadBlockFromDiskLocked(const SessionState& session, int64_t block_index);
    bool WriteBlockToDiskLocked(const SessionState& session, int64_t block_index, const std::vector<uint8_t>& bytes);

    void TouchMemoryBlockLocked(int64_t session_id, int64_t block_index);
    void InsertMemoryBlockLocked(int64_t session_id, int64_t block_index, const std::vector<uint8_t>& bytes);
    void RemoveMemoryBlockLocked(int64_t session_id, int64_t block_index);
    void EvictMemoryIfNeededLocked();
    void RemoveSessionMemoryLocked(const SessionState& session);

    std::optional<CacheLookupSnapshot> LookupLocked(const std::string& resource_key);
    std::unordered_set<std::string> CollectResourceKeysLocked() const;

    static std::optional<std::string> ParseResourceKeyFromFileName(const std::string& file_name);
    static std::optional<int64_t> ParseLongField(const std::string& json, const std::string& field);
    static std::unordered_set<int64_t> ParseLongSetField(const std::string& json, const std::string& field);
    static std::string BuildConfigJson(
            const std::string& resource_key,
            int32_t block_size_bytes,
            int64_t content_length,
            int64_t duration_ms,
            const std::unordered_set<int64_t>& block_indexes,
            int64_t last_access_epoch_ms);
    static std::string EscapeJson(const std::string& raw);
    static int64_t NowEpochMs();

    mutable std::mutex mutex_;
    bool initialized_ = false;
    std::filesystem::path cache_root_path_;
    int64_t memory_cache_cap_bytes_ = 5L * 1024L * 1024L;
    int64_t next_session_id_ = 1;
    ProviderBridge* provider_bridge_ = nullptr;
    std::unordered_map<int64_t, std::shared_ptr<SessionState>> sessions_;

    std::list<MemoryKey> memory_lru_order_;
    std::unordered_map<MemoryKey, std::list<MemoryKey>::iterator, MemoryKeyHash> memory_lru_index_;
    int64_t current_memory_bytes_ = 0;
};

}  // namespace cachecore
