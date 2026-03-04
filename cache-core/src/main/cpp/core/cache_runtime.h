#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <filesystem>
#include <functional>
#include <list>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "cache_control.h"
#include "cache_ring_buffer.h"
#include "cache_task_loop.h"
#include "cache_types.h"
#include "cache_virtual_file.h"

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

    struct StreamCallbacks {
        std::function<void(int64_t offset, int32_t requested_size)> on_begin;
        std::function<bool(int64_t chunk_offset, const std::vector<uint8_t>& bytes)> on_data;
        std::function<void(bool success)> on_end;
    };

    virtual std::vector<uint8_t> ReadAt(int64_t provider_handle, int64_t offset, int32_t size) = 0;
    virtual bool ReadAtStream(
            int64_t provider_handle,
            int64_t offset,
            int32_t size,
            const StreamCallbacks& callbacks) = 0;
    virtual void CancelInFlightRead(int64_t provider_handle) = 0;
    virtual int64_t QueryContentLength(int64_t provider_handle) = 0;
    virtual void Close(int64_t provider_handle) = 0;
};

class CacheRuntime {
public:
    bool Init(const std::string& cache_root_path, int64_t memory_cache_cap_bytes);
    bool Init(
            const std::string& cache_root_path,
            int64_t memory_cache_cap_bytes,
            int64_t disk_cache_max_bytes,
            double disk_cache_clean_range_min,
            double disk_cache_clean_range_max,
            int32_t read_retry_count);

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
        std::vector<Range> completed_ranges;
        int64_t last_access_epoch_ms = -1;
    };

    struct SessionState {
        int64_t session_id = -1;
        std::string resource_key;
        int64_t provider_handle = -1;
        SessionCursor cursor;

        std::atomic<uint64_t> read_generation{1};
        std::atomic<bool> closed{false};

        mutable std::mutex mutex;
        std::condition_variable data_cv;
        SessionStorageFiles storage;
        VirtualFile local_cache;
        RingBufferWindow memory_cache;

        int64_t memory_window_capacity_bytes = 0;
        std::atomic<int64_t> memory_cached_bytes{0};

        int64_t prefetch_cursor_offset = 0;  // last requested read offset
        bool prefetch_running = false;
        uint64_t prefetch_generation = 0;
    };

    struct StorageSnapshot {
        int32_t block_size_bytes = 64 * 1024;
        int64_t content_length = -1;
        int64_t duration_ms = -1;
        std::unordered_set<int64_t> block_indexes;
        std::vector<Range> completed_ranges;
        int64_t last_access_epoch_ms = -1;
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

    std::vector<uint8_t> ReadAtInternal(
            const std::shared_ptr<SessionState>& session,
            int64_t offset,
            int32_t size);
    bool FetchBlockFromProvider(
            const std::shared_ptr<SessionState>& session,
            int64_t request_offset,
            int32_t request_size,
            uint64_t expected_generation,
            int32_t* out_streamed_bytes,
            bool* out_cancelled);
    void ScheduleBlockPersist(
            const std::shared_ptr<SessionState>& session,
            int64_t block_start_offset,
            std::vector<uint8_t> bytes,
            uint64_t expected_generation);

    void EnsurePrefetch(
            const std::shared_ptr<SessionState>& session,
            int64_t offset,
            uint64_t expected_generation);
    void PrefetchLoop(
            std::weak_ptr<SessionState> weak_session,
            uint64_t expected_generation);
    bool WaitForReadable(
            const std::shared_ptr<SessionState>& session,
            int64_t offset,
            uint64_t expected_generation,
            int32_t timeout_ms);

    void TouchMemorySessionLocked(int64_t session_id);
    void ReportSessionMemoryBytesLocked(int64_t session_id, int64_t bytes);
    void EvictMemoryIfNeededLocked(int64_t protected_session_id);
    void RemoveSessionMemoryLocked(int64_t session_id);

    std::optional<CacheLookupSnapshot> LookupLocked(const std::string& resource_key);
    std::unordered_set<std::string> CollectResourceKeysLocked() const;
    std::unordered_set<std::string> CollectUsingResourceKeysLocked() const;

    bool TriggerDiskCleanupLocked();

    static std::optional<std::string> ParseResourceKeyFromFileName(const std::string& file_name);
    static std::optional<int64_t> ParseLongField(const std::string& json, const std::string& field);
    static std::unordered_set<int64_t> ParseLongSetField(const std::string& json, const std::string& field);
    static std::vector<Range> ParseRangesField(const std::string& json, const std::string& field);
    static std::string BuildConfigJson(
            const std::string& resource_key,
            int32_t block_size_bytes,
            int64_t content_length,
            int64_t duration_ms,
            const std::unordered_set<int64_t>& block_indexes,
            const std::vector<Range>& completed_ranges,
            int64_t last_access_epoch_ms);
    static std::string BuildRangesJson(const std::vector<Range>& ranges);
    static std::string EscapeJson(const std::string& raw);
    static int64_t NowEpochMs();

    std::shared_ptr<SessionState> GetSession(int64_t session_id) const;

    mutable std::mutex mutex_;
    bool initialized_ = false;
    std::filesystem::path cache_root_path_;

    int64_t memory_cache_cap_bytes_ = 5L * 1024L * 1024L;
    int64_t disk_cache_max_bytes_ = 500L * 1024L * 1024L;
    double disk_cache_clean_range_min_ = 0.8;
    double disk_cache_clean_range_max_ = 1.0;
    int32_t read_retry_count_ = 3;

    int64_t next_session_id_ = 1;
    ProviderBridge* provider_bridge_ = nullptr;

    CacheControl cache_control_;
    TaskLoop render_loop_;
    TaskLoop write_loop_;

    std::unordered_map<int64_t, std::shared_ptr<SessionState>> sessions_;

    std::list<int64_t> memory_lru_order_;
    std::unordered_map<int64_t, std::list<int64_t>::iterator> memory_lru_index_;
    std::unordered_map<int64_t, int64_t> memory_session_bytes_;
    int64_t current_memory_bytes_ = 0;
};

}  // namespace cachecore
