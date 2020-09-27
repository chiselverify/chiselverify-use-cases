# chisel-uvm use case
Heap based priority queue (from MicroSemi) implemented in chisel

see also:
* https://www.hackerearth.com/practice/notes/heaps-and-priority-queues/
* https://www.geeksforgeeks.org/k-ary-heap/

# Plan

 * old.MinimumFinder:
    - [x] describe old.MinimumFinder
    - [x] write test for old.MinimumFinder
 * old.PriorityMinimumFinder:
    - [x] describe old.PriorityMinimumFinder
    - [x] write test for old.PriorityMinimumFinder
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
