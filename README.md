# chisel-uvm use case
Heap based priority queue (from MicroSemi) implemented in chisel

see also:
* https://www.hackerearth.com/practice/notes/heaps-and-priority-queues/
* https://www.geeksforgeeks.org/k-ary-heap/

# Plan

 * MinimumFinder:
    - [x] describe MinimumFinder
    - [x] write test for MinimumFinder
 * PriorityMinimumFinder:
    - [x] describe PriorityMinimumFinder
    - [x] write test for PriorityMinimumFinder
 * Heapifier:
    - [ ] optimize heapify loop behaviour and RAM access [wip]
    - heapify up
        - [x] implement heapify up functionality
        - [x] create test
        - [ ] create automated test cases
    - heapify down
        - [ ] implement heapify down functionality [wip]
        - [ ] create automated test cases
 * top level
    - [ ] implement insertion functionality
    - [ ] implement reference ID mapping
    - [ ] implement removal functionality
