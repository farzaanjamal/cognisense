package org.cognisense.app

import org.cognisense.core.config.BatteryConfig
import org.cognisense.core.engine.TaskDefinition

enum class PadLayout { SINGLE, LEFT_RIGHT, BOARD }

/** One runnable part of a task (spatial span has two: forward SPF and backward SPB). */
data class PartInfo(
    val id: String,
    val pads: PadLayout,
    val standard: TaskDefinition,
    val review: TaskDefinition,
) {
    val childNameKey: String get() = "${id.lowercase()}_name"
    val instructionKey: String get() = "${id.lowercase()}_instr"
}

/** A task in the candidate pool (docs/task_specifications.md §5). No parts = DESIGNED, not implemented. */
data class TaskInfo(
    val code: String,
    val taskId: String,
    val docName: String,
    val construct: String,
    val measures: String,
    val reviewDuration: String,
    val parts: List<PartInfo>,
) {
    val implemented: Boolean get() = parts.isNotEmpty()
}

/** Built entirely from config/tasks.json: the pool, the parts, their pads and the core order. */
class TaskCatalog(cfg: BatteryConfig) {
    val pool: List<TaskInfo> = cfg.pool.map { e ->
        TaskInfo(e.code, e.id, e.name, e.construct, e.measures, e.reviewDuration,
            e.parts.map { p -> PartInfo(p, PadLayout.valueOf(cfg.pads(p)), cfg.standard(p), cfg.review(p)) })
    }
    val coreOrder: List<TaskInfo> = cfg.coreOrder.map { code -> pool.first { it.code == code } }
}
