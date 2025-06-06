package io.github.architectplatform.plugins.github

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.architectplatform.api.components.execution.CommandExecutor
import io.github.architectplatform.api.components.execution.ResourceExtractor
import io.github.architectplatform.api.core.plugins.ArchitectPlugin
import io.github.architectplatform.api.core.project.ProjectContext
import io.github.architectplatform.api.core.tasks.TaskRegistry
import io.github.architectplatform.api.core.tasks.TaskResult
import io.github.architectplatform.api.core.tasks.impl.SimpleTask
import io.github.architectplatform.api.components.workflows.core.CoreWorkflow
import io.github.architectplatform.api.core.tasks.Environment
import io.github.architectplatform.plugins.github.dto.GithubContext
import io.github.architectplatform.plugins.github.dto.PipelineContext
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

class GithubPlugin : ArchitectPlugin<GithubContext> {
	override val id = "github-plugin"
	override val contextKey: String = "github"

	override val ctxClass = GithubContext::class.java
	override var context: GithubContext = GithubContext()

	override fun register(registry: TaskRegistry) {
		println("Registering GithubPlugin with ID: $id")
		registry.add(
			SimpleTask(
				id = "github-release-task",
				phase = CoreWorkflow.RELEASE,
				task = ::releaseTask,
			)
		)

		registry.add(
			SimpleTask(
				id = "github-init-pipelines",
				phase = CoreWorkflow.INIT,
				task = ::initPipelines,
			)
		)

		registry.add(
			SimpleTask(
				id = "github-init-dependencies",
				phase = CoreWorkflow.INIT,
				task = ::initDependencies,
			)
		)
	}

	private fun initDependencies(environment: Environment, projectContext: ProjectContext): TaskResult {
		println("Github Releaser: initializing dependencies")
		if (context.deps.enabled.not()) {
			println("Github Releaser: dependencies are not enabled, skipping initialization.")
			return TaskResult.success("Dependencies initialization skipped.")
		}
		val resourceExtractor = environment.service(ResourceExtractor::class.java)
		resourceExtractor.copyDirectoryFromResources(this.javaClass.classLoader, "dependencies/${context.deps.type}", Path(projectContext.dir.toString(), ".github/"))
		return TaskResult.success("Dependencies initialized successfully.")
	}

	private fun releaseTask(environment: Environment, projectContext: ProjectContext): TaskResult {
		println("Github Releaser: releasing the application")
		println("Release context: $context")
		if (context.release.enabled.not()) {
			println("Github Releaser: release is not enabled, skipping.")
			return TaskResult.success("Release skipped as it is not enabled.")
		}
		val objectMapper = ObjectMapper()
		val standardGitAssets = listOf("**/*.gradle", "**/*.gradle.kts")
		val message = objectMapper.writeValueAsString(context.release.message)
		val assetsJson = objectMapper.writeValueAsString(context.release.assets)
		val allGitAssets = standardGitAssets + context.release.git_assets
		val gitAssetsjson = objectMapper.writeValueAsString(allGitAssets)

		val resourceExtractor = environment.service(ResourceExtractor::class.java)
		val commandExecutor = environment.service(CommandExecutor::class.java)

		resourceExtractor.copyFileFromResources(this.javaClass.classLoader, "releases/run.sh", projectContext.dir, "run.sh")
		resourceExtractor.copyFileFromResources(this.javaClass.classLoader, "releases/update-version.sh", projectContext.dir, "update-version.sh")
		commandExecutor.execute("chmod +x update-version.sh", projectContext.dir.toString())
		resourceExtractor.getResourceFileContent(this.javaClass.classLoader, "releases/.releaserc.json")
			.replace("{{message}}", message)
			.replace("{{assets}}", assetsJson)
			.replace("{{git_assets}}", gitAssetsjson)
			.let { result ->
				Files.write(Path(projectContext.dir.toString(),".releaserc.json"), result.toByteArray())
			}

		commandExecutor.execute("./run.sh", projectContext.dir.toString())

		commandExecutor.execute("rm run.sh", projectContext.dir.toString())
		commandExecutor.execute("rm update-version.sh", projectContext.dir.toString())
		commandExecutor.execute("rm .releaserc.json", projectContext.dir.toString())
		return TaskResult.success("Release completed successfully")
	}

	private fun initPipelines(environment: Environment, projectContext: ProjectContext): TaskResult {
		val results = this.context.pipelines.map { pipeline ->
			println("Initializing pipeline: ${pipeline.name} of type ${pipeline.type}")
			return initSinglePipeline(pipeline, environment, projectContext)
		}
		return TaskResult.success("All pipelines initialized successfully.", results)
	}

	private fun initSinglePipeline(
		pipeline: PipelineContext,
		environment: Environment,
		projectContext: ProjectContext,
	): TaskResult {
		val resourceRoot = "pipelines/"
		val resourceFile = resourceRoot + pipeline.type + ".yml"
		val pipelinesDir = File(
			projectContext.dir.toString(),
			".github/workflows"
		)

		if (!pipelinesDir.exists()) {
			pipelinesDir.mkdirs()
		}

		val resourceExtractor = environment.service(ResourceExtractor::class.java)
		resourceExtractor.getResourceFileContent(this.javaClass.classLoader, resourceFile)
			.let { content ->
				val filePath = File(pipelinesDir, "${pipeline.name}.yml")
				filePath.writeText(
					content
						.replace("{{name}}", pipeline.name)
				)
			}
		return TaskResult.success("Pipeline ${pipeline.name} initialized successfully.")
	}

}

