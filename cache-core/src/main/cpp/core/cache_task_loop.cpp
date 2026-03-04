#include "cache_task_loop.h"

#include <future>

namespace cachecore {

TaskLoop::~TaskLoop() {
    Stop(true);
}

bool TaskLoop::Start(const std::string& name) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (running_) {
        return true;
    }
    name_ = name;
    stop_requested_ = false;
    drain_on_stop_ = true;
    running_ = true;
    thread_ = std::thread(&TaskLoop::ThreadMain, this);
    return true;
}

void TaskLoop::Stop(bool drain) {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!running_) {
            return;
        }
        stop_requested_ = true;
        drain_on_stop_ = drain;
        if (!drain_on_stop_) {
            std::queue<std::function<void()>> empty;
            tasks_.swap(empty);
        }
    }
    cv_.notify_all();
    if (thread_.joinable()) {
        thread_.join();
    }
    std::lock_guard<std::mutex> lock(mutex_);
    running_ = false;
    stop_requested_ = false;
    drain_on_stop_ = true;
    std::queue<std::function<void()>> empty;
    tasks_.swap(empty);
}

bool TaskLoop::Post(std::function<void()> task) {
    if (!task) {
        return false;
    }
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!running_ || stop_requested_) {
            return false;
        }
        tasks_.push(std::move(task));
    }
    cv_.notify_one();
    return true;
}

bool TaskLoop::PostAndWait(std::function<void()> task) {
    if (!task) {
        return false;
    }
    auto done = std::make_shared<std::promise<void>>();
    auto future = done->get_future();
    const bool posted = Post([task = std::move(task), done]() mutable {
        task();
        done->set_value();
    });
    if (!posted) {
        return false;
    }
    future.wait();
    return true;
}

bool TaskLoop::WaitIdle() {
    return PostAndWait([] {});
}

void TaskLoop::ThreadMain() {
    while (true) {
        std::function<void()> task;
        {
            std::unique_lock<std::mutex> lock(mutex_);
            cv_.wait(lock, [this] {
                return stop_requested_ || !tasks_.empty();
            });
            if (stop_requested_ && (!drain_on_stop_ || tasks_.empty())) {
                break;
            }
            if (tasks_.empty()) {
                continue;
            }
            task = std::move(tasks_.front());
            tasks_.pop();
        }
        if (task) {
            task();
        }
    }
}

}  // namespace cachecore
