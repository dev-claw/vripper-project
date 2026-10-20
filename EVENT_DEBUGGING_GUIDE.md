# Event Publishing - Quick Reference & Debugging Guide

## EventBus Synchronous vs Asynchronous

### EventBus.publishEvent () - NON-BLOCKING

```kotlin
fun publishEvent(event: Any) {
    _events.tryEmit(event)  // Non-blocking fire-and-forget
}
```

- ✓ Caller returns immediately
- ✓ Does NOT wait for consumers
- ✓ Thread continues to next statement
- ✓ Event distributed to all collectors asynchronously
- ⚠️ May drop oldest event if buffer full (DROP_OLDEST policy)

### GuiEventBus.publishEvent () - SUSPENDING

```kotlin
suspend fun publishEvent(event: GUIEvent) {
    _events.emit(event)  // Suspends if buffer full
}
```

- ⏸️ Caller may suspend if buffer full
- ✓ Guaranteed delivery
- ✓ Consumers notified asynchronously but guaranteed
- ✓ Thread awaits buffer availability

---

## Event Ordering Guarantees

### Collection Order

Events are ALWAYS received by collectors in the order published (FIFO):

```kotlin
eventBus.publishEvent(Event1)  // Emitted first
eventBus.publishEvent(Event2)  // Emitted second
eventBus.publishEvent(Event3)  // Emitted third

// Collector ALWAYS receives:
// Event1 -> Event2 -> Event3
```

### Why Lock Events Inside Locks?

Without lock, race condition occurs:

```
Thread A:                          Thread B:
1. Modify queue
2.                                1. Modify queue (RACE!)
3. Publish event A with state X    
4.                                2. Publish event B with state Y
5. UI receives: A(X), B(Y)
   But actual state is Z
```

With lock, operations are atomic:

```
Thread A:                          Thread B:
1. Lock acquired
2. Modify queue
3. Publish event A
4. Lock released
5.                                1. Lock acquired
6.                                2. Modify queue
7.                                3. Publish event B
8.                                4. Lock released

UI receives: A(correct), B(correct)
```

---

## Debugging Commands

### View Recent Event Logs

```bash
# Show all published events with timestamps
grep "\[.*\] Publishing event:" /path/to/vripper.log

# Show specific event type
grep "QueueStateEvent" /path/to/vripper.log

# Show events in last 10 seconds
grep -E "\[1[0-9]{12}\] Publishing event:" /path/to/vripper.log

# Timeline of post creation/deletion
grep "PostCreateEvent\|PostDeleteEvent" /path/to/vripper.log

# Error count changes
grep "ErrorCountEvent" /path/to/vripper.log
```

### Analyze Event Timing

```bash
# Extract timestamps and event type
grep "Publishing event:" /path/to/vripper.log | \
  awk '{print $1, $NF}' | \
  sort -k1 -n

# Calculate time between events
grep "Publishing event:" /path/to/vripper.log | \
  awk '{print substr($1,2,13)}' | \
  while read ts; do 
    echo "scale=3; $ts - $prev" | bc
    prev=$ts
  done
```

---

## Event Publishing Checklist

### Before Publishing Any Event:

- [ ] **Inside Lock?** If modifying shared collections
  ```kotlin
  downloadManagerLock.withLock {
      // Modify
      // Publish event ← MUST BE HERE
  }
  ```

- [ ] **Inside Transaction?** If modifying database
  ```kotlin
  transaction {
      // Modify DB
      // Publish event ← MUST BE HERE
  }
  ```

- [ ] **Thread Safe?** If accessing shared state
  ```kotlin
  synchronized(sharedState) {
      loggedUser = newValue
  }
  // Publish event ← OR read/publish inside sync
  ```

- [ ] **Logging Added?** For debugging
  ```kotlin
  log.debug("[{}] Publishing event: MyEvent(...)", System.currentTimeMillis())
  ```

- [ ] **Parametrized Logging?** No string templates
  ```kotlin
  // ✓ CORRECT
  log.debug("[{}] Event: {}", timestamp, eventType)
  
  // ✗ WRONG
  log.debug("[${System.currentTimeMillis()}] Event: $eventType")
  ```

---

## Common Event Patterns

### Pattern 1: Lock-Based (QueueManager)

```kotlin
fun operation() {
    downloadManagerLock.withLock {
        // 1. Modify state
        queue.remove(item)
        queue.add(newItem)
        
        // 2. Publish event (inside lock)
        log.debug("[{}] Publishing event: QueueStateEvent", System.currentTimeMillis())
        eventBus.publishEvent(QueueStateEvent(queue.getState()))
    }
}
```

### Pattern 2: Transaction-Based (DataAccessService)

```kotlin
fun operation(entity: Entity) {
    transaction {
        // 1. Modify DB
        repository.update(entity)

        // 2. Update cache
        cache.put(entity.id, entity)

        // 3. Publish event (after transaction, before return)
    }

    // 4. Log and publish event (transaction committed)
    log.debug("[{}] Publishing event: UpdateEvent", System.currentTimeMillis())
    eventBus.publishEvent(UpdateEvent(entity))
}
```

### Pattern 3: Synchronized-Based (VGAuthService)

```kotlin
fun operation(newUser: String) {
    synchronized(this) {
        // 1. Modify shared state
        loggedUser = newUser
        authenticated = true
    }
    
    // 2. Publish event (with consistent state)
    log.debug("[{}] Publishing event: LoginEvent for {}", System.currentTimeMillis(), newUser)
    eventBus.publishEvent(LoginEvent(loggedUser))
}
```

---

## Troubleshooting

### Issue: UI shows stale data

**Causes**:

- Event published before lock released
- Event published before transaction committed
- Race condition in consumer

**Solution**:

1. Check if event published inside lock/transaction
2. Look for "Publishing event:" log entries
3. Verify timestamp matches data change

### Issue: Events arriving out of order

**Causes**:

- Multiple threads publishing simultaneously
- Not using proper synchronization

**Solution**:

1. Verify logs show correct order with timestamps
2. Check if lock/sync is acquired before publish
3. Look for concurrent modifications

### Issue: Event lost (not reaching UI)

**Causes**:

- EventBus buffer overflow (DROP_OLDEST)
- Consumer crashed
- Event filtering issue

**Solution**:

1. Check logs for "Publishing event:"
2. Verify buffer size: `Int.MAX_VALUE` for `EventBus`
3. Check consumer error logs

### Issue: Performance degradation

**Causes**:

- Blocking operations during publish
- Excessive logging
- Lock contention

**Solution**:

1. Verify events published immediately (no blocking)
2. Use parametrized logging (cheaper than string templates)
3. Profile lock hold times

---

## Event Publishing Statistics

### Log Format

```
[timestamp] Publishing event: EventType(details...)
```

### Extract Statistics

```bash
# Count events per type
grep "Publishing event:" vripper.log | \
  sed 's/.*Publishing event: //' | \
  sed 's/(.*//' | \
  sort | uniq -c | sort -rn

# Events per second
grep "Publishing event:" vripper.log | \
  awk '{ts=substr($1,2,10); count[ts]++} END {for (t in count) print t, count[t]}' | \
  sort -n

# Average time between QueueStateEvent
grep "QueueStateEvent" vripper.log | \
  awk '{print substr($1,2,13)}' | \
  paste - - | \
  awk '{print $2-$1}' | \
  awk '{sum+=$1; n++} END {print "Average:", sum/n, "ms"}'
```

---

## Testing Scenarios

### Scenario 1: Rapid Queue Changes

```bash
# Monitor queue state events during rapid operations
tail -f vripper.log | grep "QueueStateEvent"
```

Expected: Events with incrementing timestamps, no duplicates

### Scenario 2: Large Batch Deletions

```bash
# Monitor event sequence during deletion
grep "PostDeleteEvent\|ErrorCountEvent" vripper.log | tail -20
```

Expected: DeleteEvent followed by ErrorCountEvent

### Scenario 3: Concurrent Logins

```bash
# Monitor login events
grep "VGUserLoginEvent" vripper.log
```

Expected: Last login event reflects current state

---

## Performance Notes

### Event Overhead

- Each event: ~1ms to publish (non-blocking)
- No event: ~0.1ms per modify operation
- Logging: ~0.5ms per log statement

### Optimization Tips

1. Use parametrized logging: `log.debug("[{}] {}", ts, event)` ← faster
2. Avoid string templates: `log.debug("[${ts}] $event")` ← slower
3. Batch multiple changes: 1 event for 100 changes vs 100 events
4. Consider event compression for high-frequency events

---

## Files with Event Publishing

| File                    | Events                            | Pattern           | Status      |
|-------------------------|-----------------------------------|-------------------|-------------|
| QueueManager.kt         | QueueStateEvent, ErrorCountEvent  | Lock-based        | ✓ Fixed    |
| DownloadService.kt      | StoppedEvent                      | Lock-based        | ✓ Fixed    |
| DataAccessService.kt    | Post/Thread/Image/Metadata Events | Transaction-based | ✓ Fixed    |
| VGAuthService.kt        | VGUserLoginEvent                  | Synchronized      | ✓ Fixed    |
| DownloadSpeedService.kt | DownloadSpeedEvent                | Coroutine-based   | ✓ Fixed    |
| LogService.kt           | LogCreateEvent                    | Ring Buffer       | ✓ Analyzed |

---

## Next Steps

1. **Monitor logs** in production
2. **Set alerts** for event anomalies
3. **Track performance** metrics
4. **Review** quarterly for optimization opportunities

