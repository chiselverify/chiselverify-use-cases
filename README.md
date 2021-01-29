# chiselverify-use-cases

This repository hosts use-cases developed and used in connection with the chiselverify project.

## Priority queue

!!!! all scheduling in a super cycle needs to be done before entering said super cycle

This use-case is a priority queue which is meant to be used for scheduling purposes in real-time systems.
The specification was provided by Microchip.
Values referring to a specific clock cycle number in a specific super cycle can be inserted and removed. 
The priority queue sorts all enqueued values such that the value referring to the clock cycle number to be 
encountered first in the future is presented to the host system.

see also:
* https://www.hackerearth.com/practice/notes/heaps-and-priority-queues/
* https://www.geeksforgeeks.org/k-ary-heap/

The `PriorityQueue` provides scheduling functionality. It is designed for hosts which need scheduling in and across multiple super cycles.

Elements in the queue consist of a super cycle tag, a cycle tag and a reference ID. The super cycle tag does not state an absolute cycle number but a free running super cycle counter is used for relative comparison. The number of super cycles the `PriorityQueue` should be able to differentiate is specified at instantiation. The cycle tag gives an absolute cycle number which together with the super cycle tag specifies a precise point in the future.

When elements are enqueued, the super cycle and cycle tags must be provided along with an unique reference ID. The `PriorityQueue` then places the new element at the appropriate position in the queue. When an element no longer is needed, it can be removed from the queue by providing the associated reference ID.

The `PriorityQueue` presents the current head of the queue when `head.valid` is asserted. An empty queue is signalized by the assertion of `head.none`.


### Parameters

The following parameters are associated with an instance of the `PriorityQueue`. Some are explicitly specified whereas the widths are derived.

| Name          | type          | Values                    | Description                                                                   |
| ------------- | :-----------: | :-------------------:     | ----------------------------------------------------------------------------- |
| `chCount`     | explicit      | 2,4,8,16,32               | The number of children nodes per parent node in the heap.                     |
| `size`        | explicit      | `size`*`chCount`<256      | The number groups in the heap consisting of `chCount` elements. The total number of elements is given by `size`*`chCount`+1.
| `scCount`     | explicit      | 0..?                      | The number of super cycles the queue should be able to differentiate. If set to zero, the queue will not include hardware for super cycle comparison.
| `cCount`      | explicit      | 1..?                      | The number of cycles the queue should be able to differentiate.               |
|
| `cWid`        | derived      | log2Ceil(`scCount`)       | The width of the super cycle tags                                              |
| `nWid`        | derived      | log2Ceil(`cCount`)        | The width of the cycle tags                                                    |
| `rWid`        | derived      | log2Ceil(`size`*`chCount`+1)| The width of the reference ID field                                          |
| `addrWid`     | derived      | log2Ceil(`size`)          | The width of the address fields                                                |



### Interface

The following table specifies the interface of the `PriorityQueue`.

| Name                | Direction | Width           | Description                                                  |
| ------------------- | :-------: | :-------------: | ------------------------------------------------------------ |
| `head.valid`        | out       | 1               | Indicates whether the current head of the queue is presented |
| `head.none`         | out       | 1               | When true, the queue is empty                                |
| `head.prio.c`       | out       | `cWid`          | The super cycle tag of the head element                      |
| `head.prio.n`       | out       | `nWid`          | The cycle tag of the head element                            |
| `head.id`           | out       | `rWid`          | The reference ID of the head element                         |
| | | | |
| `cmd.valid`         | in        | 1               | When true, the applied operation is to be executed           |
| `cmd.op`            | in        | 1               | The operation: 0 = Remove, 1 = Insert                        |
| `cmd.prio.c`        | in        | `cWid`          | The super cycle tag of the element to be inserted            |
| `cmd.prio.n`        | in        | `nWid`          | The cycle tag of the element to be inserted                  |
| `cmd.id`            | in        | `rWid`          | The reference ID of the element to be removed/inserted       |
| `cmd.ready`         | out       | 1               | When true, the module is ready for the next operation        |
| `cmd.error`         | out       | 1               | When true, an error occurred during the last operation       |
| `cmd.rmPrio.c`      | out       | `cWid`          | The super cycle tag of the last removed element              |
| `cmd.rmPrio.n`      | out       | `nWid`          | The cycle tag of the last removed element                    |

### Timing

The timing interface to the `PriorityQueue` is quite simple: Apply the correct operation with the associated data and keep `cmd.valid` asserted until `cmd.ready` is asserted again.

The best and worst case insertion and removal times are listed in the table below.

**TODO**--------------------------------------------------------------------------
