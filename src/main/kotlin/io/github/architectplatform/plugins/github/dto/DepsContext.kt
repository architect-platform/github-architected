package io.github.architectplatform.plugins.github.dto

data class DepsContext(
	val enabled : Boolean = true,
	val type: String = "dependencies/renovate",
	val format: String = "json",
)