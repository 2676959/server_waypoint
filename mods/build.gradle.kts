plugins {
    id("base")
}

val aggregatePublishTasks = mapOf(
    "publishAllMods" to "publishMods",
    "publishAllModsToModrinth" to "publishModrinth",
    "publishAllModsToCurseForge" to "publishCurseforge",
)

val registeredAggregatePublishTasks = aggregatePublishTasks.mapValues { (taskName, childTaskName) ->
    tasks.register(taskName) {
        group = "publishing"
        description = "Runs $childTaskName for every versioned mods subproject."
    }
}

gradle.projectsEvaluated {
    registeredAggregatePublishTasks.forEach { (_, taskProvider) ->
        taskProvider.configure {
            val childTaskName = aggregatePublishTasks.getValue(name)
            dependsOn(subprojects.map { it.tasks.named(childTaskName) })
        }
    }
}
