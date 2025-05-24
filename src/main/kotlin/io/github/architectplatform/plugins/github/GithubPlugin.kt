package io.github.architectplatform.plugins.github

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.architectplatform.api.execution.CommandExecutor
import io.github.architectplatform.api.execution.ResourceExtractor
import io.github.architectplatform.api.phase.SimpleTask
import io.github.architectplatform.api.plugins.ArchitectPlugin
import io.github.architectplatform.api.project.ProjectContext
import io.github.architectplatform.api.tasks.TaskRegistry
import io.github.architectplatform.api.tasks.TaskResult
import io.github.architectplatform.api.workflows.core.CoreWorkflow
import io.github.architectplatform.plugins.github.dto.GithubContext
import java.nio.file.Files
import kotlin.io.path.Path

class GithubPlugin : ArchitectPlugin<GithubContext> {
	override val id = "github-plugin"
	override val contextKey: String = "github"

	@Suppress("UNCHECKED_CAST")
	override val ctxClass = GithubContext::class.java
	override lateinit var context: GithubContext

	override fun register(registry: TaskRegistry) {
		println("Registering GradlePlugin with ID: $id")
		registry.add(
			SimpleTask(
				id = "github-release-task",
				phase = CoreWorkflow.RELEASE,
				task = ::releaseTask,
			)
		)
	}

	private fun releaseTask(projectContext: ProjectContext): TaskResult {
		println("Github Releaser: releasing the application")
		println("Release context: $context")
		val objectMapper = ObjectMapper()
		val standardGitAssets = listOf("**/*.gradle", "**/*.gradle.kts")
		val message = objectMapper.writeValueAsString(context.release.message)
		val assetsJson = objectMapper.writeValueAsString(context.release.assets)
		val allGitAssets = standardGitAssets + context.release.git_assets
		val gitAssetsjson = objectMapper.writeValueAsString(allGitAssets)

		val resourceExtractor = projectContext.service(ResourceExtractor::class.java)
		val commandExecutor = projectContext.service(CommandExecutor::class.java)

		resourceExtractor.copyFileFromResources("releases/run.sh", Path(""), "run.sh")
		resourceExtractor.copyFileFromResources("releases/update-version.sh", Path(""), "update-version.sh")
		commandExecutor.execute("chmod +x update-version.sh")
		resourceExtractor.getResourceFileContent("releases/.releaserc.json")
			.replace("{{message}}", message)
			.replace("{{assets}}", assetsJson)
			.replace("{{git_assets}}", gitAssetsjson)
			.let { result ->
				Files.write(Path(".releaserc.json"), result.toByteArray())
			}

		commandExecutor.execute("./run.sh")

		commandExecutor.execute("rm run.sh")
		commandExecutor.execute("rm update-version.sh")
		commandExecutor.execute("rm .releaserc.json")
		return TaskResult.success("Release completed successfully")
	}

}

