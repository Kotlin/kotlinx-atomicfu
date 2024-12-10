int __ulock_wait(uint32_t operation, void *addr, uint64_t value, uint32_t timeout_us);
int __ulock_wait2(uint32_t operation, void *addr, uint64_t value, uint64_t timeout_ns, uint64_t value2);
int __ulock_wake(uint32_t operation, void *addr, uint64_t wake_value);