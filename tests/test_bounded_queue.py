import unittest
from reference.bounded_queue import Admission, BoundedQueue


class BoundedQueueTest(unittest.TestCase):
    def test_fifo_and_capacity(self):
        q = BoundedQueue(2)
        self.assertEqual(q.send(None), Admission.ACCEPTED)
        self.assertEqual(q.send(2), Admission.ACCEPTED)
        self.assertEqual(q.send(3), Admission.FULL)
        self.assertEqual(q.receive(), (True, None))
        self.assertEqual(q.receive(), (True, 2))
        self.assertEqual(q.receive(), (False, None))

    def test_close_then_drain(self):
        q = BoundedQueue(1)
        self.assertTrue(q.close())
        self.assertFalse(q.close())
        self.assertEqual(q.send(1), Admission.CLOSED)
        self.assertTrue(q.done)
        q2 = BoundedQueue(1)
        q2.send(42)
        self.assertTrue(q2.close())
        self.assertFalse(q2.done)
        self.assertEqual(q2.receive(), (True, 42))
        self.assertTrue(q2.done)

    def test_invalid_capacity(self):
        for bad in [0, -1, 65537, 1.2, True]:
            with self.assertRaises(ValueError):
                BoundedQueue(bad)


if __name__ == "__main__":
    unittest.main()
