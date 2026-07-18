#include "cache_task_loop.h"

#include <exception>
#include <future>

namespace cachecore {

TaskLoop::~TaskLoop() {
    Stop(true);
}

bool TaskLoop::Start(const std::string& name, int worker_count) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (running_) {
        return true;
    }
    if (worker_count <= 0) {
        return false;
    }
    name_ = name;
    stop_requested_ = false;
    drain_on_stop_ = true;
    active_tasks_ = 0;
    running_ = true;
    workers_.reserve(static_cast<std::size_t>(worker_count));
    for (int index = 0; index < worker_count; ++index) {
        workers_.emplace_back(&TaskLoop::ThreadMain, this);
    }
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
    for (auto& worker : workers_) {
        if (worker.joinable()) {
            worker.join();
        }
    }
    std::lock_guard<std::mutex> lock(mutex_);
    workers_.clear();
    running_ = false;
    stop_requested_ = false;
    drain_on_stop_ = true;
    active_tasks_ = 0;
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
        try {
            task();
            done->set_value();
        } catch (...) {
            done->set_exception(std::current_exception());
        }
    });
    if (!posted) {
        return false;
    }
    try {
        future.get();
        return true;
    } catch (...) {
        return false;
    }
}

bool TaskLoop::WaitIdle() {
    std::unique_lock<std::mutex> lock(mutex_);
    if (!running_) {
        return false;
    }
    idle_cv_.wait(lock, [this] {
        return tasks_.empty() && active_tasks_ == 0;
    });
    return true;
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
            active_tasks_ += 1;
        }
        if (task) {
            try {
                task();
            } catch (...) {
                // Keep the worker alive; callers surface operation failures.
            }
        }
        {
            std::lock_guard<std::mutex> lock(mutex_);
            active_tasks_ -= 1;
            if (tasks_.empty() && active_tasks_ == 0) {
                idle_cv_.notify_all();
            }
        }
    }
}

}  // namespace cachecore
