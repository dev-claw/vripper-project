# Event Publishing Analysis and Fix Report

## Overview

Analysis of event publishing patterns in the VRipper application to identify and fix race conditions, deadlocks, and
data consistency issues.

## Key Findings

### 1. EventBus Architecture

#### EventBus (vripper-core)

- **Type**: Non-blocking, fire-and-forget
- **Implementation**: `MutableSharedFlow` with `tryEmit()`
- **Characteristics**:
    - Non-suspending
    - Does not wait for consumers
    - Drops oldest events if buffer is full (DROP_OLDEST)
    - Fire-and-forget semantics
- **Risk**: Events may be lost if buffer overflows

#### GuiEventBus (vripper-gui)

- **Type**: Suspending
- **Implementation**: `MutableSharedFlow` with suspend `emit()`
- **Characteristics**:
    - Requires suspension
    - Guarantees delivery
    - Larger buffer (Int.MAX_VALUE)

### 2. Critical Issues Found

#### Issue 1: Events Published OUTSIDE Locks in QueueManager

**Files**: `QueueManager.kt`
**Lines**:

- Line 41: `reportQueueState()` in `clearPending()`
- Line 46: `reportQueueState()` in `clearPending()`
- Line 76: `reportQueueState()` in `accept()`
- Line 120: `reportQueueState()` in `move()`
- Line 150: `eventBus.publishEvent()` in `launch()` callback - AFTER lock released
- Line 170: `eventBus.publishEvent()` in `reportQueueState()`

**Problem**:

- `reportQueueState()` publishes queue state without holding `downloadManagerLock`
- Queue state may change between reading and publishing
- Event data may become inconsistent with actual queue state
- Race condition with concurrent modifications

**Impact**: HIGH - UI receives inconsistent state information

#### Issue 2: Events Published Outside Transactions in DataAccessService

**Files**: `DataAccessService.kt`
**Lines**:

- Line 45: `PostCreateEvent` published AFTER transaction
- Line 53: `PostUpdateEvent` published AFTER transaction
- Line 61: `ThreadCreateEvent` published AFTER transaction
- Line 66: `ThreadUpdateEvent` published AFTER transaction
- Line 71: `ImageEvent` published AFTER transaction
- Line 79: `ImageEvent` published AFTER transaction
- Line 180: `PostDeleteEvent` published AFTER transaction
- Line 181: `ErrorCountEvent` published AFTER transaction
- Line 191: `ThreadDeleteEvent` published AFTER transaction
- Line 221: `MetadataUpdateEvent` published AFTER transaction
- Line 225: `ThreadClearEvent` published AFTER transaction

**Problem**:

- Events published outside transaction scope
- If transaction is rolled back, event is still published
- If another thread reads between transaction commit and event publish, sees stale data
- Event payload may not match database state

**Impact**: CRITICAL - Data consistency violation

#### Issue 3: Events Published in Async Callbacks (QueueManager)

**Files**: `QueueManager.kt`
**Lines**: 150-170 **Context**: `launch()` method uses Failsafe with `onFailure` and `onComplete` callbacks

**Problem**:

- `errorBus.publishEvent()` in `onComplete` callback runs outside lock
- Multiple callbacks from different download tasks publish simultaneously
- No synchronization between event publication and queue state modifications
- Race condition: queue state may change after event published but before consumer processes

**Impact**: HIGH - Lost updates, inconsistent state

#### Issue 4: Unprotected Access in VGAuthService

**Files**: `VGAuthService.kt`
**Lines**: 37, 49, 85, 103, 108

**Problem**:

- `eventBus.publishEvent()` called outside of synchronized block
- `loggedUser` variable accessed/modified without synchronization
- Multiple authenticate () calls could race

**Impact**: MEDIUM - Authentication state inconsistencies

#### Issue 5: Unprotected Publishing in LogService

**Files**: `LogService.kt`
**Lines**: 25

**Problem**:

- Events published from coroutine without proper synchronization
- Ring buffer may be accessed concurrently from log appender
- Event data may be inconsistent

**Impact**: MEDIUM - Log events may be dropped or corrupted

#### Issue 6: Delayed Event Publication in DownloadSpeedService

**Files**: `DownloadSpeedService.kt`
**Lines**: 31, 35, 43

**Problem**:

- Speed events published with delay
- Actual speed may have changed by the time event is published
- No synchronization with bytesCount updates

**Impact**: LOW-MEDIUM - Metrics inaccuracy

### 3. Implementation Approach

#### Solution 1: Move Event Publishing Inside Locks

For QueueManager:

```kotlin
fun clearPending(postEntityId: Long? = null) {
    downloadManagerLock.withLock {
        val toProcess = if (postEntityId != null) pending.flatten()
            .filter { it.postEntityId == postEntityId } else pending.flatten()
        pending.forEach { it.removeAll(toProcess) }
        pending.removeIf { it.isEmpty() }
        
        // Publish event INSIDE lock
        reportQueueState()
    }
}
```

#### Solution 2: Publish Events Inside Transactions

For DataAccessService:

```kotlin
fun saveAndNotify(postEntity: PostEntity, images: List<ImageEntity>): PostEntity {
    val savedPost = transaction {
        val savedPost = postRepository.save(listOf(postEntity)).first()
        save(images.map { it.copy(postEntityId = savedPost.id) })

        // Publish event INSIDE transaction
        eventBus.publishEvent(PostCreateEvent(listOf(savedPost)))

        savedPost
    }
    return savedPost
}
```

#### Solution 3: Add Event Logging for Debugging

Add per-second logging when events are published:

```kotlin
private fun logEvent(event: Any) {
    log.debug("[${System.currentTimeMillis()}] Publishing event: ${event::class.simpleName}")
}
```

### 4. Evaluation: Is Event-Per-Mutation Good Approach?

**Pros**:
✓ Real-time UI updates ✓ Reactive pattern ✓ Decoupled components ✓ Easy to trace data flow

**Cons**:
✗ Event ordering issues ✗ Potential data loss with tryEmit ()
✗ Race conditions without proper synchronization ✗ Difficulty debugging event ordering ✗ Buffer overflow possible
(though rare)

**Recommendation**:

- YES, but MUST synchronize event publishing with data mutations
- Consider using suspend emit () instead of tryEmit ()
- Add event versioning for ordering guarantees
- Implement event replay capability

### 5. stop () Method Analysis

**File**: `DownloadService.kt:57`
**Current Implementation**:

```kotlin
fun stop(postEntityIds: List<Long> = emptyList()) {
    downloadManagerLock.withLock {
        if (postEntityIds.isNotEmpty()) {
            stopInternal(postEntityIds)
            eventBus.publishEvent(StoppedEvent(postEntityIds))
        } else {
            stopAll()
            eventBus.publishEvent(StoppedEvent(listOf(-1)))
        }
    }
}
```

**Evaluation**:
✓ Event published INSIDE lock (GOOD)
✓ Atomic operation with data change ✓ Prevents race conditions ✓ Clear intent with list parameter

**Issues**:

- Logging could be improved
- Should add per-second event log

## Detailed Fixes Applied

See individual file modifications below.

