#pragma once

#include <atomic>
#include <cstdint>

// Aggregates asynchronous GPU timer-query results from the active Flycast renderer.
// The UI polls this counter from a background thread, so updates stay lock-free.
namespace gpu_usage {

enum class Backend : uint32_t {
	OpenGLES = 1u << 0,
	Vulkan = 1u << 1,
};

inline std::atomic<uint64_t>& accumulatedNanoseconds()
{
	static std::atomic<uint64_t> value{0};
	return value;
}

inline std::atomic<uint32_t>& supportedBackends()
{
	static std::atomic<uint32_t> value{0};
	return value;
}

inline void SetSupported(Backend backend, bool supported)
{
	const uint32_t bit = static_cast<uint32_t>(backend);
	if (supported)
		supportedBackends().fetch_or(bit, std::memory_order_relaxed);
	else
		supportedBackends().fetch_and(~bit, std::memory_order_relaxed);
}

inline bool IsSupported()
{
	return supportedBackends().load(std::memory_order_relaxed) != 0;
}

inline void AddNanoseconds(uint64_t nanoseconds)
{
	accumulatedNanoseconds().fetch_add(nanoseconds, std::memory_order_relaxed);
}

inline uint64_t TakeNanoseconds()
{
	return accumulatedNanoseconds().exchange(0, std::memory_order_relaxed);
}

} // namespace gpu_usage
