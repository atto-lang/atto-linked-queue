"""Bounded FIFO reference model, not a native lock-free runtime implementation."""
from collections import deque
from enum import Enum


class Admission(Enum):
    ACCEPTED = "accepted"
    FULL = "full"
    CLOSED = "closed"


class BoundedQueue:
    """Single-threaded queue with monotonic close, FIFO drain and distinct None payloads."""

    def __init__(self, capacity):
        if type(capacity) is not int or not 1 <= capacity <= 65536:
            raise ValueError("capacity must be a positive bounded integer")
        self.capacity = capacity
        self._closed = False
        self._items = deque()

    def send(self, value):
        if self._closed:
            return Admission.CLOSED
        if len(self._items) == self.capacity:
            return Admission.FULL
        self._items.append(value)
        return Admission.ACCEPTED

    def receive(self):
        """Return (has_item, value), distinguishing an empty queue from None payload."""
        if not self._items:
            return (False, None)
        return (True, self._items.popleft())

    def close(self):
        """Return True on the first closure; closure never drops admitted items."""
        first_close = not self._closed
        self._closed = True
        return first_close

    @property
    def done(self):
        return self._closed and not self._items

    @property
    def pending(self):
        return len(self._items)
