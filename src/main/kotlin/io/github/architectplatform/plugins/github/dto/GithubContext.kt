package io.github.architectplatform.plugins.github.dto

data class GithubContext(
	val release: GithubReleaseContext,
	val pipelines: List<PipelineContext> = emptyList(),
)

