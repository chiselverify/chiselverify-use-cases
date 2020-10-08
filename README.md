# chisel-uvm use case
Heap based priority queue (from MicroSemi) implemented in chisel

see also:
* https://www.hackerearth.com/practice/notes/heaps-and-priority-queues/
* https://www.geeksforgeeks.org/k-ary-heap/

# Plan

 * MinFinder:
    - [x] describe MinFinder
    - [x] write test for MinFinder
 * Heapifier:
    - [x] optimize heapify loop behaviour and RAM access
    - [x] create model
    - heapify up
        - [x] implement heapify up functionality
        - [x] create test
        - [x] create automated test cases
    - heapify down
        - [x] implement heapify down functionality
        - [x] create automated test cases
 * top level
    - [x] implement insertion functionality
    - [ ] implement reference ID mapping and removal functionality 
        - [ ] integrate masked memory operation [wip]
        - [ ] integrate linear search over reference IDs [wip]
        - [ ] consider other ways to search for IDs
    - [ ] expand behavioural model? which language?