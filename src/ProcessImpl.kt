package mutex

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Timofey Belousov
 */
class ProcessImpl(private val env: Environment) : Process {
    enum class MsgType { REQ, OK }

    private var forks = MutableList(env.nProcesses + 1) { pos ->
        pos == 0 || pos >= env.processId
    }
    private var isLockPending = false
    private var locked = false

    private var requests = MutableList(env.nProcesses + 1) { false }

    override fun onMessage(srcId: Int, message: Message) {
        lateinit var type: MsgType
        message.parse {
            type = readEnum()
        }

        when (type) {
            MsgType.REQ -> {
                if (!tryFlipFork(srcId)) {
                    requests[srcId] = true
                }
            }

            MsgType.OK -> {
                assert(isLockPending)

                forks[srcId] = true

                if (!forks.contains(false)) {
                    isLockPending = false
                    env.locked()
                    locked = true
                }
            }
        }
    }

    override fun onLockRequest() {
        for ((i, fork) in forks.withIndex()) {
            if (!fork) {
                isLockPending = true
                env.send(i) {
                    writeEnum(MsgType.REQ)
                }
            }
        }

        if (!isLockPending) {
            env.locked()
            locked = true
        }
    }

    override fun onUnlockRequest() {
        locked = false
        env.unlocked()
        for ((i, req) in requests.withIndex()) {
            if (req) {
                if (tryFlipFork(i)) {
                    requests[i] = false
                }
            }
        }
    }

    private fun tryFlipFork(i: Int): Boolean {
        if (forks[i]) {
            if (locked || isLockPending) {
                return false
            }
            forks[i] = false
        }
        env.send(i) {
            writeEnum(MsgType.OK)
        }

        return true
    }

}
