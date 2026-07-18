#pragma once

#include <condition_variable>
#include <functional>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <vector>

namespace cachecore {

class TaskLoop {
public:
    TaskLoop() = default;
    ~TaskLoop();

    TaskLoop(const TaskLoop&) = delete;
    TaskLoop& operator=(const TaskLoop&) = delete;

    bool Start(const std::string& name, int worker_count = 1);
    void Stop(bool drain);

    bool Post(std::function<void()> task);
    bool PostAndWait(std::function<void()> task);
    bool WaitIdle();

private:
    void ThreadMain();

    mutable std::mutex mutex_;
    std::condition_variable cv_;
    std::condition_variable idle_cv_;
    std::queue<std::function<void()>> tasks_;
    std::vector<std::thread> workers_;
    std::string name_;
    int active_tasks_ = 0;
    bool running_ = false;
    bool stop_requested_ = false;
    bool drain_on_stop_ = true;
};

}  // namespace cachecore
