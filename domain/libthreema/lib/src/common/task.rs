//! Common items specific to tasks.
use libthreema_macros::{DebugVariantNames, VariantNames};

/// Result of polling a task or advancing a task's state in any other fashion.
///
/// Note: This allows to easily expose instructions of a sub-task from a task without exposing the
/// sub-task's results.
#[derive(DebugVariantNames, VariantNames)]
pub enum TaskLoop<TInstruction, TResult> {
    /// An enclosed instructions needs to be handled in order to advance the task's state.
    Instruction(TInstruction),

    /// Result of the completed task.
    Done(TResult),
}
