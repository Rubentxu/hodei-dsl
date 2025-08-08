package dev.rubentxu.hodei.core.domain.model

/**
 * Post-execution action sealed class hierarchy
 * 
 * Defines actions to be executed after stage or pipeline completion,
 * supporting various trigger conditions.
 */
public sealed class PostAction {
    public abstract val condition: PostCondition
    public abstract val steps: List<Step>
    
    /**
     * Always execute action
     */
    public data class Always(
        override val steps: List<Step>
    ) : PostAction() {
        override val condition: PostCondition = PostCondition.ALWAYS
    }
    
    /**
     * Execute on success
     */
    public data class Success(
        override val steps: List<Step>
    ) : PostAction() {
        override val condition: PostCondition = PostCondition.SUCCESS
    }
    
    /**
     * Execute on failure
     */
    public data class Failure(
        override val steps: List<Step>
    ) : PostAction() {
        override val condition: PostCondition = PostCondition.FAILURE
    }
    
    /**
     * Execute on unstable result
     */
    public data class Unstable(
        override val steps: List<Step>
    ) : PostAction() {
        override val condition: PostCondition = PostCondition.UNSTABLE
    }
    
    /**
     * Execute on changed result
     */
    public data class Changed(
        override val steps: List<Step>
    ) : PostAction() {
        override val condition: PostCondition = PostCondition.CHANGED
    }
    
    public companion object {
        public fun always(steps: List<Step>): Always = Always(steps)
        public fun success(steps: List<Step>): Success = Success(steps)
        public fun failure(steps: List<Step>): Failure = Failure(steps)
        public fun unstable(steps: List<Step>): Unstable = Unstable(steps)
        public fun changed(steps: List<Step>): Changed = Changed(steps)
    }
}

/**
 * Post condition enumeration
 */
public enum class PostCondition {
    ALWAYS,
    SUCCESS,
    FAILURE,
    UNSTABLE,
    CHANGED,
    ABORTED,
    CLEANUP
}